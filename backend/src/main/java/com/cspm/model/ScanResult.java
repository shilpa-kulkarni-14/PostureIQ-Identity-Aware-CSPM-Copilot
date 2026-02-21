package com.cspm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scan_results")
public class ScanResult {

    @Id
    private String scanId;

    private Instant timestamp;

    private String status;

    @OneToMany(mappedBy = "scanResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Finding> findings = new ArrayList<>();

    private int totalFindings;
    private int highSeverity;
    private int mediumSeverity;
    private int lowSeverity;
}
