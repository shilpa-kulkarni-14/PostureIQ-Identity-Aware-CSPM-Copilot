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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Mock implementation of {@link RemediationToolService} used when
 * {@code aws.scanner.enabled} is false (default).
 * Returns realistic mock responses with simulated delays.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "false", matchIfMissing = true)
public class MockRemediationToolService implements RemediationToolService {

    private final FindingRepository findingRepository;
    private final ScanResultRepository scanResultRepository;
    private final IamIdentityRepository iamIdentityRepository;

    // ── Write tools ────────────────────────────────────────────────────

    @Override
    public ToolResult blockS3PublicAccess(String bucketName) {
        log.info("[MOCK] Blocking public access on S3 bucket: {}", bucketName);
        simulateDelay();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucketName", bucketName);
        data.put("beforeState", Map.of(
                "blockPublicAcls", false,
                "ignorePublicAcls", false,
                "blockPublicPolicy", false,
                "restrictPublicBuckets", false
        ));
        data.put("afterState", Map.of(
                "blockPublicAcls", true,
                "ignorePublicAcls", true,
                "blockPublicPolicy", true,
                "restrictPublicBuckets", true
        ));
        return ToolResult.success(
                "Successfully blocked all public access on bucket " + bucketName, data);
    }

    @Override
    public ToolResult restrictSecurityGroup(String groupId, int port, String protocol) {
        log.info("[MOCK] Restricting security group {} on port {}/{}", groupId, port, protocol);
        simulateDelay();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("beforeState", Map.of(
                "groupId", groupId,
                "ingressRules", List.of(Map.of(
                        "protocol", protocol,
                        "port", port,
                        "cidr", "0.0.0.0/0",
                        "description", "Open to the world"
                ))
        ));
        data.put("afterState", Map.of(
                "groupId", groupId,
                "ingressRules", List.of()
        ));
        return ToolResult.success(
                "Removed 0.0.0.0/0 ingress rule for port " + port + "/" + protocol
                        + " on security group " + groupId, data);
    }

    @Override
    public ToolResult enableEbsEncryption(String volumeId) {
        log.info("[MOCK] Enabling EBS encryption for volume: {}", volumeId);
        simulateDelay();

        String mockSnapshotId = "snap-mock-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("volumeId", volumeId);
        data.put("beforeState", Map.of(
                "encrypted", false,
                "volumeId", volumeId
        ));
        data.put("afterState", Map.of(
                "encrypted", true,
                "volumeId", volumeId,
                "snapshotId", mockSnapshotId
        ));
        return ToolResult.success(
                "Created encrypted snapshot " + mockSnapshotId + " for volume " + volumeId, data);
    }

    @Override
    public ToolResult enableCloudTrail(String trailName) {
        log.info("[MOCK] Enabling CloudTrail logging for trail: {}", trailName);
        simulateDelay();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trailName", trailName);
        data.put("beforeState", Map.of("isLogging", false));
        data.put("afterState", Map.of("isLogging", true));
        return ToolResult.success(
                "Started logging on CloudTrail trail " + trailName, data);
    }

    @Override
    public ToolResult rotateAccessKey(String username, String accessKeyId) {
        log.info("[MOCK] Rotating access key {} for user: {}", accessKeyId, username);
        simulateDelay();

        String newKeyId = "AKIAMOCK" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("beforeState", Map.of(
                "accessKeyId", accessKeyId,
                "status", "Active"
        ));
        data.put("afterState", Map.of(
                "oldAccessKeyId", accessKeyId,
                "oldKeyStatus", "Inactive",
                "newAccessKeyId", newKeyId,
                "newKeyStatus", "Active"
        ));
        return ToolResult.success(
                "Deactivated key " + accessKeyId + " and created new key " + newKeyId
                        + " for user " + username, data);
    }

    @Override
    public ToolResult deleteUnusedCredentials(String username, String accessKeyId) {
        log.info("[MOCK] Deleting unused credentials {} for user: {}", accessKeyId, username);
        simulateDelay();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("beforeState", Map.of(
                "accessKeyId", accessKeyId,
                "status", "Active"
        ));
        data.put("afterState", Map.of(
                "accessKeyId", accessKeyId,
                "status", "Deleted"
        ));
        return ToolResult.success(
                "Deleted access key " + accessKeyId + " for user " + username, data);
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

        // Filter to identities that are potentially high-risk:
        // no MFA, have console access, or haven't been used recently
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

    private void simulateDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(200, 801));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

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
