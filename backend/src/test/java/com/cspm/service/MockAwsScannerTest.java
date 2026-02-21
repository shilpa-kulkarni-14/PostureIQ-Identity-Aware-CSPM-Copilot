package com.cspm.service;

import com.cspm.model.ScanResult;
import com.cspm.repository.ScanResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MockAwsScannerTest {

    @Autowired
    private ScannerService scannerService;

    @Autowired
    private ScanResultRepository scanResultRepository;

    @Test
    @Transactional
    void runScan_shouldCreateScanWithFindings() {
        ScanResult result = scannerService.runScan();

        assertNotNull(result);
        assertNotNull(result.getScanId());
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getTimestamp());
        assertTrue(result.getTotalFindings() > 0);
        assertEquals(result.getFindings().size(), result.getTotalFindings());
    }

    @Test
    @Transactional
    void runScan_shouldHaveCorrectSeverityCounts() {
        ScanResult result = scannerService.runScan();

        int expectedTotal = result.getHighSeverity() + result.getMediumSeverity() + result.getLowSeverity();
        assertEquals(expectedTotal, result.getTotalFindings());
        assertTrue(result.getHighSeverity() > 0, "Should have HIGH severity findings");
        assertTrue(result.getMediumSeverity() > 0, "Should have MEDIUM severity findings");
        assertTrue(result.getLowSeverity() > 0, "Should have LOW severity findings");
    }

    @Test
    @Transactional
    void runScan_shouldPersistToDatabase() {
        ScanResult result = scannerService.runScan();

        Optional<ScanResult> retrieved = scannerService.getScanResultWithFindings(result.getScanId());

        assertTrue(retrieved.isPresent());
        assertEquals(result.getScanId(), retrieved.get().getScanId());
        assertEquals(result.getTotalFindings(), retrieved.get().getFindings().size());
    }

    @Test
    @Transactional
    void getScanResult_withInvalidId_shouldReturnEmpty() {
        Optional<ScanResult> result = scannerService.getScanResult("nonexistent-scan-id");

        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    void getAllScans_shouldReturnAllScans() {
        scannerService.runScan();
        scannerService.runScan();

        var allScans = scannerService.getAllScans();

        assertTrue(allScans.size() >= 2);
    }

    @Test
    @Transactional
    void runScan_findingsShouldHaveRequiredFields() {
        ScanResult result = scannerService.runScan();

        result.getFindings().forEach(finding -> {
            assertNotNull(finding.getId(), "Finding ID should not be null");
            assertNotNull(finding.getResourceType(), "Resource type should not be null");
            assertNotNull(finding.getResourceId(), "Resource ID should not be null");
            assertNotNull(finding.getSeverity(), "Severity should not be null");
            assertNotNull(finding.getTitle(), "Title should not be null");
            assertNotNull(finding.getDescription(), "Description should not be null");
            assertTrue(
                    finding.getSeverity().equals("HIGH") ||
                            finding.getSeverity().equals("MEDIUM") ||
                            finding.getSeverity().equals("LOW"),
                    "Severity should be HIGH, MEDIUM, or LOW"
            );
        });
    }
}
