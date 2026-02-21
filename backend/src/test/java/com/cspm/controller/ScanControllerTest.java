package com.cspm.controller;

import com.cspm.model.ScanResult;
import com.cspm.service.ScannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScannerService scannerService;

    @Test
    void scan_withoutAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/scan"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void triggerScan_withAuth_shouldReturnScanResult() throws Exception {
        mockMvc.perform(post("/api/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalFindings").isNumber());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void getScanResult_withValidId_shouldReturnResult() throws Exception {
        ScanResult scan = scannerService.runScan();

        mockMvc.perform(get("/api/scan/" + scan.getScanId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scan.getScanId()))
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.findings", hasSize(greaterThan(0))));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void getScanResult_withInvalidId_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/scan/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void getAllScans_shouldReturnList() throws Exception {
        scannerService.runScan();

        mockMvc.perform(get("/api/scans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void downloadPdfReport_withValidScan_shouldReturnPdf() throws Exception {
        ScanResult scan = scannerService.runScan();

        mockMvc.perform(get("/api/scan/" + scan.getScanId() + "/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void downloadPdfReport_withInvalidScan_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/scan/nonexistent-id/report"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void exportScanJson_withValidScan_shouldReturnJson() throws Exception {
        ScanResult scan = scannerService.runScan();

        mockMvc.perform(get("/api/scan/" + scan.getScanId() + "/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.scanId").value(scan.getScanId()));
    }
}
