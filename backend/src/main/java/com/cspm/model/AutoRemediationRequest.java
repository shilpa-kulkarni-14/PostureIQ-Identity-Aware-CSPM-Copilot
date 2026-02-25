package com.cspm.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRemediationRequest {
    private String findingId;
    private String scanId;
    private String sessionId;
    @Builder.Default
    private boolean dryRun = false;
    @Builder.Default
    private boolean requireApproval = false; // when true, returns plan before executing
}
