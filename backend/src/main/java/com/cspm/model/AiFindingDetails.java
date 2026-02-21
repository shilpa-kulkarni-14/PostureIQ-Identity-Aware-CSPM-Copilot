package com.cspm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

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

    @JsonProperty("findingId")
    public String getFindingId() {
        return finding != null ? finding.getId() : null;
    }
}
