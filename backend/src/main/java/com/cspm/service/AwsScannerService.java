package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.ScanResult;
import com.cspm.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "true")
public class AwsScannerService implements ScannerService {

    private final S3Client s3Client;
    private final IamClient iamClient;
    private final Ec2Client ec2Client;
    private final ScanResultRepository scanResultRepository;

    @Override
    @Transactional
    public ScanResult runScan() {
        log.info("Starting real AWS security scan");
        String scanId = UUID.randomUUID().toString();
        List<Finding> findings = new ArrayList<>();

        findings.addAll(scanS3Buckets());
        findings.addAll(scanIamPolicies());
        findings.addAll(scanSecurityGroups());
        findings.addAll(scanEbsVolumes());

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

        findings.forEach(f -> f.setScanResult(result));

        log.info("AWS scan completed: {} findings ({} high, {} medium, {} low)",
                findings.size(), highCount, mediumCount, lowCount);
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

    // --- S3 Scanning ---

    private List<Finding> scanS3Buckets() {
        List<Finding> findings = new ArrayList<>();
        try {
            ListBucketsResponse bucketsResponse = s3Client.listBuckets();
            for (Bucket bucket : bucketsResponse.buckets()) {
                findings.addAll(checkBucketPublicAccess(bucket));
                findings.addAll(checkBucketEncryption(bucket));
            }
        } catch (S3Exception e) {
            log.error("Error scanning S3 buckets: {}", e.awsErrorDetails().errorMessage());
            findings.add(createErrorFinding("S3", "S3 Service",
                    "Unable to scan S3 buckets: " + e.awsErrorDetails().errorMessage()));
        }
        return findings;
    }

    private List<Finding> checkBucketPublicAccess(Bucket bucket) {
        List<Finding> findings = new ArrayList<>();
        String bucketName = bucket.name();
        String arn = "arn:aws:s3:::" + bucketName;

        try {
            GetPublicAccessBlockResponse publicAccess =
                    s3Client.getPublicAccessBlock(r -> r.bucket(bucketName));
            PublicAccessBlockConfiguration config = publicAccess.publicAccessBlockConfiguration();

            if (!config.blockPublicAcls() || !config.ignorePublicAcls()
                    || !config.blockPublicPolicy() || !config.restrictPublicBuckets()) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("S3")
                        .resourceId(arn)
                        .severity("HIGH")
                        .title("S3 Bucket Has Public Access Enabled")
                        .description(String.format(
                                "The S3 bucket '%s' does not have all public access blocks enabled. "
                                + "BlockPublicAcls=%s, IgnorePublicAcls=%s, BlockPublicPolicy=%s, RestrictPublicBuckets=%s.",
                                bucketName, config.blockPublicAcls(), config.ignorePublicAcls(),
                                config.blockPublicPolicy(), config.restrictPublicBuckets()))
                        .build());
            }
        } catch (S3Exception e) {
            if ("NoSuchPublicAccessBlockConfiguration".equals(e.awsErrorDetails().errorCode())) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("S3")
                        .resourceId(arn)
                        .severity("HIGH")
                        .title("S3 Bucket Has No Public Access Block Configuration")
                        .description(String.format(
                                "The S3 bucket '%s' has no public access block configuration. "
                                + "Without this, the bucket may be publicly accessible.", bucketName))
                        .build());
            } else {
                log.warn("Error checking public access for bucket {}: {}", bucketName,
                        e.awsErrorDetails().errorMessage());
            }
        }

        // Check bucket ACLs
        try {
            GetBucketAclResponse aclResponse = s3Client.getBucketAcl(r -> r.bucket(bucketName));
            for (Grant grant : aclResponse.grants()) {
                if (grant.grantee() != null && grant.grantee().uri() != null
                        && grant.grantee().uri().contains("AllUsers")) {
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("S3")
                            .resourceId(arn)
                            .severity("HIGH")
                            .title("S3 Bucket ACL Allows Public Access")
                            .description(String.format(
                                    "The S3 bucket '%s' has an ACL granting access to AllUsers (public). "
                                    + "Permission: %s.", bucketName, grant.permissionAsString()))
                            .build());
                    break;
                }
            }
        } catch (S3Exception e) {
            log.warn("Error checking ACLs for bucket {}: {}", bucketName,
                    e.awsErrorDetails().errorMessage());
        }

        return findings;
    }

    private List<Finding> checkBucketEncryption(Bucket bucket) {
        List<Finding> findings = new ArrayList<>();
        String bucketName = bucket.name();
        String arn = "arn:aws:s3:::" + bucketName;

        try {
            s3Client.getBucketEncryption(r -> r.bucket(bucketName));
        } catch (S3Exception e) {
            if ("ServerSideEncryptionConfigurationNotFoundError".equals(e.awsErrorDetails().errorCode())) {
                findings.add(Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("S3")
                        .resourceId(arn)
                        .severity("MEDIUM")
                        .title("S3 Bucket Missing Server-Side Encryption")
                        .description(String.format(
                                "The S3 bucket '%s' does not have default server-side encryption enabled. "
                                + "Data at rest should be encrypted to protect against unauthorized access.",
                                bucketName))
                        .build());
            } else {
                log.warn("Error checking encryption for bucket {}: {}", bucketName,
                        e.awsErrorDetails().errorMessage());
            }
        }

        return findings;
    }

    // --- IAM Scanning ---

    private List<Finding> scanIamPolicies() {
        List<Finding> findings = new ArrayList<>();
        try {
            // Check customer-managed policies for overly permissive actions
            findings.addAll(checkManagedPolicies());
            // Check users for MFA and access key rotation
            findings.addAll(checkIamUsers());
        } catch (IamException e) {
            log.error("Error scanning IAM: {}", e.awsErrorDetails().errorMessage());
            findings.add(createErrorFinding("IAM", "IAM Service",
                    "Unable to scan IAM: " + e.awsErrorDetails().errorMessage()));
        }
        return findings;
    }

    private List<Finding> checkManagedPolicies() {
        List<Finding> findings = new ArrayList<>();
        ListPoliciesResponse policiesResponse = iamClient.listPolicies(r -> r.scope("Local"));
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Policy policy : policiesResponse.policies()) {
            try {
                GetPolicyVersionResponse versionResponse = iamClient.getPolicyVersion(r -> r
                        .policyArn(policy.arn())
                        .versionId(policy.defaultVersionId()));
                String document = java.net.URLDecoder.decode(
                        versionResponse.policyVersion().document(), java.nio.charset.StandardCharsets.UTF_8);

                if (hasFullAdminAccess(objectMapper, document)) {
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("IAM")
                            .resourceId(policy.arn())
                            .severity("HIGH")
                            .title("IAM Policy Grants Full Administrative Access")
                            .description(String.format(
                                    "The IAM policy '%s' contains Action: '*' and Resource: '*' permissions. "
                                    + "This violates the principle of least privilege.",
                                    policy.policyName()))
                            .build());
                }
            } catch (IamException e) {
                log.warn("Error checking policy {}: {}", policy.policyName(),
                        e.awsErrorDetails().errorMessage());
            }
        }
        return findings;
    }

    private boolean hasFullAdminAccess(com.fasterxml.jackson.databind.ObjectMapper objectMapper, String policyDocument) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(policyDocument);
            com.fasterxml.jackson.databind.JsonNode statements = root.get("Statement");
            if (statements == null || !statements.isArray()) {
                return false;
            }
            for (com.fasterxml.jackson.databind.JsonNode statement : statements) {
                if (jsonFieldContainsValue(statement, "Action", "*")
                        && jsonFieldContainsValue(statement, "Resource", "*")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse IAM policy document: {}", e.getMessage());
        }
        return false;
    }

    private boolean jsonFieldContainsValue(com.fasterxml.jackson.databind.JsonNode node, String fieldName, String value) {
        com.fasterxml.jackson.databind.JsonNode field = node.get(fieldName);
        if (field == null) {
            return false;
        }
        if (field.isTextual()) {
            return value.equals(field.asText());
        }
        if (field.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode element : field) {
                if (element.isTextual() && value.equals(element.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Finding> checkIamUsers() {
        List<Finding> findings = new ArrayList<>();
        ListUsersResponse usersResponse = iamClient.listUsers();

        for (User user : usersResponse.users()) {
            // Check for MFA
            try {
                ListMfaDevicesResponse mfaResponse = iamClient.listMFADevices(r -> r.userName(user.userName()));
                if (mfaResponse.mfaDevices().isEmpty()) {
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("IAM")
                            .resourceId(user.arn())
                            .severity("MEDIUM")
                            .title("IAM User Missing MFA")
                            .description(String.format(
                                    "The IAM user '%s' does not have MFA enabled. "
                                    + "MFA adds an extra layer of protection on top of username and password.",
                                    user.userName()))
                            .build());
                }
            } catch (IamException e) {
                log.warn("Error checking MFA for user {}: {}", user.userName(),
                        e.awsErrorDetails().errorMessage());
            }

            // Check for old access keys
            try {
                ListAccessKeysResponse keysResponse = iamClient.listAccessKeys(r -> r.userName(user.userName()));
                for (AccessKeyMetadata key : keysResponse.accessKeyMetadata()) {
                    if (key.createDate() != null) {
                        long daysSinceCreation = java.time.Duration.between(
                                key.createDate(), Instant.now()).toDays();
                        if (daysSinceCreation > 90) {
                            findings.add(Finding.builder()
                                    .id(UUID.randomUUID().toString())
                                    .resourceType("IAM")
                                    .resourceId(user.arn())
                                    .severity("MEDIUM")
                                    .title("IAM Access Key Not Rotated")
                                    .description(String.format(
                                            "The IAM user '%s' has an access key (ID: %s) that is %d days old. "
                                            + "Access keys should be rotated every 90 days.",
                                            user.userName(), key.accessKeyId(), daysSinceCreation))
                                    .build());
                        }
                    }
                }
            } catch (IamException e) {
                log.warn("Error checking access keys for user {}: {}", user.userName(),
                        e.awsErrorDetails().errorMessage());
            }
        }
        return findings;
    }

    // --- EC2 Security Group Scanning ---

    private List<Finding> scanSecurityGroups() {
        List<Finding> findings = new ArrayList<>();
        try {
            DescribeSecurityGroupsResponse sgResponse = ec2Client.describeSecurityGroups();
            for (SecurityGroup sg : sgResponse.securityGroups()) {
                findings.addAll(checkSecurityGroupIngress(sg));
                findings.addAll(checkSecurityGroupEgress(sg));
            }
        } catch (Ec2Exception e) {
            log.error("Error scanning security groups: {}", e.awsErrorDetails().errorMessage());
            findings.add(createErrorFinding("EC2", "EC2 Service",
                    "Unable to scan security groups: " + e.awsErrorDetails().errorMessage()));
        }
        return findings;
    }

    private List<Finding> checkSecurityGroupIngress(SecurityGroup sg) {
        List<Finding> findings = new ArrayList<>();

        for (IpPermission permission : sg.ipPermissions()) {
            for (IpRange range : permission.ipRanges()) {
                if ("0.0.0.0/0".equals(range.cidrIp())) {
                    Integer fromPort = permission.fromPort();
                    if (fromPort != null && fromPort == 22) {
                        findings.add(Finding.builder()
                                .id(UUID.randomUUID().toString())
                                .resourceType("EC2")
                                .resourceId(sg.groupId())
                                .severity("HIGH")
                                .title("Security Group Allows SSH from 0.0.0.0/0")
                                .description(String.format(
                                        "Security group '%s' (%s) allows inbound SSH (port 22) from any IP address (0.0.0.0/0). "
                                        + "This exposes instances to brute force attacks.",
                                        sg.groupName(), sg.groupId()))
                                .build());
                    } else if (fromPort != null && fromPort == 3389) {
                        findings.add(Finding.builder()
                                .id(UUID.randomUUID().toString())
                                .resourceType("EC2")
                                .resourceId(sg.groupId())
                                .severity("HIGH")
                                .title("Security Group Allows RDP from Any IP")
                                .description(String.format(
                                        "Security group '%s' (%s) allows inbound RDP (port 3389) from 0.0.0.0/0. "
                                        + "Windows instances are exposed to remote desktop attacks.",
                                        sg.groupName(), sg.groupId()))
                                .build());
                    }
                }
            }
        }
        return findings;
    }

    private List<Finding> checkSecurityGroupEgress(SecurityGroup sg) {
        List<Finding> findings = new ArrayList<>();

        for (IpPermission permission : sg.ipPermissionsEgress()) {
            // Check for allow-all egress (protocol -1 means all)
            if ("-1".equals(permission.ipProtocol())) {
                for (IpRange range : permission.ipRanges()) {
                    if ("0.0.0.0/0".equals(range.cidrIp())) {
                        // Skip default security groups as they always have this
                        if (!"default".equals(sg.groupName())) {
                            findings.add(Finding.builder()
                                    .id(UUID.randomUUID().toString())
                                    .resourceType("EC2")
                                    .resourceId(sg.groupId())
                                    .severity("MEDIUM")
                                    .title("Security Group Has Overly Permissive Egress Rules")
                                    .description(String.format(
                                            "Security group '%s' (%s) allows all outbound traffic to 0.0.0.0/0. "
                                            + "Consider restricting egress to only required destinations and ports.",
                                            sg.groupName(), sg.groupId()))
                                    .build());
                        }
                    }
                }
            }
        }
        return findings;
    }

    // --- EBS Volume Scanning ---

    private List<Finding> scanEbsVolumes() {
        List<Finding> findings = new ArrayList<>();
        try {
            DescribeVolumesResponse volumesResponse = ec2Client.describeVolumes();
            for (Volume volume : volumesResponse.volumes()) {
                if (!volume.encrypted()) {
                    String attachedInstance = volume.attachments().isEmpty() ? "none"
                            : volume.attachments().get(0).instanceId();
                    findings.add(Finding.builder()
                            .id(UUID.randomUUID().toString())
                            .resourceType("EBS")
                            .resourceId(volume.volumeId())
                            .severity("MEDIUM")
                            .title("EBS Volume Not Encrypted")
                            .description(String.format(
                                    "EBS volume '%s' (attached to instance '%s') is not encrypted. "
                                    + "Unencrypted volumes may expose data if the underlying hardware is compromised.",
                                    volume.volumeId(), attachedInstance))
                            .build());
                }
            }
        } catch (Ec2Exception e) {
            log.error("Error scanning EBS volumes: {}", e.awsErrorDetails().errorMessage());
            findings.add(createErrorFinding("EBS", "EBS Service",
                    "Unable to scan EBS volumes: " + e.awsErrorDetails().errorMessage()));
        }
        return findings;
    }

    private Finding createErrorFinding(String resourceType, String resourceId, String message) {
        return Finding.builder()
                .id(UUID.randomUUID().toString())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .severity("LOW")
                .title("Scan Error: Unable to Complete " + resourceType + " Scan")
                .description(message)
                .build();
    }
}
