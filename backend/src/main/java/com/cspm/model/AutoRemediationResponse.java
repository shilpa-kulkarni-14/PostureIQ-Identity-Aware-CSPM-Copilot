package com.cspm.model;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRemediationResponse {
    private String findingId;
    private String sessionId;
    private String status; // COMPLETED, FAILED, PARTIAL
    private List<RemediationAction> actions;
    private String summary;
    private long totalDurationMs;
    private boolean demoMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemediationAction {
        private String toolName;
        private String input;
        private String output;
        private String status; // SUCCESS, FAILED, MOCK
        private String beforeState;
        private String afterState;
        private long durationMs;
    }
}
