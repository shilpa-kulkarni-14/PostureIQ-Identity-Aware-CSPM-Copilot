package com.cspm.mcp;

import com.cspm.mcp.RemediationToolService.ToolResult;
import com.cspm.model.Finding;
import com.cspm.model.IamIdentity;
import com.cspm.model.ScanResult;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.ScanResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockRemediationToolServiceTest {

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private ScanResultRepository scanResultRepository;

    @Mock
    private IamIdentityRepository iamIdentityRepository;

    private MockRemediationToolService service;

    @BeforeEach
    void setUp() {
        service = new MockRemediationToolService(findingRepository, scanResultRepository, iamIdentityRepository);
    }

    // ── Write tool tests ──────────────────────────────────────────────

    @Test
    void blockS3PublicAccess_shouldReturnSuccessWithBeforeAndAfterState() {
        ToolResult result = service.blockS3PublicAccess("my-test-bucket");

        assertTrue(result.success());
        assertNotNull(result.message());
        assertTrue(result.message().contains("my-test-bucket"));
        assertEquals("my-test-bucket", result.data().get("bucketName"));
        assertNotNull(result.data().get("beforeState"), "beforeState should be present");
        assertNotNull(result.data().get("afterState"), "afterState should be present");

        @SuppressWarnings("unchecked")
        Map<String, Object> beforeState = (Map<String, Object>) result.data().get("beforeState");
        assertEquals(false, beforeState.get("blockPublicAcls"));

        @SuppressWarnings("unchecked")
        Map<String, Object> afterState = (Map<String, Object>) result.data().get("afterState");
        assertEquals(true, afterState.get("blockPublicAcls"));
        assertEquals(true, afterState.get("ignorePublicAcls"));
        assertEquals(true, afterState.get("blockPublicPolicy"));
        assertEquals(true, afterState.get("restrictPublicBuckets"));
    }

    @Test
    void restrictSecurityGroup_shouldReturnSuccessWithBeforeAndAfterState() {
        ToolResult result = service.restrictSecurityGroup("sg-0abc1234", 22, "tcp");

        assertTrue(result.success());
        assertNotNull(result.message());
        assertTrue(result.message().contains("sg-0abc1234"));
        assertTrue(result.message().contains("22"));
        assertEquals("sg-0abc1234", result.data().get("groupId"));
        assertNotNull(result.data().get("beforeState"));
        assertNotNull(result.data().get("afterState"));
    }

    @Test
    void enableEbsEncryption_shouldReturnSuccessWithSnapshotInfo() {
        ToolResult result = service.enableEbsEncryption("vol-0abc1234");

        assertTrue(result.success());
        assertTrue(result.message().contains("vol-0abc1234"));
        assertEquals("vol-0abc1234", result.data().get("volumeId"));
        assertNotNull(result.data().get("beforeState"));
        assertNotNull(result.data().get("afterState"));

        @SuppressWarnings("unchecked")
        Map<String, Object> beforeState = (Map<String, Object>) result.data().get("beforeState");
        assertEquals(false, beforeState.get("encrypted"));

        @SuppressWarnings("unchecked")
        Map<String, Object> afterState = (Map<String, Object>) result.data().get("afterState");
        assertEquals(true, afterState.get("encrypted"));
        assertNotNull(afterState.get("snapshotId"), "Encrypted snapshot ID should be generated");
    }

    @Test
    void enableCloudTrail_shouldReturnSuccessWithLoggingState() {
        ToolResult result = service.enableCloudTrail("my-trail");

        assertTrue(result.success());
        assertTrue(result.message().contains("my-trail"));
        assertEquals("my-trail", result.data().get("trailName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> beforeState = (Map<String, Object>) result.data().get("beforeState");
        assertEquals(false, beforeState.get("isLogging"));

        @SuppressWarnings("unchecked")
        Map<String, Object> afterState = (Map<String, Object>) result.data().get("afterState");
        assertEquals(true, afterState.get("isLogging"));
    }

    @Test
    void rotateAccessKey_shouldReturnSuccessWithKeyRotationData() {
        ToolResult result = service.rotateAccessKey("admin-user", "AKIAOLD12345678");

        assertTrue(result.success());
        assertTrue(result.message().contains("admin-user"));
        assertTrue(result.message().contains("AKIAOLD12345678"));
        assertEquals("admin-user", result.data().get("username"));

        @SuppressWarnings("unchecked")
        Map<String, Object> beforeState = (Map<String, Object>) result.data().get("beforeState");
        assertEquals("AKIAOLD12345678", beforeState.get("accessKeyId"));
        assertEquals("Active", beforeState.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> afterState = (Map<String, Object>) result.data().get("afterState");
        assertEquals("Inactive", afterState.get("oldKeyStatus"));
        assertNotNull(afterState.get("newAccessKeyId"), "New access key ID should be generated");
        assertEquals("Active", afterState.get("newKeyStatus"));
    }

    @Test
    void deleteUnusedCredentials_shouldReturnSuccessWithDeletionState() {
        ToolResult result = service.deleteUnusedCredentials("stale-user", "AKIASTALE99999");

        assertTrue(result.success());
        assertTrue(result.message().contains("stale-user"));
        assertTrue(result.message().contains("AKIASTALE99999"));
        assertEquals("stale-user", result.data().get("username"));

        @SuppressWarnings("unchecked")
        Map<String, Object> beforeState = (Map<String, Object>) result.data().get("beforeState");
        assertEquals("AKIASTALE99999", beforeState.get("accessKeyId"));
        assertEquals("Active", beforeState.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> afterState = (Map<String, Object>) result.data().get("afterState");
        assertEquals("Deleted", afterState.get("status"));
    }

    // ── Read-only tool tests ──────────────────────────────────────────

    @Test
    void getScanFindings_withExistingScan_shouldReturnFindings() {
        Finding finding = Finding.builder()
                .id("finding-1")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::my-bucket")
                .severity("HIGH")
                .title("S3 Bucket Public Access")
                .description("Bucket is publicly accessible")
                .remediation("Block public access")
                .category("CONFIG")
                .build();

        ScanResult scanResult = ScanResult.builder()
                .scanId("scan-123")
                .status("COMPLETED")
                .timestamp(Instant.now())
                .totalFindings(1)
                .highSeverity(1)
                .mediumSeverity(0)
                .lowSeverity(0)
                .findings(List.of(finding))
                .build();

        when(scanResultRepository.findByIdWithFindings("scan-123"))
                .thenReturn(Optional.of(scanResult));

        ToolResult result = service.getScanFindings("scan-123");

        assertTrue(result.success());
        assertTrue(result.message().contains("1"));
        assertEquals("scan-123", result.data().get("scanId"));
        assertEquals("COMPLETED", result.data().get("status"));
        assertEquals(1, result.data().get("totalFindings"));
        assertEquals(1, result.data().get("highSeverity"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> findings = (List<Map<String, Object>>) result.data().get("findings");
        assertNotNull(findings);
        assertEquals(1, findings.size());
        assertEquals("finding-1", findings.get(0).get("id"));
        assertEquals("S3", findings.get(0).get("resourceType"));

        verify(scanResultRepository).findByIdWithFindings("scan-123");
    }

    @Test
    void getScanFindings_withNonExistentScan_shouldReturnFailure() {
        when(scanResultRepository.findByIdWithFindings("nonexistent"))
                .thenReturn(Optional.empty());

        ToolResult result = service.getScanFindings("nonexistent");

        assertFalse(result.success());
        assertTrue(result.message().contains("nonexistent"));
        verify(scanResultRepository).findByIdWithFindings("nonexistent");
    }

    @Test
    void getFindingDetails_withExistingFinding_shouldReturnDetails() {
        Finding finding = Finding.builder()
                .id("finding-42")
                .resourceType("EC2")
                .resourceId("sg-0abc1234")
                .severity("CRITICAL")
                .title("Unrestricted SSH Access")
                .description("Security group allows SSH from 0.0.0.0/0")
                .remediation("Restrict SSH to known IPs")
                .category("NETWORK")
                .primaryIdentityArn("arn:aws:iam::123456789:user/admin")
                .build();

        when(findingRepository.findById("finding-42"))
                .thenReturn(Optional.of(finding));

        ToolResult result = service.getFindingDetails("finding-42");

        assertTrue(result.success());
        assertTrue(result.message().contains("finding-42"));
        assertEquals("finding-42", result.data().get("id"));
        assertEquals("EC2", result.data().get("resourceType"));
        assertEquals("sg-0abc1234", result.data().get("resourceId"));
        assertEquals("CRITICAL", result.data().get("severity"));
        assertEquals("Unrestricted SSH Access", result.data().get("title"));
        assertEquals("NETWORK", result.data().get("category"));

        verify(findingRepository).findById("finding-42");
    }

    @Test
    void getFindingDetails_withNonExistentFinding_shouldReturnFailure() {
        when(findingRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

        ToolResult result = service.getFindingDetails("nonexistent");

        assertFalse(result.success());
        assertTrue(result.message().contains("nonexistent"));
        verify(findingRepository).findById("nonexistent");
    }

    @Test
    void getHighRiskIdentities_shouldFilterAndReturnHighRiskEntities() {
        IamIdentity highRiskNoMfa = IamIdentity.builder()
                .id("id-1")
                .name("dev-user")
                .arn("arn:aws:iam::123456789:user/dev-user")
                .identityType(IamIdentity.IdentityType.USER)
                .hasConsoleAccess(true)
                .mfaEnabled(false)
                .lastUsed(Instant.now())
                .policies(List.of())
                .build();

        IamIdentity highRiskConsoleAccess = IamIdentity.builder()
                .id("id-2")
                .name("ops-user")
                .arn("arn:aws:iam::123456789:user/ops-user")
                .identityType(IamIdentity.IdentityType.USER)
                .hasConsoleAccess(true)
                .mfaEnabled(true)
                .lastUsed(Instant.now())
                .policies(List.of())
                .build();

        IamIdentity safeIdentity = IamIdentity.builder()
                .id("id-3")
                .name("safe-role")
                .arn("arn:aws:iam::123456789:role/safe-role")
                .identityType(IamIdentity.IdentityType.ROLE)
                .hasConsoleAccess(false)
                .mfaEnabled(true)
                .lastUsed(Instant.now())
                .policies(List.of())
                .build();

        when(iamIdentityRepository.findAll())
                .thenReturn(List.of(highRiskNoMfa, highRiskConsoleAccess, safeIdentity));

        ToolResult result = service.getHighRiskIdentities();

        assertTrue(result.success());
        assertEquals(3, result.data().get("totalIdentities"));
        assertEquals(2, result.data().get("highRiskCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> identities = (List<Map<String, Object>>) result.data().get("identities");
        assertEquals(2, identities.size());
        assertEquals("dev-user", identities.get(0).get("name"));
        assertEquals(false, identities.get(0).get("mfaEnabled"));
        assertEquals("ops-user", identities.get(1).get("name"));
        assertEquals(true, identities.get(1).get("hasConsoleAccess"));

        verify(iamIdentityRepository).findAll();
    }

    @Test
    void getHighRiskIdentities_withNoIdentities_shouldReturnEmptyList() {
        when(iamIdentityRepository.findAll()).thenReturn(List.of());

        ToolResult result = service.getHighRiskIdentities();

        assertTrue(result.success());
        assertEquals(0, result.data().get("totalIdentities"));
        assertEquals(0, result.data().get("highRiskCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> identities = (List<Map<String, Object>>) result.data().get("identities");
        assertTrue(identities.isEmpty());
    }
}
