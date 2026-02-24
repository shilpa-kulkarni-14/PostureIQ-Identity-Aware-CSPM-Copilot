package com.cspm.service;

import com.cspm.mcp.McpToolRegistry;
import com.cspm.mcp.RemediationToolService.ToolResult;
import com.cspm.model.AutoRemediationRequest;
import com.cspm.model.AutoRemediationResponse;
import com.cspm.model.Finding;
import com.cspm.model.RemediationAudit;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.RemediationAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgenticRemediationServiceTest {

    @Mock
    private AgenticClaudeClient claudeClient;

    @Mock
    private McpToolRegistry toolRegistry;

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private RemediationAuditRepository auditRepository;

    private AgenticRemediationService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new AgenticRemediationService(claudeClient, toolRegistry,
                findingRepository, auditRepository, objectMapper);
        ReflectionTestUtils.setField(service, "maxIterations", 10);
        ReflectionTestUtils.setField(service, "timeoutSeconds", 120);
    }

    // ── Demo mode tests ───────────────────────────────────────────────

    @Test
    void testDemoModeForS3Finding() {
        when(claudeClient.isAvailable()).thenReturn(false);

        Finding s3Finding = Finding.builder()
                .id("f-s3-001")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::my-public-bucket")
                .severity("HIGH")
                .title("S3 Bucket Public Access Enabled")
                .description("Bucket allows public access")
                .remediation("Block all public access")
                .category("CONFIG")
                .build();

        when(findingRepository.findById("f-s3-001")).thenReturn(Optional.of(s3Finding));
        when(toolRegistry.dispatch(eq("block_s3_public_access"), anyMap()))
                .thenReturn(ToolResult.success("Blocked public access", Map.of(
                        "beforeState", Map.of("blockPublicAcls", false),
                        "afterState", Map.of("blockPublicAcls", true)
                )));

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-s3-001")
                .scanId("scan-1")
                .dryRun(false)
                .build();

        AutoRemediationResponse response = service.executeRemediation(request);

        assertEquals("f-s3-001", response.getFindingId());
        assertNotNull(response.getSessionId());
        assertEquals("COMPLETED", response.getStatus());
        assertTrue(response.isDemoMode());
        assertFalse(response.getActions().isEmpty(), "Actions list should not be empty");
        assertEquals("block_s3_public_access", response.getActions().get(0).getToolName());
        assertEquals("SUCCESS", response.getActions().get(0).getStatus());
        assertTrue(response.getTotalDurationMs() >= 0);

        verify(auditRepository, atLeastOnce()).save(any(RemediationAudit.class));
    }

    @Test
    void testDemoModeForEC2Finding() {
        when(claudeClient.isAvailable()).thenReturn(false);

        Finding ec2Finding = Finding.builder()
                .id("f-ec2-001")
                .resourceType("EC2")
                .resourceId("sg-0abc1234")
                .severity("CRITICAL")
                .title("Unrestricted SSH Access")
                .description("Security group allows SSH from 0.0.0.0/0")
                .remediation("Restrict inbound SSH")
                .category("NETWORK")
                .build();

        when(findingRepository.findById("f-ec2-001")).thenReturn(Optional.of(ec2Finding));
        when(toolRegistry.dispatch(eq("restrict_security_group"), anyMap()))
                .thenReturn(ToolResult.success("Restricted security group", Map.of(
                        "beforeState", Map.of("cidr", "0.0.0.0/0"),
                        "afterState", Map.of("ingressRules", List.of())
                )));

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-ec2-001")
                .scanId("scan-2")
                .dryRun(false)
                .build();

        AutoRemediationResponse response = service.executeRemediation(request);

        assertEquals("f-ec2-001", response.getFindingId());
        assertEquals("COMPLETED", response.getStatus());
        assertTrue(response.isDemoMode());
        assertFalse(response.getActions().isEmpty());
        assertEquals("restrict_security_group", response.getActions().get(0).getToolName());
        assertEquals("SUCCESS", response.getActions().get(0).getStatus());
    }

    @Test
    void testFindingNotFound() {
        when(findingRepository.findById("nonexistent")).thenReturn(Optional.empty());

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("nonexistent")
                .scanId("scan-x")
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.executeRemediation(request));

        verify(findingRepository).findById("nonexistent");
        verifyNoInteractions(claudeClient);
    }

    // ── Agentic loop tests ────────────────────────────────────────────

    @Test
    void testAgenticLoopWithToolUse() {
        when(claudeClient.isAvailable()).thenReturn(true);

        Finding finding = Finding.builder()
                .id("f-agentic-001")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::leaky-bucket")
                .severity("HIGH")
                .title("S3 Bucket Public Access")
                .description("Bucket is publicly accessible")
                .remediation("Block public access")
                .category("CONFIG")
                .build();

        when(findingRepository.findById("f-agentic-001")).thenReturn(Optional.of(finding));
        when(toolRegistry.getToolDefinitions())
                .thenReturn(List.of(Map.of("name", "block_s3_public_access")));

        // First Claude call returns a tool_use response
        Map<String, Object> toolUseResponse = new LinkedHashMap<>();
        toolUseResponse.put("stop_reason", "tool_use");
        List<Map<String, Object>> contentBlocks = List.of(
                Map.of(
                        "type", "tool_use",
                        "id", "toolu_001",
                        "name", "block_s3_public_access",
                        "input", Map.of("bucketName", "leaky-bucket")
                )
        );
        toolUseResponse.put("content", contentBlocks);

        // Second Claude call returns a text response (end_turn)
        Map<String, Object> textResponse = new LinkedHashMap<>();
        textResponse.put("stop_reason", "end_turn");
        textResponse.put("content", List.of(
                Map.of("type", "text", "text", "Successfully remediated S3 bucket.")
        ));

        when(claudeClient.sendWithTools(anyList(), anyList(), anyString()))
                .thenReturn(toolUseResponse)
                .thenReturn(textResponse);

        when(claudeClient.hasToolUse(toolUseResponse)).thenReturn(true);
        when(claudeClient.hasToolUse(textResponse)).thenReturn(false);
        when(claudeClient.extractContentBlocks(toolUseResponse)).thenReturn(contentBlocks);
        when(claudeClient.extractText(textResponse)).thenReturn("Successfully remediated S3 bucket.");

        when(toolRegistry.dispatch(eq("block_s3_public_access"), anyMap()))
                .thenReturn(ToolResult.success("Mock success", Map.of(
                        "beforeState", Map.of("blockPublicAcls", false),
                        "afterState", Map.of("blockPublicAcls", true)
                )));

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-agentic-001")
                .scanId("scan-a1")
                .dryRun(false)
                .build();

        AutoRemediationResponse response = service.executeRemediation(request);

        assertEquals("f-agentic-001", response.getFindingId());
        assertNotNull(response.getSessionId());
        assertEquals("COMPLETED", response.getStatus());
        assertFalse(response.isDemoMode());
        assertEquals(1, response.getActions().size());
        assertEquals("block_s3_public_access", response.getActions().get(0).getToolName());
        assertEquals("SUCCESS", response.getActions().get(0).getStatus());
        assertEquals("Successfully remediated S3 bucket.", response.getSummary());

        verify(claudeClient, times(2)).sendWithTools(anyList(), anyList(), anyString());
        verify(toolRegistry).dispatch(eq("block_s3_public_access"), anyMap());
        verify(auditRepository, atLeastOnce()).save(any(RemediationAudit.class));
    }

    @Test
    void testAgenticLoopTextOnly() {
        when(claudeClient.isAvailable()).thenReturn(true);

        Finding finding = Finding.builder()
                .id("f-text-001")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::some-bucket")
                .severity("LOW")
                .title("Minor config issue")
                .description("Non-critical finding")
                .remediation("Review bucket settings")
                .category("CONFIG")
                .build();

        when(findingRepository.findById("f-text-001")).thenReturn(Optional.of(finding));
        when(toolRegistry.getToolDefinitions())
                .thenReturn(List.of(Map.of("name", "block_s3_public_access")));

        // Claude returns text directly without any tool use
        Map<String, Object> textResponse = new LinkedHashMap<>();
        textResponse.put("stop_reason", "end_turn");
        textResponse.put("content", List.of(
                Map.of("type", "text", "text", "This finding is low severity. No automated remediation needed.")
        ));

        when(claudeClient.sendWithTools(anyList(), anyList(), anyString()))
                .thenReturn(textResponse);
        when(claudeClient.hasToolUse(textResponse)).thenReturn(false);
        when(claudeClient.extractText(textResponse))
                .thenReturn("This finding is low severity. No automated remediation needed.");

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-text-001")
                .scanId("scan-t1")
                .dryRun(false)
                .build();

        AutoRemediationResponse response = service.executeRemediation(request);

        assertEquals("f-text-001", response.getFindingId());
        assertEquals("COMPLETED", response.getStatus());
        assertFalse(response.isDemoMode());
        assertEquals(0, response.getActions().size(), "No actions should be taken for text-only response");
        assertEquals("This finding is low severity. No automated remediation needed.", response.getSummary());

        verify(claudeClient, times(1)).sendWithTools(anyList(), anyList(), anyString());
        verify(toolRegistry, never()).dispatch(anyString(), anyMap());
    }

    @Test
    void testDemoModeDryRun() {
        when(claudeClient.isAvailable()).thenReturn(false);

        Finding finding = Finding.builder()
                .id("f-dry-001")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::dry-run-bucket")
                .severity("HIGH")
                .title("S3 Public Access")
                .description("Bucket publicly accessible")
                .remediation("Block public access")
                .category("CONFIG")
                .build();

        when(findingRepository.findById("f-dry-001")).thenReturn(Optional.of(finding));

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-dry-001")
                .scanId("scan-dry")
                .dryRun(true)
                .build();

        AutoRemediationResponse response = service.executeRemediation(request);

        assertEquals("COMPLETED", response.getStatus());
        assertTrue(response.isDemoMode());
        assertFalse(response.getActions().isEmpty());
        // In dry-run mode the service dispatches with a DRY RUN wrapper, so the tool
        // should not be called directly -- verify dispatch was NOT called because
        // the service short-circuits with its own DRY RUN ToolResult
        // Actually, looking at the code, dryRun creates its own ToolResult without calling dispatch
        // So dispatch should NOT be called in dry-run mode
        verify(toolRegistry, never()).dispatch(anyString(), anyMap());
    }
}
