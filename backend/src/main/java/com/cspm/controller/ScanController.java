package com.cspm.controller;

import com.cspm.model.RemediationRequest;
import com.cspm.model.RemediationResponse;
import com.cspm.model.ScanResult;
import com.cspm.service.ClaudeService;
import com.cspm.service.ReportService;
import com.cspm.service.ScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScanController {

    private final ScannerService scannerService;
    private final ClaudeService claudeService;
    private final ReportService reportService;

    @PostMapping("/scan")
    public ResponseEntity<ScanResult> triggerScan(
            @RequestParam(required = false) String region) {
        ScanResult result = scannerService.runScan();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/scan/{scanId}")
    public ResponseEntity<ScanResult> getScanResult(@PathVariable String scanId) {
        return scannerService.getScanResultWithFindings(scanId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/scans")
    public ResponseEntity<List<ScanResult>> getAllScans() {
        return ResponseEntity.ok(scannerService.getAllScans());
    }

    @PostMapping("/remediate")
    public ResponseEntity<RemediationResponse> getRemediation(@RequestBody RemediationRequest request) {
        String remediation = claudeService.getRemediation(request);
        RemediationResponse response = RemediationResponse.builder()
                .findingId(request.getFindingId())
                .remediation(remediation)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/scan/{scanId}/report")
    public ResponseEntity<byte[]> downloadPdfReport(@PathVariable String scanId) {
        return scannerService.getScanResultWithFindings(scanId)
                .map(scanResult -> {
                    byte[] pdf = reportService.generatePdfReport(scanResult);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", "cspm-report-" + scanId + ".pdf");
                    headers.setContentLength(pdf.length);
                    return ResponseEntity.ok().headers(headers).body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/scan/{scanId}/export")
    public ResponseEntity<ScanResult> exportScanJson(@PathVariable String scanId) {
        return scannerService.getScanResultWithFindings(scanId)
                .map(scanResult -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentDispositionFormData("attachment", "cspm-scan-" + scanId + ".json");
                    return ResponseEntity.ok().headers(headers).body(scanResult);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
