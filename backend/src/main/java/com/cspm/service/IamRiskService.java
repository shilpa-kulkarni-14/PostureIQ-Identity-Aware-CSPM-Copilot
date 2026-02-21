package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.IamIdentity;
import com.cspm.model.IamPolicy;
import com.cspm.model.ScanResult;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.IamPolicyRepository;
import com.cspm.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IamRiskService {

    private final IamIdentityRepository iamIdentityRepository;
    private final IamPolicyRepository iamPolicyRepository;
    private final FindingRepository findingRepository;
    private final ScanResultRepository scanResultRepository;

    @Transactional
    public ScanResult runIamScan() {
        log.info("Starting IAM risk scan");

        List<IamIdentity> identities = iamIdentityRepository.findAll();
        List<Finding> findings = new ArrayList<>();

        for (IamIdentity identity : identities) {
            List<IamPolicy> policies = identity.getPolicies();

            // Check a) Admin-like policies
            boolean hasAdminLike = policies.stream().anyMatch(IamPolicy::isAdminLike);
            if (hasAdminLike) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("IAM")
                        .resourceId(identity.getArn())
                        .severity("HIGH")
                        .title("IAM identity has admin-like access")
                        .description(String.format("Identity '%s' (%s) has admin-like policies attached, granting broad access to AWS resources.",
                                identity.getName(), identity.getArn()))
                        .category("IAM")
                        .primaryIdentityArn(identity.getArn())
                        .build());
            }

            // Check b) Console without MFA
            if (identity.isHasConsoleAccess() && !identity.isMfaEnabled()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("IAM")
                        .resourceId(identity.getArn())
                        .severity("HIGH")
                        .title("Console user without MFA")
                        .description(String.format("Identity '%s' has console access but MFA is not enabled, making the account vulnerable to credential-based attacks.",
                                identity.getName()))
                        .category("IAM")
                        .primaryIdentityArn(identity.getArn())
                        .build());
            }

            // Check c) Dormant high-privilege
            if (identity.getLastUsed() != null && hasAdminLike) {
                long daysSinceUsed = ChronoUnit.DAYS.between(identity.getLastUsed(), Instant.now());
                if (daysSinceUsed > 90) {
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("IAM")
                            .resourceId(identity.getArn())
                            .severity("MEDIUM")
                            .title("Dormant high-privilege identity")
                            .description(String.format("Identity '%s' has admin-like access but has not been used for %d days. Dormant privileged accounts pose a significant security risk.",
                                    identity.getName(), daysSinceUsed))
                            .category("IAM")
                            .primaryIdentityArn(identity.getArn())
                            .build());
                }
            }

            // Check d) Wildcard service access (roles only)
            if (identity.getIdentityType() == IamIdentity.IdentityType.ROLE) {
                boolean hasWildcard = policies.stream().anyMatch(IamPolicy::isHasWildcardActions);
                if (hasWildcard) {
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("IAM")
                            .resourceId(identity.getArn())
                            .severity("MEDIUM")
                            .title("Service account with broad wildcard access")
                            .description(String.format("Role '%s' has policies with wildcard actions, granting overly broad access for a service account.",
                                    identity.getName()))
                            .category("IAM")
                            .primaryIdentityArn(identity.getArn())
                            .build());
                }
            }
        }

        long highCount = findings.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
        long mediumCount = findings.stream().filter(f -> "MEDIUM".equals(f.getSeverity())).count();
        long lowCount = findings.stream().filter(f -> "LOW".equals(f.getSeverity())).count();

        ScanResult result = ScanResult.builder()
                .scanId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .status("COMPLETED")
                .findings(findings)
                .totalFindings(findings.size())
                .highSeverity((int) highCount)
                .mediumSeverity((int) mediumCount)
                .lowSeverity((int) lowCount)
                .build();

        findings.forEach(f -> f.setScanResult(result));

        ScanResult saved = scanResultRepository.save(result);
        log.info("IAM risk scan completed: {} findings ({} high, {} medium, {} low)",
                findings.size(), highCount, mediumCount, lowCount);
        return saved;
    }
}
