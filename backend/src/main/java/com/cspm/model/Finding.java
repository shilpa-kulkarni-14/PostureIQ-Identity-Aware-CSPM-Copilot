package com.cspm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "findings")
public class Finding {

    @Id
    private String id;

    private String resourceType;
    private String resourceId;
    private String severity;
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 4000)
    private String remediation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ScanResult scanResult;
}
