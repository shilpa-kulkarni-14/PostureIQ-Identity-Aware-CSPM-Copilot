package com.cspm.controller;

import com.cspm.model.ApprovalRequest;
import com.cspm.model.AutoRemediationRequest;
import com.cspm.model.AutoRemediationResponse;
import com.cspm.model.RemediationAudit;
import com.cspm.repository.RemediationAuditRepository;
import com.cspm.service.AgenticRemediationService;
import com.cspm.service.RemediationProgressEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/remediate")
@RequiredArgsConstructor
@Slf4j
public class RemediationController {

    private final AgenticRemediationService remediationService;
    private final RemediationAuditRepository auditRepository;
    private final RemediationProgressEmitter progressEmitter;

    @PostMapping("/auto")
    public ResponseEntity<AutoRemediationResponse> autoRemediate(@RequestBody AutoRemediationRequest request) {
        log.info("Auto-remediation requested for finding: {}", request.getFindingId());
        AutoRemediationResponse response = remediationService.executeRemediation(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String sessionId) {
        log.info("SSE stream requested for session: {}", sessionId);
        return progressEmitter.createEmitter(sessionId);
    }

    @PostMapping("/approve")
    public ResponseEntity<AutoRemediationResponse> approveRemediation(@RequestBody ApprovalRequest approval) {
        log.info("Remediation approval received for finding: {}, tool: {}", approval.getFindingId(), approval.getToolName());
        return ResponseEntity.ok(remediationService.executeApprovedRemediation(approval));
    }

    @GetMapping("/audit/{findingId}")
    public ResponseEntity<List<RemediationAudit>> getAuditTrail(@PathVariable String findingId) {
        return ResponseEntity.ok(auditRepository.findByFindingIdOrderByExecutedAtDesc(findingId));
    }

    @GetMapping("/audit/session/{sessionId}")
    public ResponseEntity<List<RemediationAudit>> getSessionAudit(@PathVariable String sessionId) {
        return ResponseEntity.ok(auditRepository.findByClaudeSessionIdOrderByExecutedAtAsc(sessionId));
    }
}
