package com.cspm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "regulatory_finding_mappings")
public class RegulatoryFindingMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_finding_details_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private AiFindingDetails aiFindingDetails;

    @Column(nullable = false, length = 50)
    private String framework;

    @Column(name = "control_id", nullable = false, length = 100)
    private String controlId;

    @Column(name = "control_title", length = 500)
    private String controlTitle;

    @Column(name = "violation_summary", columnDefinition = "TEXT")
    private String violationSummary;

    @Column(name = "remediation_guidance", columnDefinition = "TEXT")
    private String remediationGuidance;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
