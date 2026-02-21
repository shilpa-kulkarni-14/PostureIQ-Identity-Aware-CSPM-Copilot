package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.IamIdentity;
import com.cspm.model.IamPolicy;
import com.cspm.model.ScanResult;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrelationService {

    private final ScanResultRepository scanResultRepository;
    private final IamIdentityRepository iamIdentityRepository;

    @Transactional
    public ScanResult correlate() {
        log.info("Starting correlation analysis");

        List<ScanResult> allScans = scanResultRepository.findAllByOrderByTimestampDesc();

        List<Finding> configFindings = new ArrayList<>();
        List<Finding> iamFindings = new ArrayList<>();

        for (ScanResult scan : allScans) {
            ScanResult withFindings = scanResultRepository.findByIdWithFindings(scan.getScanId()).orElse(null);
            if (withFindings == null) continue;

            List<Finding> scanFindings = withFindings.getFindings();
            if (configFindings.isEmpty()) {
                List<Finding> configs = scanFindings.stream()
                        .filter(ff -> "CONFIG".equals(ff.getCategory()))
                        .toList();
                if (!configs.isEmpty()) {
                    configFindings.addAll(configs);
                }
            }
            if (iamFindings.isEmpty()) {
                List<Finding> iams = scanFindings.stream()
                        .filter(ff -> "IAM".equals(ff.getCategory()))
                        .toList();
                if (!iams.isEmpty()) {
                    iamFindings.addAll(iams);
                }
            }
            if (!configFindings.isEmpty() && !iamFindings.isEmpty()) break;
        }

        List<IamIdentity> identities = iamIdentityRepository.findAll();
        List<Finding> correlatedFindings = new ArrayList<>();

        // a) S3 exposure + S3 access
        List<Finding> s3ConfigFindings = configFindings.stream()
                .filter(f -> "S3".equals(f.getResourceType()) && f.getTitle() != null
                        && (f.getTitle().toLowerCase().contains("public") || f.getTitle().toLowerCase().contains("exposure")))
                .toList();

        if (!s3ConfigFindings.isEmpty()) {
            for (IamIdentity identity : identities) {
                boolean hasS3Access = identity.getPolicies().stream()
                        .anyMatch(p -> hasServiceAccess(p, "s3"));
                if (hasS3Access) {
                    for (Finding s3Finding : s3ConfigFindings) {
                        correlatedFindings.add(Finding.builder()
                                .id(UUID.randomUUID().toString())
                                .resourceType("S3")
                                .resourceId(s3Finding.getResourceId())
                                .severity("CRITICAL")
                                .title("Over-privileged identity can access public S3 bucket")
                                .description(String.format(
                                        "Identity '%s' has S3 access permissions and bucket '%s' is publicly exposed. " +
                                        "An attacker could leverage this identity to exfiltrate data from the public bucket.",
                                        identity.getName(), s3Finding.getResourceId()))
                                .category("CORRELATED")
                                .primaryIdentityArn(identity.getArn())
                                .build());
                    }
                }
            }
        }

        // b) EC2 SG exposure + EC2 access
        List<Finding> ec2ConfigFindings = configFindings.stream()
                .filter(f -> "EC2".equals(f.getResourceType()) && f.getTitle() != null
                        && (f.getTitle().toLowerCase().contains("ssh") || f.getTitle().toLowerCase().contains("rdp")
                            || f.getTitle().toLowerCase().contains("security group")))
                .toList();

        if (!ec2ConfigFindings.isEmpty()) {
            for (IamIdentity identity : identities) {
                boolean hasEc2Access = identity.getPolicies().stream()
                        .anyMatch(p -> hasServiceAccess(p, "ec2"));
                if (hasEc2Access) {
                    for (Finding ec2Finding : ec2ConfigFindings) {
                        correlatedFindings.add(Finding.builder()
                                .id(UUID.randomUUID().toString())
                                .resourceType("EC2")
                                .resourceId(ec2Finding.getResourceId())
                                .severity("HIGH")
                                .title("Identity with EC2 access and exposed security group")
                                .description(String.format(
                                        "Identity '%s' has EC2 access and security group '%s' has open ports. " +
                                        "This combination could allow lateral movement from a compromised instance.",
                                        identity.getName(), ec2Finding.getResourceId()))
                                .category("CORRELATED")
                                .primaryIdentityArn(identity.getArn())
                                .build());
                    }
                }
            }
        }

        // c) Admin identity + any HIGH config finding
        List<Finding> highConfigFindings = configFindings.stream()
                .filter(f -> "HIGH".equals(f.getSeverity()))
                .toList();

        if (!highConfigFindings.isEmpty()) {
            for (IamIdentity identity : identities) {
                boolean isAdmin = identity.getPolicies().stream().anyMatch(IamPolicy::isAdminLike);
                if (isAdmin) {
                    correlatedFindings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("IAM")
                            .resourceId(identity.getArn())
                            .severity("HIGH")
                            .title("Admin identity can exploit misconfigured resources")
                            .description(String.format(
                                    "Identity '%s' has admin-like access and there are %d high-severity configuration findings. " +
                                    "An attacker with these credentials could exploit misconfigured resources across the environment.",
                                    identity.getName(), highConfigFindings.size()))
                            .category("CORRELATED")
                            .primaryIdentityArn(identity.getArn())
                            .build());
                }
            }
        }

        long highCount = correlatedFindings.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
        long mediumCount = correlatedFindings.stream().filter(f -> "MEDIUM".equals(f.getSeverity())).count();
        long lowCount = correlatedFindings.stream().filter(f -> "LOW".equals(f.getSeverity())).count();
        long criticalCount = correlatedFindings.stream().filter(f -> "CRITICAL".equals(f.getSeverity())).count();

        ScanResult result = ScanResult.builder()
                .scanId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .status("COMPLETED")
                .findings(correlatedFindings)
                .totalFindings(correlatedFindings.size())
                .highSeverity((int) (highCount + criticalCount))
                .mediumSeverity((int) mediumCount)
                .lowSeverity((int) lowCount)
                .build();

        correlatedFindings.forEach(f -> f.setScanResult(result));

        ScanResult saved = scanResultRepository.save(result);
        log.info("Correlation analysis completed: {} correlated findings", correlatedFindings.size());
        return saved;
    }

    private boolean hasServiceAccess(IamPolicy policy, String service) {
        if (policy.isAdminLike()) return true;
        String doc = policy.getPolicyDocument();
        if (doc == null) return false;
        String lower = doc.toLowerCase();
        return lower.contains("\"" + service + ":*\"") || lower.contains("\"" + service + ":get")
                || lower.contains("\"action\":\"*\"");
    }
}
