package com.cspm.service;

import com.cspm.mcp.McpToolRegistry;
import com.cspm.mcp.RemediationToolService.ToolResult;
import com.cspm.model.*;
import com.cspm.model.AutoRemediationResponse.RemediationAction;
import com.cspm.model.RemediationProgressEvent.EventType;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.RemediationAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgenticRemediationService {

    private final AgenticClaudeClient claudeClient;
    private final McpToolRegistry mcpToolRegistry;
    private final FindingRepository findingRepository;
    private final RemediationAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final RemediationProgressEmitter progressEmitter;

    @Value("${mcp.remediation.max-iterations:10}")
    private int maxIterations;

    @Value("${mcp.remediation.timeout-seconds:120}")
    private int timeoutSeconds;

    private static final String SYSTEM_PROMPT = """
            You are a cloud security remediation agent for PostureIQ. Given a security finding, \
            use the available tools to fix it. Analyze the finding, determine the appropriate \
            remediation action, call the necessary tools to remediate the issue, and then \
            provide a concise summary of what was done and the result. \
            Always verify the finding details before taking action. \
            If a tool call fails, explain why and suggest manual steps.""";

    // In-memory store for pending approval sessions (sessionId -> finding + tool calls)
    private final Map<String, PendingApprovalSession> pendingApprovals = new java.util.concurrent.ConcurrentHashMap<>();

    private record PendingApprovalSession(Finding finding, AutoRemediationRequest request,
                                           List<SyntheticToolCall> toolCalls, long startTime) {}

    /**
     * Execute remediation for the given request. Uses the Claude agentic loop when
     * an API key is configured; falls back to a synthetic demo-mode sequence otherwise.
     */
    public AutoRemediationResponse executeRemediation(AutoRemediationRequest request) {
        long startTime = System.currentTimeMillis();
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        Finding finding = findingRepository.findById(request.getFindingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Finding not found: " + request.getFindingId()));

        if (claudeClient.isAvailable()) {
            return executeAgenticLoop(finding, request, sessionId, startTime);
        } else {
            return executeDemoMode(finding, request, sessionId, startTime);
        }
    }

    // ── Claude agentic loop ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private AutoRemediationResponse executeAgenticLoop(Finding finding,
                                                        AutoRemediationRequest request,
                                                        String sessionId,
                                                        long startTime) {
        List<RemediationAction> actions = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        int stepNumber = 0;

        // Emit STARTED event
        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                .type(EventType.STARTED)
                .findingId(finding.getId())
                .sessionId(sessionId)
                .message("Starting agentic remediation for " + finding.getTitle())
                .status("IN_PROGRESS")
                .stepNumber(0)
                .totalSteps(-1)
                .elapsedMs(System.currentTimeMillis() - startTime)
                .demoMode(false)
                .build());

        // Initial user message with finding context
        String userContent = buildFindingPrompt(finding, request);
        messages.add(Map.of("role", "user", "content", userContent));

        List<Map<String, Object>> tools = mcpToolRegistry.getToolDefinitions();
        String summary = "";
        String status = "COMPLETED";

        try {
            long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

            for (int iteration = 0; iteration < maxIterations; iteration++) {
                if (System.currentTimeMillis() > deadline) {
                    log.warn("Agentic loop timed out after {}s for finding {}",
                            timeoutSeconds, finding.getId());
                    status = "PARTIAL";
                    summary = "Remediation timed out after " + timeoutSeconds + " seconds. " +
                              "Completed " + actions.size() + " action(s) before timeout.";
                    break;
                }

                // Emit THINKING event before Claude processes
                progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                        .type(EventType.THINKING)
                        .findingId(finding.getId())
                        .sessionId(sessionId)
                        .message("Claude is analyzing and planning next action...")
                        .status("IN_PROGRESS")
                        .stepNumber(stepNumber)
                        .totalSteps(-1)
                        .elapsedMs(System.currentTimeMillis() - startTime)
                        .demoMode(false)
                        .build());

                Map<String, Object> response = claudeClient.sendWithTools(messages, tools, SYSTEM_PROMPT);

                if (claudeClient.hasToolUse(response)) {
                    // Claude wants to call tools — extract tool_use blocks
                    List<Map<String, Object>> contentBlocks = claudeClient.extractContentBlocks(response);

                    // Add assistant response to conversation history
                    messages.add(Map.of("role", "assistant", "content", contentBlocks));

                    // Process each tool_use block and build tool_result user message
                    List<Map<String, Object>> toolResults = new ArrayList<>();
                    for (Map<String, Object> block : contentBlocks) {
                        if (!"tool_use".equals(block.get("type"))) {
                            continue;
                        }

                        String toolUseId = (String) block.get("id");
                        String toolName = (String) block.get("name");
                        Map<String, Object> toolInput = block.get("input") instanceof Map
                                ? (Map<String, Object>) block.get("input")
                                : Map.of();

                        stepNumber++;

                        log.info("Agentic loop iteration {}: calling tool '{}' with input {}",
                                iteration, toolName, toolInput);

                        // Emit TOOL_EXECUTING event
                        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                                .type(EventType.TOOL_EXECUTING)
                                .findingId(finding.getId())
                                .sessionId(sessionId)
                                .toolName(toolName)
                                .message("Executing " + toolName)
                                .status("IN_PROGRESS")
                                .stepNumber(stepNumber)
                                .totalSteps(-1)
                                .elapsedMs(System.currentTimeMillis() - startTime)
                                .demoMode(false)
                                .build());

                        // Execute tool via MCP registry
                        long toolStart = System.currentTimeMillis();
                        ToolResult toolResult;
                        try {
                            if (request.isDryRun()) {
                                toolResult = ToolResult.success(
                                        "[DRY RUN] Would execute " + toolName + " with " + toolInput,
                                        Map.of("dryRun", true));
                            } else {
                                toolResult = mcpToolRegistry.dispatch(toolName, toolInput);
                            }
                        } catch (Exception e) {
                            log.error("Tool execution failed: {} - {}", toolName, e.getMessage(), e);
                            toolResult = ToolResult.failure("Tool execution error: " + e.getMessage());
                        }
                        long toolDuration = System.currentTimeMillis() - toolStart;

                        // Build remediation action record
                        String toolOutputJson = serializeJson(Map.of(
                                "success", toolResult.success(),
                                "message", toolResult.message(),
                                "data", toolResult.data()));

                        String beforeState = toolResult.data().containsKey("beforeState")
                                ? serializeJson(toolResult.data().get("beforeState")) : null;
                        String afterState = toolResult.data().containsKey("afterState")
                                ? serializeJson(toolResult.data().get("afterState")) : null;

                        RemediationAction action = RemediationAction.builder()
                                .toolName(toolName)
                                .input(serializeJson(toolInput))
                                .output(toolOutputJson)
                                .status(toolResult.success() ? "SUCCESS" : "FAILED")
                                .beforeState(beforeState)
                                .afterState(afterState)
                                .durationMs(toolDuration)
                                .build();
                        actions.add(action);

                        // Emit TOOL_COMPLETED event
                        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                                .type(EventType.TOOL_COMPLETED)
                                .findingId(finding.getId())
                                .sessionId(sessionId)
                                .toolName(toolName)
                                .message(toolResult.success()
                                        ? toolName + " completed successfully"
                                        : toolName + " failed: " + toolResult.message())
                                .status(toolResult.success() ? "SUCCESS" : "FAILED")
                                .beforeState(beforeState)
                                .afterState(afterState)
                                .stepNumber(stepNumber)
                                .totalSteps(-1)
                                .elapsedMs(System.currentTimeMillis() - startTime)
                                .demoMode(false)
                                .build());

                        // Save audit record
                        saveAudit(finding, request, sessionId, toolName, toolInput,
                                toolResult, toolDuration, false);

                        // Build tool_result content block for Claude
                        Map<String, Object> resultBlock = new LinkedHashMap<>();
                        resultBlock.put("type", "tool_result");
                        resultBlock.put("tool_use_id", toolUseId);
                        resultBlock.put("content", toolResult.message());
                        toolResults.add(resultBlock);
                    }

                    // Send tool results back as a user message
                    messages.add(Map.of("role", "user", "content", toolResults));

                } else {
                    // Claude returned end_turn or text — extract final summary
                    summary = claudeClient.extractText(response);
                    break;
                }
            }

            // If we exited the loop without a summary (hit maxIterations)
            if (summary.isEmpty() && "COMPLETED".equals(status)) {
                status = "PARTIAL";
                summary = "Reached maximum iterations (" + maxIterations +
                          "). Completed " + actions.size() + " action(s).";
            }

            // Check if any action failed
            boolean anyFailed = actions.stream().anyMatch(a -> "FAILED".equals(a.getStatus()));
            if (anyFailed && "COMPLETED".equals(status)) {
                status = "PARTIAL";
            }

        } catch (Exception e) {
            log.error("Agentic remediation failed for finding {}: {}", finding.getId(), e.getMessage(), e);
            status = "FAILED";
            summary = "Remediation failed: " + e.getMessage();

            // Emit ERROR event
            progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                    .type(EventType.ERROR)
                    .findingId(finding.getId())
                    .sessionId(sessionId)
                    .message("Remediation failed: " + e.getMessage())
                    .status("FAILED")
                    .stepNumber(stepNumber)
                    .totalSteps(stepNumber)
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .demoMode(false)
                    .build());
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        // Persist remediation status on the finding
        finding.setRemediationStatus(status);
        findingRepository.save(finding);

        // Emit COMPLETED event
        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                .type("FAILED".equals(status) ? EventType.ERROR : EventType.COMPLETED)
                .findingId(finding.getId())
                .sessionId(sessionId)
                .message(summary)
                .status(status)
                .stepNumber(stepNumber)
                .totalSteps(stepNumber)
                .elapsedMs(totalDuration)
                .demoMode(false)
                .build());
        progressEmitter.complete(sessionId);

        return AutoRemediationResponse.builder()
                .findingId(finding.getId())
                .sessionId(sessionId)
                .status(status)
                .actions(actions)
                .summary(summary)
                .totalDurationMs(totalDuration)
                .demoMode(false)
                .build();
    }

    // ── Demo / synthetic mode ────────────────────────────────────────────

    private AutoRemediationResponse executeDemoMode(Finding finding,
                                                     AutoRemediationRequest request,
                                                     String sessionId,
                                                     long startTime) {
        log.info("No Anthropic API key configured — running demo-mode remediation for finding {}",
                finding.getId());

        List<RemediationAction> actions = new ArrayList<>();
        String status = "COMPLETED";
        StringBuilder summaryBuilder = new StringBuilder();

        try {
            List<SyntheticToolCall> toolCalls = buildSyntheticToolCalls(finding);
            int totalSteps = toolCalls.size();

            // If approval is required, return the plan without executing
            if (request.isRequireApproval()) {
                List<ApprovalRequest> approvalRequests = new ArrayList<>();
                for (SyntheticToolCall call : toolCalls) {
                    approvalRequests.add(ApprovalRequest.builder()
                            .findingId(finding.getId())
                            .sessionId(sessionId)
                            .toolName(call.toolName())
                            .toolInput(serializeJson(call.input()))
                            .resourceType(finding.getResourceType())
                            .resourceId(finding.getResourceId())
                            .description(buildToolDescription(call.toolName(), call.input(), finding))
                            .approved(false)
                            .build());
                }

                // Store the session for later execution upon approval
                pendingApprovals.put(sessionId,
                        new PendingApprovalSession(finding, request, toolCalls, startTime));

                return AutoRemediationResponse.builder()
                        .findingId(finding.getId())
                        .sessionId(sessionId)
                        .status("PENDING_APPROVAL")
                        .actions(List.of())
                        .summary("Remediation plan generated. Awaiting approval before execution.")
                        .totalDurationMs(System.currentTimeMillis() - startTime)
                        .demoMode(true)
                        .pendingApproval(true)
                        .approvalRequests(approvalRequests)
                        .build();
            }

            // Emit STARTED event
            progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                    .type(EventType.STARTED)
                    .findingId(finding.getId())
                    .sessionId(sessionId)
                    .message("Starting demo-mode remediation for " + finding.getTitle())
                    .status("IN_PROGRESS")
                    .stepNumber(0)
                    .totalSteps(totalSteps)
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .demoMode(true)
                    .build());

            int stepNumber = 0;
            for (SyntheticToolCall call : toolCalls) {
                stepNumber++;

                // Emit TOOL_EXECUTING event
                progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                        .type(EventType.TOOL_EXECUTING)
                        .findingId(finding.getId())
                        .sessionId(sessionId)
                        .toolName(call.toolName)
                        .message("Executing " + call.toolName)
                        .status("IN_PROGRESS")
                        .stepNumber(stepNumber)
                        .totalSteps(totalSteps)
                        .elapsedMs(System.currentTimeMillis() - startTime)
                        .demoMode(true)
                        .build());

                long toolStart = System.currentTimeMillis();
                ToolResult toolResult;
                try {
                    if (request.isDryRun()) {
                        toolResult = ToolResult.success(
                                "[DRY RUN] Would execute " + call.toolName + " with " + call.input,
                                Map.of("dryRun", true));
                    } else {
                        toolResult = mcpToolRegistry.dispatch(call.toolName, call.input);
                    }
                } catch (Exception e) {
                    log.error("Demo-mode tool execution failed: {} - {}",
                            call.toolName, e.getMessage(), e);
                    toolResult = ToolResult.failure("Tool execution error: " + e.getMessage());
                }
                long toolDuration = System.currentTimeMillis() - toolStart;

                String toolOutputJson = serializeJson(Map.of(
                        "success", toolResult.success(),
                        "message", toolResult.message(),
                        "data", toolResult.data()));

                String beforeState = toolResult.data().containsKey("beforeState")
                        ? serializeJson(toolResult.data().get("beforeState")) : null;
                String afterState = toolResult.data().containsKey("afterState")
                        ? serializeJson(toolResult.data().get("afterState")) : null;

                RemediationAction action = RemediationAction.builder()
                        .toolName(call.toolName)
                        .input(serializeJson(call.input))
                        .output(toolOutputJson)
                        .status(toolResult.success() ? "SUCCESS" : "FAILED")
                        .beforeState(beforeState)
                        .afterState(afterState)
                        .durationMs(toolDuration)
                        .build();
                actions.add(action);

                // Emit TOOL_COMPLETED event
                progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                        .type(EventType.TOOL_COMPLETED)
                        .findingId(finding.getId())
                        .sessionId(sessionId)
                        .toolName(call.toolName)
                        .message(toolResult.success()
                                ? call.toolName + " completed successfully"
                                : call.toolName + " failed: " + toolResult.message())
                        .status(toolResult.success() ? "SUCCESS" : "FAILED")
                        .beforeState(beforeState)
                        .afterState(afterState)
                        .stepNumber(stepNumber)
                        .totalSteps(totalSteps)
                        .elapsedMs(System.currentTimeMillis() - startTime)
                        .demoMode(true)
                        .build());

                saveAudit(finding, request, sessionId, call.toolName, call.input,
                        toolResult, toolDuration, true);

                if (!toolResult.success()) {
                    status = "PARTIAL";
                }
            }

            summaryBuilder.append(buildDemoSummary(finding, actions));

        } catch (Exception e) {
            log.error("Demo-mode remediation failed for finding {}: {}", finding.getId(), e.getMessage(), e);
            status = "FAILED";
            summaryBuilder.append("Demo-mode remediation failed: ").append(e.getMessage());

            // Emit ERROR event
            progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                    .type(EventType.ERROR)
                    .findingId(finding.getId())
                    .sessionId(sessionId)
                    .message("Demo-mode remediation failed: " + e.getMessage())
                    .status("FAILED")
                    .stepNumber(0)
                    .totalSteps(0)
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .demoMode(true)
                    .build());
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        // Persist remediation status on the finding
        finding.setRemediationStatus(status);
        findingRepository.save(finding);

        // Emit COMPLETED event
        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                .type("FAILED".equals(status) ? EventType.ERROR : EventType.COMPLETED)
                .findingId(finding.getId())
                .sessionId(sessionId)
                .message(summaryBuilder.toString())
                .status(status)
                .stepNumber(actions.size())
                .totalSteps(actions.size())
                .elapsedMs(totalDuration)
                .demoMode(true)
                .build());
        progressEmitter.complete(sessionId);

        return AutoRemediationResponse.builder()
                .findingId(finding.getId())
                .sessionId(sessionId)
                .status(status)
                .actions(actions)
                .summary(summaryBuilder.toString())
                .totalDurationMs(totalDuration)
                .demoMode(true)
                .build();
    }

    // ── Approval workflow ─────────────────────────────────────────────────

    /**
     * Execute a previously approved remediation. Looks up the pending session
     * and runs the approved tool call.
     */
    public AutoRemediationResponse executeApprovedRemediation(ApprovalRequest approval) {
        long startTime = System.currentTimeMillis();
        String sessionId = approval.getSessionId();

        PendingApprovalSession session = pendingApprovals.remove(sessionId);

        // If no pending session found, look up the finding and execute the single tool directly
        Finding finding;
        if (session != null) {
            finding = session.finding();
        } else {
            finding = findingRepository.findById(approval.getFindingId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Finding not found: " + approval.getFindingId()));
        }

        List<RemediationAction> actions = new ArrayList<>();
        String status = "COMPLETED";
        StringBuilder summaryBuilder = new StringBuilder();

        List<SyntheticToolCall> toolCalls = session != null
                ? session.toolCalls()
                : buildSyntheticToolCalls(finding);

        int totalSteps = toolCalls.size();

        // Emit STARTED event
        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                .type(EventType.STARTED)
                .findingId(finding.getId())
                .sessionId(sessionId)
                .message("Executing approved remediation for " + finding.getTitle())
                .status("IN_PROGRESS")
                .stepNumber(0)
                .totalSteps(totalSteps)
                .elapsedMs(System.currentTimeMillis() - startTime)
                .demoMode(true)
                .build());

        int stepNumber = 0;
        for (SyntheticToolCall call : toolCalls) {
            stepNumber++;

            // Emit TOOL_EXECUTING event
            progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                    .type(EventType.TOOL_EXECUTING)
                    .findingId(finding.getId())
                    .sessionId(sessionId)
                    .toolName(call.toolName())
                    .message("Executing approved action: " + call.toolName())
                    .status("IN_PROGRESS")
                    .stepNumber(stepNumber)
                    .totalSteps(totalSteps)
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .demoMode(true)
                    .build());

            long toolStart = System.currentTimeMillis();
            ToolResult toolResult;
            try {
                toolResult = mcpToolRegistry.dispatch(call.toolName(), call.input());
            } catch (Exception e) {
                log.error("Approved tool execution failed: {} - {}",
                        call.toolName(), e.getMessage(), e);
                toolResult = ToolResult.failure("Tool execution error: " + e.getMessage());
            }
            long toolDuration = System.currentTimeMillis() - toolStart;

            String toolOutputJson = serializeJson(Map.of(
                    "success", toolResult.success(),
                    "message", toolResult.message(),
                    "data", toolResult.data()));

            String beforeState = toolResult.data().containsKey("beforeState")
                    ? serializeJson(toolResult.data().get("beforeState")) : null;
            String afterState = toolResult.data().containsKey("afterState")
                    ? serializeJson(toolResult.data().get("afterState")) : null;

            RemediationAction action = RemediationAction.builder()
                    .toolName(call.toolName())
                    .input(serializeJson(call.input()))
                    .output(toolOutputJson)
                    .status(toolResult.success() ? "SUCCESS" : "FAILED")
                    .beforeState(beforeState)
                    .afterState(afterState)
                    .durationMs(toolDuration)
                    .build();
            actions.add(action);

            // Emit TOOL_COMPLETED event
            progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                    .type(EventType.TOOL_COMPLETED)
                    .findingId(finding.getId())
                    .sessionId(sessionId)
                    .toolName(call.toolName())
                    .message(toolResult.success()
                            ? call.toolName() + " completed successfully"
                            : call.toolName() + " failed: " + toolResult.message())
                    .status(toolResult.success() ? "SUCCESS" : "FAILED")
                    .beforeState(beforeState)
                    .afterState(afterState)
                    .stepNumber(stepNumber)
                    .totalSteps(totalSteps)
                    .elapsedMs(System.currentTimeMillis() - startTime)
                    .demoMode(true)
                    .build());

            AutoRemediationRequest auditRequest = session != null ? session.request() :
                    AutoRemediationRequest.builder().findingId(finding.getId()).sessionId(sessionId).build();
            saveAudit(finding, auditRequest, sessionId, call.toolName(), call.input(),
                    toolResult, toolDuration, true);

            if (!toolResult.success()) {
                status = "PARTIAL";
            }
        }

        summaryBuilder.append(buildDemoSummary(finding, actions));

        long totalDuration = System.currentTimeMillis() - startTime;

        // Persist remediation status on the finding
        finding.setRemediationStatus(status);
        findingRepository.save(finding);

        // Emit COMPLETED event
        progressEmitter.emit(sessionId, RemediationProgressEvent.builder()
                .type("FAILED".equals(status) ? EventType.ERROR : EventType.COMPLETED)
                .findingId(finding.getId())
                .sessionId(sessionId)
                .message(summaryBuilder.toString())
                .status(status)
                .stepNumber(actions.size())
                .totalSteps(actions.size())
                .elapsedMs(totalDuration)
                .demoMode(true)
                .build());
        progressEmitter.complete(sessionId);

        return AutoRemediationResponse.builder()
                .findingId(finding.getId())
                .sessionId(sessionId)
                .status(status)
                .actions(actions)
                .summary(summaryBuilder.toString())
                .totalDurationMs(totalDuration)
                .demoMode(true)
                .pendingApproval(false)
                .build();
    }

    private String buildToolDescription(String toolName, Map<String, Object> input, Finding finding) {
        return switch (toolName) {
            case "block_s3_public_access" ->
                    "Block all public access on S3 bucket " + input.getOrDefault("bucketName", finding.getResourceId());
            case "restrict_security_group" ->
                    "Restrict security group " + input.getOrDefault("groupId", finding.getResourceId()) +
                            " by removing port " + input.getOrDefault("port", "22") + " inbound from 0.0.0.0/0";
            case "enable_ebs_encryption" ->
                    "Enable encryption on EBS volume " + input.getOrDefault("volumeId", finding.getResourceId());
            case "enable_cloudtrail" ->
                    "Enable CloudTrail logging on trail " + input.getOrDefault("trailName", finding.getResourceId());
            case "rotate_access_key" ->
                    "Rotate access key for IAM user " + input.getOrDefault("username", finding.getResourceId());
            case "delete_unused_credentials" ->
                    "Delete unused credentials for IAM user " + input.getOrDefault("username", finding.getResourceId());
            default -> "Execute " + toolName + " on " + finding.getResourceId();
        };
    }

    // ── Synthetic tool call routing ──────────────────────────────────────

    private record SyntheticToolCall(String toolName, Map<String, Object> input) {}

    private List<SyntheticToolCall> buildSyntheticToolCalls(Finding finding) {
        List<SyntheticToolCall> calls = new ArrayList<>();
        String resourceType = finding.getResourceType() != null ? finding.getResourceType() : "";
        String resourceId = finding.getResourceId() != null ? finding.getResourceId() : "";
        String title = finding.getTitle() != null ? finding.getTitle() : "";

        switch (resourceType) {
            case "S3" -> {
                String bucketName = extractBucketName(resourceId);
                calls.add(new SyntheticToolCall("block_s3_public_access",
                        Map.of("bucketName", bucketName)));
            }
            case "EC2" -> {
                String groupId = resourceId;
                calls.add(new SyntheticToolCall("restrict_security_group",
                        Map.of("groupId", groupId, "port", 22, "protocol", "tcp")));
            }
            case "EBS" -> {
                String volumeId = resourceId;
                calls.add(new SyntheticToolCall("enable_ebs_encryption",
                        Map.of("volumeId", volumeId)));
            }
            case "CloudTrail" -> {
                String trailName = resourceId;
                calls.add(new SyntheticToolCall("enable_cloudtrail",
                        Map.of("trailName", trailName)));
            }
            case "IAM" -> {
                if (title.toLowerCase().contains("unused") || title.toLowerCase().contains("inactive")) {
                    calls.add(new SyntheticToolCall("delete_unused_credentials",
                            Map.of("username", extractUsernameFromArn(resourceId),
                                    "accessKeyId", resourceId)));
                } else {
                    calls.add(new SyntheticToolCall("rotate_access_key",
                            Map.of("username", extractUsernameFromArn(resourceId),
                                    "accessKeyId", resourceId)));
                }
            }
            default -> log.warn("No synthetic tool mapping for resource type: {}", resourceType);
        }

        return calls;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String buildFindingPrompt(Finding finding, AutoRemediationRequest request) {
        return String.format("""
                Please remediate the following cloud security finding:

                Finding ID: %s
                Title: %s
                Severity: %s
                Resource Type: %s
                Resource ID: %s
                Description: %s
                Recommended Remediation: %s
                Category: %s
                %s

                Analyze the finding and use the available tools to fix it. \
                After remediation, provide a brief summary of what was done.""",
                finding.getId(),
                finding.getTitle(),
                finding.getSeverity(),
                finding.getResourceType(),
                finding.getResourceId(),
                finding.getDescription(),
                finding.getRemediation() != null ? finding.getRemediation() : "N/A",
                finding.getCategory(),
                request.isDryRun() ? "\n**DRY RUN MODE** — do not make actual changes, only report what would be done." : "");
    }

    private void saveAudit(Finding finding, AutoRemediationRequest request,
                           String sessionId, String toolName,
                           Map<String, Object> toolInput, ToolResult toolResult,
                           long durationMs, boolean isMock) {
        try {
            RemediationAudit audit = RemediationAudit.builder()
                    .findingId(finding.getId())
                    .scanId(request.getScanId())
                    .toolName(toolName)
                    .toolInput(serializeJson(toolInput))
                    .toolOutput(serializeJson(Map.of(
                            "success", toolResult.success(),
                            "message", toolResult.message(),
                            "data", toolResult.data())))
                    .status(toolResult.success() ? "SUCCESS" : "FAILED")
                    .resourceType(finding.getResourceType())
                    .resourceId(finding.getResourceId())
                    .initiatedBy(isMock ? "DEMO_MODE" : "AGENTIC_CLAUDE")
                    .beforeState(toolResult.data().containsKey("beforeState")
                            ? serializeJson(toolResult.data().get("beforeState")) : null)
                    .afterState(toolResult.data().containsKey("afterState")
                            ? serializeJson(toolResult.data().get("afterState")) : null)
                    .claudeSessionId(sessionId)
                    .executedAt(LocalDateTime.now())
                    .isMock(isMock)
                    .build();
            auditRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to save remediation audit for tool {}: {}", toolName, e.getMessage(), e);
        }
    }

    private String buildDemoSummary(Finding finding, List<RemediationAction> actions) {
        long successCount = actions.stream().filter(a -> "SUCCESS".equals(a.getStatus())).count();
        long failedCount = actions.stream().filter(a -> "FAILED".equals(a.getStatus())).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Demo-mode remediation for %s finding '%s' on resource %s. ",
                finding.getSeverity(), finding.getTitle(), finding.getResourceId()));
        sb.append(String.format("Executed %d tool call(s): %d succeeded, %d failed. ",
                actions.size(), successCount, failedCount));

        for (RemediationAction action : actions) {
            sb.append(String.format("Tool '%s' returned %s. ", action.getToolName(), action.getStatus()));
        }

        if (successCount == actions.size()) {
            sb.append("All remediation actions completed successfully.");
        } else {
            sb.append("Some actions require manual follow-up.");
        }

        return sb.toString();
    }

    private String extractBucketName(String resourceId) {
        if (resourceId != null && resourceId.startsWith("arn:aws:s3:::")) {
            return resourceId.substring("arn:aws:s3:::".length());
        }
        return resourceId != null ? resourceId : "";
    }

    private String extractUsernameFromArn(String arn) {
        if (arn != null && arn.contains("/")) {
            return arn.substring(arn.lastIndexOf('/') + 1);
        }
        return arn != null ? arn : "unknown";
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return String.valueOf(obj);
        }
    }
}
