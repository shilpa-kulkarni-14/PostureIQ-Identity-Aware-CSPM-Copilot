package com.cspm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceSummaryResponse {

    private String scanId;
    private List<FrameworkSummary> frameworkSummaries;
    private int totalViolations;
    private List<String> frameworksCovered;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrameworkSummary {
        private String framework;
        private long violationCount;
        private List<String> criticalControls;
        private String overallRiskLevel;
    }
}
