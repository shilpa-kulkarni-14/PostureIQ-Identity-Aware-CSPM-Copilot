package com.cspm.controller;

import com.cspm.model.AutoRemediationRequest;
import com.cspm.model.AutoRemediationResponse;
import com.cspm.model.RemediationAudit;
import com.cspm.repository.RemediationAuditRepository;
import com.cspm.service.AgenticRemediationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/remediate")
@RequiredArgsConstructor
@Slf4j
public class RemediationController {

    private final AgenticRemediationService remediationService;
    private final RemediationAuditRepository auditRepository;

    @PostMapping("/auto")
    public ResponseEntity<AutoRemediationResponse> autoRemediate(@RequestBody AutoRemediationRequest request) {
        log.info("Auto-remediation requested for finding: {}", request.getFindingId());
        AutoRemediationResponse response = remediationService.executeRemediation(request);
        return ResponseEntity.ok(response);
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
