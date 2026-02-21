package com.cspm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemediationRequest {
    private String findingId;
    private String resourceType;
    private String resourceId;
    private String title;
    private String description;
}
