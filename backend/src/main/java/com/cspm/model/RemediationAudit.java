package com.cspm.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "remediation_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemediationAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "finding_id")
    private String findingId;

    @Column(name = "scan_id")
    private String scanId;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_input", columnDefinition = "TEXT")
    private String toolInput;

    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    @Column(length = 20)
    private String status; // SUCCESS, FAILED, MOCK

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 500)
    private String resourceId;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "claude_session_id")
    private String claudeSessionId;

    @Column(name = "executed_at")
    @Builder.Default
    private LocalDateTime executedAt = LocalDateTime.now();

    @Column(name = "is_mock")
    @Builder.Default
    private Boolean isMock = false;
}
