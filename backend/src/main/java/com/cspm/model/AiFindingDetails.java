package com.cspm.model;

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
    private Finding finding;

    private String finalSeverity;

    @Column(length = 4000)
    private String attackPathNarrative;

    @Column(length = 2000)
    private String businessImpact;

    @Column(length = 4000)
    private String remediationSteps;
}
