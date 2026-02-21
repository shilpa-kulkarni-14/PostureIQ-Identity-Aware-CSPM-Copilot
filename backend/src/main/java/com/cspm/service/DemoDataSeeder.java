package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.ScanResult;
import com.cspm.model.User;
import com.cspm.repository.ScanResultRepository;
import com.cspm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "demo.seed-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ScanResultRepository scanResultRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Demo data seeder: starting...");

        createDemoUser();
        createHistoricalScans();

        log.info("Demo data seeder: completed");
    }

    private void createDemoUser() {
        if (userRepository.existsByUsername("demo")) {
            log.info("Demo user already exists, skipping");
            return;
        }

        User demoUser = User.builder()
                .username("demo")
                .password(passwordEncoder.encode("demo1234"))
                .email("demo@cspm.io")
                .role("USER")
                .build();

        userRepository.save(demoUser);
        log.info("Created demo user (username: demo)");
    }

    private void createHistoricalScans() {
        if (scanResultRepository.count() > 0) {
            log.info("Scan data already exists, skipping seed");
            return;
        }

        // 5 historical CSPM scans with decreasing finding counts (showing improvement)
        int[][] scanData = {
                {6, 5, 3},  // 30 days ago: 6 high, 5 medium, 3 low
                {5, 4, 3},  // 24 days ago
                {4, 4, 2},  // 18 days ago
                {3, 3, 2},  // 12 days ago
                {2, 3, 1},  // 6 days ago
        };

        for (int i = 0; i < scanData.length; i++) {
            int daysAgo = 30 - (i * 6);
            int high = scanData[i][0];
            int medium = scanData[i][1];
            int low = scanData[i][2];

            createCspmScan(daysAgo, high, medium, low);
        }

        // 1 IAM scan (3 days ago)
        createIamScan(3);

        // 1 correlation scan (2 days ago)
        createCorrelationScan(2);

        log.info("Created 7 historical scans with demo data");
    }

    private void createCspmScan(int daysAgo, int highCount, int mediumCount, int lowCount) {
        String scanId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        List<Finding> findings = new ArrayList<>();

        String[][] highFindings = {
                {"S3", "arn:aws:s3:::company-public-assets", "S3 Bucket Has Public Access Enabled", "The S3 bucket has public access enabled through its bucket policy."},
                {"IAM", "arn:aws:iam::123456789012:policy/DeveloperFullAccess", "IAM Policy Grants Full Administrative Access", "Contains Action:* and Resource:* permissions."},
                {"EC2", "sg-0abc123def456789", "Security Group Allows SSH from 0.0.0.0/0", "Allows inbound SSH (port 22) from any IP address."},
                {"S3", "arn:aws:s3:::app-logs-backup", "S3 Bucket ACL Allows Public Read", "Bucket ACL grants public read access to log data."},
                {"EC2", "sg-0def789abc123456", "Security Group Allows RDP from Any IP", "Allows inbound RDP (port 3389) from 0.0.0.0/0."},
                {"IAM", "arn:aws:iam::123456789012:user/deploy-service", "IAM User Has Inline Policy with Excessive Permissions", "Inline policy allowing iam:* actions."},
        };

        String[][] mediumFindings = {
                {"S3", "arn:aws:s3:::customer-uploads-prod", "S3 Bucket Missing Server-Side Encryption", "Does not have default server-side encryption enabled."},
                {"IAM", "arn:aws:iam::123456789012:role/LambdaExecutionRole", "IAM Role Missing Permission Boundary", "No permission boundary attached."},
                {"EC2", "sg-0ghi456jkl789012", "Security Group Has Overly Permissive Egress Rules", "Allows all outbound traffic to 0.0.0.0/0."},
                {"EBS", "vol-0123456789abcdef0", "EBS Volume Not Encrypted", "Volume is not encrypted."},
                {"EC2", "i-0abc123def456789", "EC2 Instance Missing IMDSv2", "Instance metadata service not enforcing IMDSv2."},
        };

        String[][] lowFindings = {
                {"EBS", "snap-0fedcba9876543210", "EBS Snapshot is Public", "Snapshot is shared publicly."},
                {"S3", "arn:aws:s3:::static-assets-cdn", "S3 Bucket Versioning Not Enabled", "Versioning is not enabled on the bucket."},
                {"IAM", "arn:aws:iam::123456789012:user/readonly-user", "IAM User Has Unused Access Keys", "Access keys not used in 90 days."},
        };

        for (int i = 0; i < Math.min(highCount, highFindings.length); i++) {
            findings.add(createFinding(highFindings[i][0], highFindings[i][1], "HIGH", highFindings[i][2], highFindings[i][3], "CONFIG"));
        }
        for (int i = 0; i < Math.min(mediumCount, mediumFindings.length); i++) {
            findings.add(createFinding(mediumFindings[i][0], mediumFindings[i][1], "MEDIUM", mediumFindings[i][2], mediumFindings[i][3], "CONFIG"));
        }
        for (int i = 0; i < Math.min(lowCount, lowFindings.length); i++) {
            findings.add(createFinding(lowFindings[i][0], lowFindings[i][1], "LOW", lowFindings[i][2], lowFindings[i][3], "CONFIG"));
        }

        ScanResult result = ScanResult.builder()
                .scanId(scanId)
                .timestamp(timestamp)
                .status("COMPLETED")
                .findings(findings)
                .totalFindings(findings.size())
                .highSeverity(highCount)
                .mediumSeverity(mediumCount)
                .lowSeverity(lowCount)
                .build();

        findings.forEach(f -> f.setScanResult(result));
        scanResultRepository.save(result);
    }

    private void createIamScan(int daysAgo) {
        String scanId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        List<Finding> findings = new ArrayList<>();

        findings.add(createFinding("IAM", "arn:aws:iam::123456789012:user/dev-user", "HIGH",
                "IAM User Without MFA Has Console Access",
                "User dev-user has console access but MFA is not enabled.", "IAM"));
        findings.add(createFinding("IAM", "arn:aws:iam::123456789012:user/dormant-admin", "HIGH",
                "Dormant Admin Account Detected",
                "User dormant-admin has AdministratorAccess but has not been used in 180 days.", "IAM"));
        findings.add(createFinding("IAM", "arn:aws:iam::123456789012:role/ci-role", "MEDIUM",
                "Service Role With Overly Broad Permissions",
                "Role ci-role has both S3FullAccess and EC2FullAccess policies.", "IAM"));

        ScanResult result = ScanResult.builder()
                .scanId(scanId)
                .timestamp(timestamp)
                .status("COMPLETED")
                .findings(findings)
                .totalFindings(findings.size())
                .highSeverity(2)
                .mediumSeverity(1)
                .lowSeverity(0)
                .build();

        findings.forEach(f -> f.setScanResult(result));
        scanResultRepository.save(result);
    }

    private void createCorrelationScan(int daysAgo) {
        String scanId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        List<Finding> findings = new ArrayList<>();

        findings.add(createFinding("IAM+S3", "arn:aws:iam::123456789012:user/dev-user", "HIGH",
                "Overprivileged User Can Access Public S3 Bucket",
                "User dev-user with PowerUserAccess can exfiltrate data from publicly accessible S3 bucket company-public-assets.",
                "CORRELATED"));
        findings.add(createFinding("IAM+EC2", "arn:aws:iam::123456789012:user/dormant-admin", "HIGH",
                "Dormant Admin Can Modify Open Security Groups",
                "Dormant account dormant-admin retains full access and could modify security groups allowing SSH/RDP from 0.0.0.0/0.",
                "CORRELATED"));

        ScanResult result = ScanResult.builder()
                .scanId(scanId)
                .timestamp(timestamp)
                .status("COMPLETED")
                .findings(findings)
                .totalFindings(findings.size())
                .highSeverity(2)
                .mediumSeverity(0)
                .lowSeverity(0)
                .build();

        findings.forEach(f -> f.setScanResult(result));
        scanResultRepository.save(result);
    }

    private Finding createFinding(String resourceType, String resourceId, String severity,
                                   String title, String description, String category) {
        return Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .severity(severity)
                .title(title)
                .description(description)
                .category(category)
                .build();
    }
}
