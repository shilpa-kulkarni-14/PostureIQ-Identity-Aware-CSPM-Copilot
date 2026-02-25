package com.cspm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemediationProgressEvent {
    public enum EventType {
        STARTED, TOOL_EXECUTING, TOOL_COMPLETED, THINKING, COMPLETED, ERROR
    }

    private EventType type;
    private String findingId;
    private String sessionId;
    private String toolName;
    private String message;
    private String status;        // SUCCESS, FAILED, IN_PROGRESS
    private String beforeState;   // JSON
    private String afterState;    // JSON
    private int stepNumber;
    private int totalSteps;       // -1 if unknown
    private long elapsedMs;
    private boolean demoMode;
}
