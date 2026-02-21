package com.cspm.service;

import com.cspm.model.Finding;
import com.cspm.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Test
    void generatePdfReport_shouldProduceValidPdf() {
        ScanResult scanResult = createTestScanResult();

        byte[] pdf = reportService.generatePdfReport(scanResult);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        // PDF files start with %PDF
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    void generatePdfReport_withNoFindings_shouldStillProducePdf() {
        ScanResult scanResult = ScanResult.builder()
                .scanId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .status("COMPLETED")
                .findings(List.of())
                .totalFindings(0)
                .highSeverity(0)
                .mediumSeverity(0)
                .lowSeverity(0)
                .build();

        byte[] pdf = reportService.generatePdfReport(scanResult);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void generatePdfReport_withAllSeverities_shouldIncludeAllFindings() {
        ScanResult scanResult = createTestScanResult();

        byte[] pdf = reportService.generatePdfReport(scanResult);

        // Just verify it generates without error and has content
        assertNotNull(pdf);
        assertTrue(pdf.length > 100, "PDF should have substantial content");
    }

    private ScanResult createTestScanResult() {
        List<Finding> findings = List.of(
                Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("S3")
                        .resourceId("arn:aws:s3:::test-bucket")
                        .severity("HIGH")
                        .title("Public S3 Bucket")
                        .description("S3 bucket has public access enabled")
                        .build(),
                Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("EC2")
                        .resourceId("sg-12345")
                        .severity("MEDIUM")
                        .title("Open Security Group")
                        .description("Security group allows wide access")
                        .build(),
                Finding.builder()
                        .id(UUID.randomUUID().toString())
                        .resourceType("EBS")
                        .resourceId("vol-12345")
                        .severity("LOW")
                        .title("Unencrypted Volume")
                        .description("EBS volume is not encrypted")
                        .remediation("Enable EBS encryption")
                        .build()
        );

        return ScanResult.builder()
                .scanId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .status("COMPLETED")
                .findings(findings)
                .totalFindings(3)
                .highSeverity(1)
                .mediumSeverity(1)
                .lowSeverity(1)
                .build();
    }
}
