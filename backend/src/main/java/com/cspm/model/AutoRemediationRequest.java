package com.cspm.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRemediationRequest {
    private String findingId;
    private String scanId;
    @Builder.Default
    private boolean dryRun = false;
}
