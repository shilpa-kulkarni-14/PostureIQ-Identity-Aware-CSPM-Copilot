package com.cspm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_finding_details")
public class AiFindingDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Finding finding;

    private String finalSeverity;

    @Column(columnDefinition = "TEXT")
    private String attackPathNarrative;

    @Column(columnDefinition = "TEXT")
    private String businessImpact;

    @Column(columnDefinition = "TEXT")
    private String remediationSteps;

    @Column(columnDefinition = "TEXT")
    private String regulatoryAnalysis;

    @Builder.Default
    @OneToMany(mappedBy = "aiFindingDetails", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RegulatoryFindingMapping> regulatoryMappings = new ArrayList<>();

    @JsonProperty("findingId")
    public String getFindingId() {
        return finding != null ? finding.getId() : null;
    }
}
