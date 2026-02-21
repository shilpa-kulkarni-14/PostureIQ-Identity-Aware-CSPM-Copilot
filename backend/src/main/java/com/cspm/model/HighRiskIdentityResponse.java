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
public class HighRiskIdentityResponse {
    private String identityArn;
    private String identityName;
    private String identityType;
    private int riskScore;
    private int findingCount;
    private int highSeverityCount;
    private List<Finding> findings;
}
