package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.ScanResult;
import com.cspm.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "false", matchIfMissing = true)
public class MockAwsScanner implements ScannerService {

    private final ScanResultRepository scanResultRepository;

    @Override
    @Transactional
    public ScanResult runScan() {
        log.info("Running mock AWS security scan (no AWS credentials configured)");
        String scanId = UUID.randomUUID().toString();
        List<Finding> findings = generateMockFindings();

        long highCount = findings.stream().filter(f -> "HIGH".equals(f.getSeverity())).count();
        long mediumCount = findings.stream().filter(f -> "MEDIUM".equals(f.getSeverity())).count();
        long lowCount = findings.stream().filter(f -> "LOW".equals(f.getSeverity())).count();

        ScanResult result = ScanResult.builder()
                .scanId(scanId)
                .timestamp(Instant.now())
                .status("COMPLETED")
                .findings(findings)
                .totalFindings(findings.size())
                .highSeverity((int) highCount)
                .mediumSeverity((int) mediumCount)
                .lowSeverity((int) lowCount)
                .build();

        // Set the back-reference on each finding
        findings.forEach(f -> f.setScanResult(result));

        return scanResultRepository.save(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScanResult> getScanResult(String scanId) {
        return scanResultRepository.findById(scanId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScanResult> getScanResultWithFindings(String scanId) {
        return scanResultRepository.findByIdWithFindings(scanId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScanResult> getAllScans() {
        return scanResultRepository.findAllByOrderByTimestampDesc();
    }

    private List<Finding> generateMockFindings() {
        List<Finding> findings = new ArrayList<>();

        // S3 Public Bucket Findings
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("S3")
                .resourceId("arn:aws:s3:::company-public-assets")
                .severity("HIGH")
                .title("S3 Bucket Has Public Access Enabled")
                .description("The S3 bucket 'company-public-assets' has public access enabled through its bucket policy. " +
                        "This allows anyone on the internet to access the contents of this bucket, potentially exposing sensitive data.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("S3")
                .resourceId("arn:aws:s3:::app-logs-backup")
                .severity("HIGH")
                .title("S3 Bucket ACL Allows Public Read")
                .description("The S3 bucket 'app-logs-backup' has an ACL that grants public read access. " +
                        "Application logs may contain sensitive information such as IP addresses, user IDs, and error details.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("S3")
                .resourceId("arn:aws:s3:::customer-uploads-prod")
                .severity("MEDIUM")
                .title("S3 Bucket Missing Server-Side Encryption")
                .description("The S3 bucket 'customer-uploads-prod' does not have default server-side encryption enabled. " +
                        "Data at rest should be encrypted to protect against unauthorized access.")
                .build());

        // IAM Policy Findings
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("IAM")
                .resourceId("arn:aws:iam::123456789012:policy/DeveloperFullAccess")
                .severity("HIGH")
                .title("IAM Policy Grants Full Administrative Access")
                .description("The IAM policy 'DeveloperFullAccess' contains 'Action': '*' and 'Resource': '*' permissions. " +
                        "This violates the principle of least privilege and could allow unintended access to all AWS resources.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("IAM")
                .resourceId("arn:aws:iam::123456789012:user/deploy-service")
                .severity("HIGH")
                .title("IAM User Has Inline Policy with Excessive Permissions")
                .description("The IAM user 'deploy-service' has an inline policy allowing 'iam:*' actions. " +
                        "Service accounts should have minimal permissions required for their function.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("IAM")
                .resourceId("arn:aws:iam::123456789012:role/LambdaExecutionRole")
                .severity("MEDIUM")
                .title("IAM Role Missing Permission Boundary")
                .description("The IAM role 'LambdaExecutionRole' does not have a permission boundary attached. " +
                        "Permission boundaries help prevent privilege escalation.")
                .build());

        // Security Group Findings
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("EC2")
                .resourceId("sg-0abc123def456789")
                .severity("HIGH")
                .title("Security Group Allows SSH from 0.0.0.0/0")
                .description("Security group 'sg-0abc123def456789' allows inbound SSH (port 22) from any IP address (0.0.0.0/0). " +
                        "This exposes instances to brute force attacks and unauthorized access attempts.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("EC2")
                .resourceId("sg-0def789abc123456")
                .severity("HIGH")
                .title("Security Group Allows RDP from Any IP")
                .description("Security group 'sg-0def789abc123456' allows inbound RDP (port 3389) from 0.0.0.0/0. " +
                        "Windows instances are exposed to remote desktop attacks from the internet.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("EC2")
                .resourceId("sg-0ghi456jkl789012")
                .severity("MEDIUM")
                .title("Security Group Has Overly Permissive Egress Rules")
                .description("Security group 'sg-0ghi456jkl789012' allows all outbound traffic to 0.0.0.0/0. " +
                        "Consider restricting egress to only required destinations and ports.")
                .build());

        // EBS Findings
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("EBS")
                .resourceId("vol-0123456789abcdef0")
                .severity("MEDIUM")
                .title("EBS Volume Not Encrypted")
                .description("EBS volume 'vol-0123456789abcdef0' attached to instance 'i-0abc123' is not encrypted. " +
                        "Unencrypted volumes may expose data if the underlying hardware is compromised.")
                .build());

        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("EBS")
                .resourceId("snap-0fedcba9876543210")
                .severity("LOW")
                .title("EBS Snapshot is Public")
                .description("EBS snapshot 'snap-0fedcba9876543210' is shared publicly. " +
                        "Public snapshots can be accessed by any AWS account, potentially exposing sensitive data.")
                .build());

        // CloudTrail Findings
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("CloudTrail")
                .resourceId("arn:aws:cloudtrail:us-east-1:123456789012:trail/management-trail")
                .severity("HIGH")
                .title("CloudTrail Logging Is Disabled")
                .description("CloudTrail trail 'management-trail' exists but logging is disabled. " +
                        "Without active logging, API calls are not recorded, making it impossible to detect unauthorized activity.")
                .build());

        // Default VPC Finding
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("VPC")
                .resourceId("vpc-0a1b2c3d4e5f67890")
                .severity("LOW")
                .title("Default VPC Is Present")
                .description("The default VPC 'vpc-0a1b2c3d4e5f67890' (172.31.0.0/16) exists in this region. " +
                        "Default VPCs have permissive network configurations that may not align with security best practices. " +
                        "Consider migrating workloads to custom VPCs with properly configured subnets, NACLs, and route tables.")
                .build());

        // Unused Credentials Finding
        findings.add(Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType("IAM")
                .resourceId("arn:aws:iam::123456789012:user/legacy-service-account")
                .severity("MEDIUM")
                .title("IAM Access Key Unused for Over 90 Days")
                .description("The IAM user 'legacy-service-account' has an active access key (ID: AKIAIOSFODNN7EXAMPLE) " +
                        "last used 147 days ago. Stale credentials should be deactivated to reduce the risk of compromised keys being exploited.")
                .build());

        return findings;
    }
}
