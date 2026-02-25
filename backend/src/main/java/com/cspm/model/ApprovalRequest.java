package com.cspm.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    private String findingId;
    private String sessionId;
    private String toolName;
    private String toolInput;
    private String resourceType;
    private String resourceId;
    private String description;  // human-readable description of what will happen
    private boolean approved;
}
