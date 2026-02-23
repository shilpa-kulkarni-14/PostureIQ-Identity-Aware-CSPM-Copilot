package com.cspm.controller;

import com.cspm.model.*;
import com.cspm.repository.AiFindingDetailsRepository;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.RegulatoryFindingMappingRepository;
import com.cspm.service.ClaudeService;
import com.cspm.service.CorrelationService;
import com.cspm.service.IamIngestionService;
import com.cspm.service.IamRiskService;
import com.cspm.service.RegulatoryIngestionService;
import com.cspm.service.ScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PostureIqController {

    private final IamIngestionService iamIngestionService;
    private final IamRiskService iamRiskService;
    private final CorrelationService correlationService;
    private final ClaudeService claudeService;
    private final ScannerService scannerService;
    private final IamIdentityRepository iamIdentityRepository;
    private final FindingRepository findingRepository;
    private final AiFindingDetailsRepository aiFindingDetailsRepository;
    private final RegulatoryFindingMappingRepository regulatoryFindingMappingRepository;
    private final RegulatoryIngestionService regulatoryIngestionService;

    @PostMapping("/scan/iam")
    public ResponseEntity<ScanResult> runIamScan() {
        log.info("Starting IAM scan");
        iamIngestionService.ingestIdentities();
        ScanResult result = iamRiskService.runIamScan();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/scan/correlate")
    public ResponseEntity<ScanResult> correlateFindings() {
        log.info("Starting correlation of IAM + CSPM findings");
        ScanResult result = correlationService.correlate();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/scan/{scanId}/enrich")
    public ResponseEntity<List<AiFindingDetails>> enrichFindings(@PathVariable String scanId) {
        log.info("Starting AI enrichment for scan {}", scanId);
        return scannerService.getScanResultWithFindings(scanId)
                .map(scanResult -> {
                    List<AiFindingDetails> enrichedDetails = new ArrayList<>();
                    for (Finding finding : scanResult.getFindings()) {
                        if ("CORRELATED".equals(finding.getCategory())) {
                            Optional<AiFindingDetails> existing = aiFindingDetailsRepository.findByFindingId(finding.getId());
                            if (existing.isPresent()) {
                                enrichedDetails.add(existing.get());
                            } else {
                                AiFindingDetails details = claudeService.enrichFinding(finding);
                                aiFindingDetailsRepository.save(details);
                                enrichedDetails.add(details);
                            }
                        }
                    }
                    log.info("Enriched {} correlated findings for scan {}", enrichedDetails.size(), scanId);
                    return ResponseEntity.ok(enrichedDetails);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/identities/high-risk")
    public ResponseEntity<List<HighRiskIdentityResponse>> getHighRiskIdentities() {
        List<IamIdentity> identities = iamIdentityRepository.findAll();
        List<HighRiskIdentityResponse> responses = new ArrayList<>();

        for (IamIdentity identity : identities) {
            List<Finding> findings = findingRepository.findByPrimaryIdentityArn(identity.getArn());

            int highCount = (int) findings.stream()
                    .filter(f -> "HIGH".equals(f.getSeverity()) || "CRITICAL".equals(f.getSeverity()))
                    .count();
            int riskScore = findings.stream().mapToInt(f -> {
                String sev = f.getSeverity();
                if (sev == null) return 0;
                return switch (sev) {
                    case "CRITICAL" -> 4;
                    case "HIGH" -> 3;
                    case "MEDIUM" -> 2;
                    case "LOW" -> 1;
                    default -> 0;
                };
            }).sum();

            responses.add(HighRiskIdentityResponse.builder()
                    .identityArn(identity.getArn())
                    .identityName(identity.getName())
                    .identityType(identity.getIdentityType().name())
                    .riskScore(riskScore)
                    .findingCount(findings.size())
                    .highSeverityCount(highCount)
                    .findings(findings)
                    .build());
        }

        responses.sort(Comparator.comparingInt(HighRiskIdentityResponse::getRiskScore).reversed());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/scan/{scanId}/compliance-summary")
    public ResponseEntity<ComplianceSummaryResponse> getComplianceSummary(@PathVariable String scanId) {
        log.info("Getting compliance summary for scan {}", scanId);

        return scannerService.getScanResultWithFindings(scanId)
                .map(scanResult -> {
                    // Collect all AI finding detail IDs for this scan
                    List<Long> detailIds = new ArrayList<>();
                    List<RegulatoryFindingMapping> allMappings = new ArrayList<>();

                    for (Finding finding : scanResult.getFindings()) {
                        aiFindingDetailsRepository.findByFindingId(finding.getId())
                                .ifPresent(details -> {
                                    detailIds.add(details.getId());
                                    if (details.getRegulatoryMappings() != null) {
                                        allMappings.addAll(details.getRegulatoryMappings());
                                    }
                                });
                    }

                    // Group by framework
                    Map<String, List<RegulatoryFindingMapping>> byFramework = allMappings.stream()
                            .collect(Collectors.groupingBy(RegulatoryFindingMapping::getFramework));

                    List<ComplianceSummaryResponse.FrameworkSummary> summaries = byFramework.entrySet().stream()
                            .map(entry -> {
                                List<RegulatoryFindingMapping> frameworkMappings = entry.getValue();
                                List<String> criticalControls = frameworkMappings.stream()
                                        .filter(m -> m.getRelevanceScore() != null && m.getRelevanceScore() >= 0.9)
                                        .map(RegulatoryFindingMapping::getControlId)
                                        .distinct()
                                        .collect(Collectors.toList());

                                double avgScore = frameworkMappings.stream()
                                        .filter(m -> m.getRelevanceScore() != null)
                                        .mapToDouble(RegulatoryFindingMapping::getRelevanceScore)
                                        .average()
                                        .orElse(0.0);

                                String riskLevel = avgScore >= 0.9 ? "CRITICAL" :
                                        avgScore >= 0.7 ? "HIGH" :
                                        avgScore >= 0.5 ? "MEDIUM" : "LOW";

                                return ComplianceSummaryResponse.FrameworkSummary.builder()
                                        .framework(entry.getKey())
                                        .violationCount(frameworkMappings.size())
                                        .criticalControls(criticalControls)
                                        .overallRiskLevel(riskLevel)
                                        .build();
                            })
                            .sorted(Comparator.comparingLong(ComplianceSummaryResponse.FrameworkSummary::getViolationCount).reversed())
                            .collect(Collectors.toList());

                    ComplianceSummaryResponse response = ComplianceSummaryResponse.builder()
                            .scanId(scanId)
                            .frameworkSummaries(summaries)
                            .totalViolations(allMappings.size())
                            .frameworksCovered(new ArrayList<>(byFramework.keySet()))
                            .build();

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/findings/{findingId}/compliance")
    public ResponseEntity<Map<String, Object>> getFindingCompliance(@PathVariable String findingId) {
        log.info("Getting compliance details for finding {}", findingId);

        return aiFindingDetailsRepository.findByFindingId(findingId)
                .map(details -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("findingId", findingId);
                    response.put("regulatoryAnalysis", details.getRegulatoryAnalysis());
                    response.put("mappings", details.getRegulatoryMappings() != null
                            ? details.getRegulatoryMappings() : Collections.emptyList());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/admin/regulatory/ingest")
    public ResponseEntity<Map<String, String>> reingestRegulatoryData() {
        log.info("Triggering regulatory data re-ingestion");
        try {
            regulatoryIngestionService.ingestAll();
            return ResponseEntity.ok(Map.of("status", "success", "message", "Regulatory data ingested successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
