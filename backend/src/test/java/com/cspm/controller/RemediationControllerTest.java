package com.cspm.controller;

import com.cspm.model.AutoRemediationRequest;
import com.cspm.model.AutoRemediationResponse;
import com.cspm.model.AutoRemediationResponse.RemediationAction;
import com.cspm.model.RemediationAudit;
import com.cspm.repository.RemediationAuditRepository;
import com.cspm.security.JwtAuthenticationFilter;
import com.cspm.service.AgenticRemediationService;
import com.cspm.service.JwtService;
import com.cspm.service.RemediationProgressEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RemediationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RemediationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgenticRemediationService remediationService;

    @MockBean
    private RemediationAuditRepository auditRepository;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private RemediationProgressEmitter progressEmitter;

    // ── POST /api/remediate/auto ──────────────────────────────────────

    @Test
    void testAutoRemediate_shouldReturnOkWithResponse() throws Exception {
        RemediationAction action = RemediationAction.builder()
                .toolName("block_s3_public_access")
                .input("{\"bucketName\":\"my-bucket\"}")
                .output("{\"success\":true,\"message\":\"Blocked\"}")
                .status("SUCCESS")
                .beforeState("{\"blockPublicAcls\":false}")
                .afterState("{\"blockPublicAcls\":true}")
                .durationMs(350)
                .build();

        AutoRemediationResponse mockResponse = AutoRemediationResponse.builder()
                .findingId("f-001")
                .sessionId("sess-abc123")
                .status("COMPLETED")
                .actions(List.of(action))
                .summary("Successfully blocked public access on bucket my-bucket.")
                .totalDurationMs(1200)
                .demoMode(true)
                .build();

        when(remediationService.executeRemediation(any(AutoRemediationRequest.class)))
                .thenReturn(mockResponse);

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-001")
                .scanId("scan-1")
                .dryRun(false)
                .build();

        mockMvc.perform(post("/api/remediate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value("f-001"))
                .andExpect(jsonPath("$.sessionId").value("sess-abc123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.totalDurationMs").isNumber())
                .andExpect(jsonPath("$.actions", hasSize(1)))
                .andExpect(jsonPath("$.actions[0].toolName").value("block_s3_public_access"))
                .andExpect(jsonPath("$.actions[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.summary").value(containsString("my-bucket")));

        verify(remediationService).executeRemediation(any(AutoRemediationRequest.class));
    }

    @Test
    void testAutoRemediate_withDryRun_shouldReturnOk() throws Exception {
        AutoRemediationResponse mockResponse = AutoRemediationResponse.builder()
                .findingId("f-002")
                .sessionId("sess-dry")
                .status("COMPLETED")
                .actions(List.of())
                .summary("[DRY RUN] Would remediate finding f-002.")
                .totalDurationMs(100)
                .demoMode(true)
                .build();

        when(remediationService.executeRemediation(any(AutoRemediationRequest.class)))
                .thenReturn(mockResponse);

        AutoRemediationRequest request = AutoRemediationRequest.builder()
                .findingId("f-002")
                .scanId("scan-2")
                .dryRun(true)
                .build();

        mockMvc.perform(post("/api/remediate/auto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findingId").value("f-002"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ── GET /api/remediate/audit/{findingId} ──────────────────────────

    @Test
    void testGetAuditTrail_shouldReturnAuditList() throws Exception {
        RemediationAudit audit1 = RemediationAudit.builder()
                .id(1L)
                .findingId("f-001")
                .scanId("scan-1")
                .toolName("block_s3_public_access")
                .toolInput("{\"bucketName\":\"my-bucket\"}")
                .toolOutput("{\"success\":true}")
                .status("SUCCESS")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::my-bucket")
                .initiatedBy("DEMO_MODE")
                .claudeSessionId("sess-abc123")
                .executedAt(LocalDateTime.of(2026, 2, 23, 10, 30, 0))
                .isMock(true)
                .build();

        RemediationAudit audit2 = RemediationAudit.builder()
                .id(2L)
                .findingId("f-001")
                .scanId("scan-1")
                .toolName("get_finding_details")
                .toolInput("{\"findingId\":\"f-001\"}")
                .toolOutput("{\"success\":true}")
                .status("SUCCESS")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::my-bucket")
                .initiatedBy("AGENTIC_CLAUDE")
                .claudeSessionId("sess-abc456")
                .executedAt(LocalDateTime.of(2026, 2, 23, 10, 25, 0))
                .isMock(false)
                .build();

        when(auditRepository.findByFindingIdOrderByExecutedAtDesc("f-001"))
                .thenReturn(List.of(audit1, audit2));

        mockMvc.perform(get("/api/remediate/audit/f-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].findingId").value("f-001"))
                .andExpect(jsonPath("$[0].toolName").value("block_s3_public_access"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].toolName").value("get_finding_details"));

        verify(auditRepository).findByFindingIdOrderByExecutedAtDesc("f-001");
    }

    @Test
    void testGetAuditTrail_withNoAudits_shouldReturnEmptyList() throws Exception {
        when(auditRepository.findByFindingIdOrderByExecutedAtDesc("f-empty"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/remediate/audit/f-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/remediate/audit/session/{sessionId} ──────────────────

    @Test
    void testGetSessionAudit_shouldReturnAuditListBySession() throws Exception {
        RemediationAudit audit = RemediationAudit.builder()
                .id(10L)
                .findingId("f-001")
                .scanId("scan-1")
                .toolName("block_s3_public_access")
                .status("SUCCESS")
                .resourceType("S3")
                .resourceId("arn:aws:s3:::my-bucket")
                .initiatedBy("AGENTIC_CLAUDE")
                .claudeSessionId("sess-xyz789")
                .executedAt(LocalDateTime.of(2026, 2, 23, 11, 0, 0))
                .isMock(false)
                .build();

        when(auditRepository.findByClaudeSessionIdOrderByExecutedAtAsc("sess-xyz789"))
                .thenReturn(List.of(audit));

        mockMvc.perform(get("/api/remediate/audit/session/sess-xyz789"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].claudeSessionId").value("sess-xyz789"))
                .andExpect(jsonPath("$[0].toolName").value("block_s3_public_access"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));

        verify(auditRepository).findByClaudeSessionIdOrderByExecutedAtAsc("sess-xyz789");
    }

    @Test
    void testGetSessionAudit_withUnknownSession_shouldReturnEmptyList() throws Exception {
        when(auditRepository.findByClaudeSessionIdOrderByExecutedAtAsc("unknown-session"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/remediate/audit/session/unknown-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
