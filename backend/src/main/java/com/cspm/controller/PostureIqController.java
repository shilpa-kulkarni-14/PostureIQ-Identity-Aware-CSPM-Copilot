package com.cspm.controller;

import com.cspm.model.*;
import com.cspm.repository.AiFindingDetailsRepository;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.service.ClaudeService;
import com.cspm.service.CorrelationService;
import com.cspm.service.IamIngestionService;
import com.cspm.service.IamRiskService;
import com.cspm.service.ScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
}
