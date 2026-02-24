package com.cspm.mcp;

import com.cspm.model.Finding;
import com.cspm.model.IamIdentity;
import com.cspm.model.ScanResult;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.StartLoggingRequest;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CopySnapshotRequest;
import software.amazon.awssdk.services.ec2.model.CopySnapshotResponse;
import software.amazon.awssdk.services.ec2.model.CreateSnapshotRequest;
import software.amazon.awssdk.services.ec2.model.CreateSnapshotResponse;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.RevokeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.StatusType;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Real AWS implementation of {@link RemediationToolService}.
 * Active only when {@code aws.scanner.enabled=true}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "true")
public class AwsRemediationToolService implements RemediationToolService {

    private final S3Client s3Client;
    private final Ec2Client ec2Client;
    private final IamClient iamClient;
    private final CloudTrailClient cloudTrailClient;
    private final FindingRepository findingRepository;
    private final ScanResultRepository scanResultRepository;
    private final IamIdentityRepository iamIdentityRepository;

    // ── Write tools ────────────────────────────────────────────────────

    @Override
    public ToolResult blockS3PublicAccess(String bucketName) {
        log.info("Blocking public access on S3 bucket: {}", bucketName);
        try {
            Map<String, Object> beforeState = Map.of(
                    "blockPublicAcls", false,
                    "ignorePublicAcls", false,
                    "blockPublicPolicy", false,
                    "restrictPublicBuckets", false
            );

            s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                            .blockPublicAcls(true)
                            .ignorePublicAcls(true)
                            .blockPublicPolicy(true)
                            .restrictPublicBuckets(true)
                            .build())
                    .build());

            Map<String, Object> afterState = Map.of(
                    "blockPublicAcls", true,
                    "ignorePublicAcls", true,
                    "blockPublicPolicy", true,
                    "restrictPublicBuckets", true
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bucketName", bucketName);
            data.put("beforeState", beforeState);
            data.put("afterState", afterState);
            return ToolResult.success(
                    "Successfully blocked all public access on bucket " + bucketName, data);

        } catch (Exception e) {
            log.error("Failed to block public access on bucket {}: {}", bucketName, e.getMessage(), e);
            return ToolResult.failure("Failed to block public access on bucket "
                    + bucketName + ": " + e.getMessage());
        }
    }

    @Override
    public ToolResult restrictSecurityGroup(String groupId, int port, String protocol) {
        log.info("Restricting security group {} on port {}/{}", groupId, port, protocol);
        try {
            Map<String, Object> beforeState = Map.of(
                    "groupId", groupId,
                    "ingressRules", List.of(Map.of(
                            "protocol", protocol,
                            "port", port,
                            "cidr", "0.0.0.0/0"
                    ))
            );

            IpPermission permission = IpPermission.builder()
                    .ipProtocol(protocol)
                    .fromPort(port)
                    .toPort(port)
                    .ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
                    .build();

            ec2Client.revokeSecurityGroupIngress(RevokeSecurityGroupIngressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(permission)
                    .build());

            Map<String, Object> afterState = Map.of(
                    "groupId", groupId,
                    "ingressRules", List.of()
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("groupId", groupId);
            data.put("beforeState", beforeState);
            data.put("afterState", afterState);
            return ToolResult.success(
                    "Removed 0.0.0.0/0 ingress rule for port " + port + "/" + protocol
                            + " on security group " + groupId, data);

        } catch (Exception e) {
            log.error("Failed to restrict security group {}: {}", groupId, e.getMessage(), e);
            return ToolResult.failure("Failed to restrict security group "
                    + groupId + ": " + e.getMessage());
        }
    }

    @Override
    public ToolResult enableEbsEncryption(String volumeId) {
        log.info("Enabling EBS encryption for volume: {}", volumeId);
        try {
            Map<String, Object> beforeState = Map.of(
                    "encrypted", false,
                    "volumeId", volumeId
            );

            // Step 1: Create a snapshot of the unencrypted volume
            CreateSnapshotResponse snapshotResponse = ec2Client.createSnapshot(
                    CreateSnapshotRequest.builder()
                            .volumeId(volumeId)
                            .description("PostureIQ remediation - encrypted copy of " + volumeId)
                            .build());

            String snapshotId = snapshotResponse.snapshotId();

            // Step 2: Copy the snapshot with encryption enabled
            CopySnapshotResponse copyResponse = ec2Client.copySnapshot(
                    CopySnapshotRequest.builder()
                            .sourceSnapshotId(snapshotId)
                            .sourceRegion(s3Client.serviceClientConfiguration()
                                    .region().id())
                            .encrypted(true)
                            .description("PostureIQ encrypted copy of snapshot " + snapshotId)
                            .build());

            String encryptedSnapshotId = copyResponse.snapshotId();

            Map<String, Object> afterState = Map.of(
                    "encrypted", true,
                    "volumeId", volumeId,
                    "originalSnapshotId", snapshotId,
                    "encryptedSnapshotId", encryptedSnapshotId
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("volumeId", volumeId);
            data.put("beforeState", beforeState);
            data.put("afterState", afterState);
            return ToolResult.success(
                    "Created encrypted snapshot " + encryptedSnapshotId + " for volume " + volumeId, data);

        } catch (Exception e) {
            log.error("Failed to enable EBS encryption for volume {}: {}", volumeId, e.getMessage(), e);
            return ToolResult.failure("Failed to enable EBS encryption for volume "
                    + volumeId + ": " + e.getMessage());
        }
    }

    @Override
    public ToolResult enableCloudTrail(String trailName) {
        log.info("Enabling CloudTrail logging for trail: {}", trailName);
        try {
            Map<String, Object> beforeState = Map.of("isLogging", false);

            cloudTrailClient.startLogging(StartLoggingRequest.builder()
                    .name(trailName)
                    .build());

            Map<String, Object> afterState = Map.of("isLogging", true);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("trailName", trailName);
            data.put("beforeState", beforeState);
            data.put("afterState", afterState);
            return ToolResult.success(
                    "Started logging on CloudTrail trail " + trailName, data);

        } catch (Exception e) {
            log.error("Failed to enable CloudTrail for trail {}: {}", trailName, e.getMessage(), e);
            return ToolResult.failure("Failed to enable CloudTrail for trail "
                    + trailName + ": " + e.getMessage());
        }
    }

    @Override
    public ToolResult rotateAccessKey(String username, String accessKeyId) {
        log.info("Rotating access key {} for user: {}", accessKeyId, username);
        try {
            Map<String, Object> beforeState = Map.of(
                    "accessKeyId", accessKeyId,
                    "status", "Active"
            );

            // Step 1: Deactivate the old key
            iamClient.updateAccessKey(UpdateAccessKeyRequest.builder()
                    .userName(username)
                    .accessKeyId(accessKeyId)
                    .status(StatusType.INACTIVE)
                    .build());

            // Step 2: Create a new key
            CreateAccessKeyResponse createResponse = iamClient.createAccessKey(
                    CreateAccessKeyRequest.builder()
                            .userName(username)
                            .build());

            String newKeyId = createResponse.accessKey().accessKeyId();

            Map<String, Object> afterState = Map.of(
                    "oldAccessKeyId", accessKeyId,
                    "oldKeyStatus", "Inactive",
                    "newAccessKeyId", newKeyId,
                    "newKeyStatus", "Active"
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("beforeState", beforeState);
            data.put("afterState", afterState);
            return ToolResult.success(
                    "Deactivated key " + accessKeyId + " and created new key " + newKeyId
                            + " for user " + username, data);

        } catch (Exception e) {
            log.error("Failed to rotate access key for user {}: {}", username, e.getMessage(), e);
            return ToolResult.failure("Failed to rotate access key for user "
                    + username + ": " + e.getMessage());
        }
    }

    @Override
    public ToolResult deleteUnusedCredentials(String username, String accessKeyId) {
        log.info("Deleting unused credentials {} for user: {}", accessKeyId, username);
        try {
            Map<String, Object> beforeState = Map.of(
                    "accessKeyId", accessKeyId,
                    "status", "Active"
            );

            iamClient.deleteAccessKey(DeleteAccessKeyRequest.builder()
                    .userName(username)
                    .accessKeyId(accessKeyId)
                    .build());

            Map<String, Object> afterState = Map.of(
                    "accessKeyId", accessKeyId,
                    "status", "Deleted"
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("username", username);
            data.put("beforeState", beforeState);
            data.put("afterState", afterState);
            return ToolResult.success(
                    "Deleted access key " + accessKeyId + " for user " + username, data);

        } catch (Exception e) {
            log.error("Failed to delete credentials for user {}: {}", username, e.getMessage(), e);
            return ToolResult.failure("Failed to delete credentials for user "
                    + username + ": " + e.getMessage());
        }
    }

    // ── Read-only tools ────────────────────────────────────────────────

    @Override
    public ToolResult getScanFindings(String scanId) {
        log.info("Fetching scan findings for scanId: {}", scanId);

        Optional<ScanResult> optionalScan = scanResultRepository.findByIdWithFindings(scanId);
        if (optionalScan.isEmpty()) {
            return ToolResult.failure("Scan not found: " + scanId);
        }

        ScanResult scan = optionalScan.get();
        List<Map<String, Object>> findingsList = scan.getFindings().stream()
                .map(this::findingToMap)
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scanId", scan.getScanId());
        data.put("status", scan.getStatus());
        data.put("timestamp", scan.getTimestamp() != null ? scan.getTimestamp().toString() : null);
        data.put("totalFindings", scan.getTotalFindings());
        data.put("highSeverity", scan.getHighSeverity());
        data.put("mediumSeverity", scan.getMediumSeverity());
        data.put("lowSeverity", scan.getLowSeverity());
        data.put("findings", findingsList);

        return ToolResult.success(
                "Retrieved " + findingsList.size() + " findings for scan " + scanId, data);
    }

    @Override
    public ToolResult getFindingDetails(String findingId) {
        log.info("Fetching finding details for findingId: {}", findingId);

        Optional<Finding> optionalFinding = findingRepository.findById(findingId);
        if (optionalFinding.isEmpty()) {
            return ToolResult.failure("Finding not found: " + findingId);
        }

        Finding finding = optionalFinding.get();
        Map<String, Object> data = findingToMap(finding);

        return ToolResult.success(
                "Retrieved finding details for " + findingId, data);
    }

    @Override
    public ToolResult getHighRiskIdentities() {
        log.info("Fetching high-risk IAM identities");

        List<IamIdentity> identities = iamIdentityRepository.findAll();

        List<Map<String, Object>> highRisk = identities.stream()
                .filter(id -> !id.isMfaEnabled() || id.isHasConsoleAccess())
                .map(id -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", id.getId());
                    m.put("name", id.getName());
                    m.put("arn", id.getArn());
                    m.put("identityType", id.getIdentityType() != null ? id.getIdentityType().name() : null);
                    m.put("hasConsoleAccess", id.isHasConsoleAccess());
                    m.put("mfaEnabled", id.isMfaEnabled());
                    m.put("lastUsed", id.getLastUsed() != null ? id.getLastUsed().toString() : null);
                    m.put("policyCount", id.getPolicies() != null ? id.getPolicies().size() : 0);
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalIdentities", identities.size());
        data.put("highRiskCount", highRisk.size());
        data.put("identities", highRisk);

        return ToolResult.success(
                "Found " + highRisk.size() + " high-risk identities out of " + identities.size(), data);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Map<String, Object> findingToMap(Finding finding) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", finding.getId());
        m.put("resourceType", finding.getResourceType());
        m.put("resourceId", finding.getResourceId());
        m.put("severity", finding.getSeverity());
        m.put("title", finding.getTitle());
        m.put("description", finding.getDescription());
        m.put("remediation", finding.getRemediation());
        m.put("category", finding.getCategory());
        m.put("primaryIdentityArn", finding.getPrimaryIdentityArn());
        return m;
    }
}
