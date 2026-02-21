package com.cspm.controller;

import com.cspm.model.Finding;
import com.cspm.model.ScanResult;
import com.cspm.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ScanResultRepository scanResultRepository;

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        List<ScanResult> allScans = scanResultRepository.findAllByOrderByTimestampDesc();

        Map<String, Object> stats = new LinkedHashMap<>();

        // Summary stats
        int totalScans = allScans.size();
        int totalFindings = allScans.stream().mapToInt(ScanResult::getTotalFindings).sum();
        int totalHigh = allScans.stream().mapToInt(ScanResult::getHighSeverity).sum();
        int totalMedium = allScans.stream().mapToInt(ScanResult::getMediumSeverity).sum();
        int totalLow = allScans.stream().mapToInt(ScanResult::getLowSeverity).sum();

        // Compliance score: percentage of non-HIGH findings
        double complianceScore = totalFindings > 0
                ? Math.round(((double) (totalFindings - totalHigh) / totalFindings) * 100.0)
                : 100.0;

        stats.put("totalScans", totalScans);
        stats.put("totalFindings", totalFindings);
        stats.put("complianceScore", complianceScore);

        // Severity distribution
        Map<String, Integer> severityDistribution = new LinkedHashMap<>();
        severityDistribution.put("HIGH", totalHigh);
        severityDistribution.put("MEDIUM", totalMedium);
        severityDistribution.put("LOW", totalLow);
        stats.put("severityDistribution", severityDistribution);

        // Findings by resource type (aggregate across all scans)
        Map<String, Long> resourceTypeCounts = allScans.stream()
                .flatMap(scan -> scan.getFindings().stream())
                .collect(Collectors.groupingBy(Finding::getResourceType, Collectors.counting()));
        stats.put("findingsByResourceType", resourceTypeCounts);

        // Scan history trends (most recent 10 scans, oldest first for chart)
        List<Map<String, Object>> scanHistory = allScans.stream()
                .limit(10)
                .sorted(Comparator.comparing(ScanResult::getTimestamp))
                .map(scan -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("scanId", scan.getScanId());
                    entry.put("timestamp", scan.getTimestamp().toString());
                    entry.put("totalFindings", scan.getTotalFindings());
                    entry.put("highSeverity", scan.getHighSeverity());
                    entry.put("mediumSeverity", scan.getMediumSeverity());
                    entry.put("lowSeverity", scan.getLowSeverity());
                    return entry;
                })
                .collect(Collectors.toList());
        stats.put("scanHistory", scanHistory);

        return ResponseEntity.ok(stats);
    }
}
