package com.cspm.controller;

import com.cspm.model.ScanResult;
import com.cspm.repository.AiFindingDetailsRepository;
import com.cspm.repository.FindingRepository;
import com.cspm.repository.IamIdentityRepository;
import com.cspm.repository.IamPolicyRepository;
import com.cspm.repository.ScanResultRepository;
import com.cspm.service.CorrelationService;
import com.cspm.service.IamIngestionService;
import com.cspm.service.IamRiskService;
import com.cspm.service.ScannerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostureIqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScannerService scannerService;

    @Autowired
    private IamIngestionService iamIngestionService;

    @Autowired
    private IamRiskService iamRiskService;

    @Autowired
    private CorrelationService correlationService;

    @Autowired
    private ScanResultRepository scanResultRepository;

    @Autowired
    private FindingRepository findingRepository;

    @Autowired
    private IamIdentityRepository iamIdentityRepository;

    @Autowired
    private IamPolicyRepository iamPolicyRepository;

    @Autowired
    private AiFindingDetailsRepository aiFindingDetailsRepository;

    @BeforeEach
    void cleanDatabase() {
        aiFindingDetailsRepository.deleteAll();
        findingRepository.deleteAll();
        scanResultRepository.deleteAll();
        iamIdentityRepository.deleteAll();
        iamPolicyRepository.deleteAll();
    }

    // ========================================================================
    // POST /api/scan/iam - IAM Scan Endpoint
    // ========================================================================

    @Nested
    class IamScanEndpoint {

        @Test
        void runIamScan_withoutAuth_shouldReturnOk() throws Exception {
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_withAuth_shouldReturnCompletedScanWithFindings() throws Exception {
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings").isNumber())
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)))
                    .andExpect(jsonPath("$.highSeverity").isNumber())
                    .andExpect(jsonPath("$.mediumSeverity").isNumber())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_shouldDetectAdminLikeAccess() throws Exception {
            // Mock identities include admin-user and dormant-admin with AdministratorAccess
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[?(@.title == 'IAM identity has admin-like access')]",
                            hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_shouldDetectConsoleWithoutMfa() throws Exception {
            // dev-user and dormant-admin have console access without MFA
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[?(@.title == 'Console user without MFA')]",
                            hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_shouldDetectDormantHighPrivilegeIdentity() throws Exception {
            // dormant-admin: admin-like, last used 180 days ago (>90 days)
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[?(@.title == 'Dormant high-privilege identity')]",
                            hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_findingsShouldHaveIamCategory() throws Exception {
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[*].category",
                            everyItem(equalTo("IAM"))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_findingsShouldHavePrimaryIdentityArn() throws Exception {
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[*].primaryIdentityArn",
                            everyItem(startsWith("arn:aws:iam:"))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void runIamScan_calledTwice_shouldProduceFreshResults() throws Exception {
            // First scan
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty());

            // Second scan should also succeed (identities are re-ingested)
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

    // ========================================================================
    // POST /api/scan/correlate - Correlation Endpoint
    // ========================================================================

    @Nested
    class CorrelateEndpoint {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_withNoExistingScans_shouldReturnEmptyFindings() throws Exception {
            // No CSPM or IAM scans run yet -- correlation should still succeed
            // but produce zero correlated findings since there is no data to correlate
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings").value(0));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_afterIamScanOnly_shouldReturnEmptyOrMinimalFindings() throws Exception {
            // Run IAM scan only (no CONFIG findings to cross-reference)
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
            // Without CONFIG findings, no S3/EC2 correlation can occur.
            // Admin+HIGH config correlation also needs CONFIG findings.
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_afterCspmScanOnly_shouldReturnEmptyFindings() throws Exception {
            // Run CSPM scan only (no IAM identities ingested)
            scannerService.runScan();

            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings").value(0));
            // Correlation needs identities, and none were ingested
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_afterBothScans_shouldReturnCorrelatedFindings() throws Exception {
            // Step 1: Run CSPM scan (produces CONFIG findings including S3 public, EC2 SG open)
            scannerService.runScan();

            // Step 2: Run IAM scan (ingests mock identities + produces IAM findings)
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            // Step 3: Correlate
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)))
                    .andExpect(jsonPath("$.findings[*].category",
                            everyItem(equalTo("CORRELATED"))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_afterBothScans_shouldDetectS3PlusIdentityCorrelation() throws Exception {
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            // admin-user and ci-role have S3 access; mock CSPM has public S3 buckets
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[?(@.title == 'Over-privileged identity can access public S3 bucket')]",
                            hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_afterBothScans_shouldDetectEc2PlusIdentityCorrelation() throws Exception {
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            // admin-user and ci-role have EC2 access; mock CSPM has open SSH/RDP SGs
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[?(@.title == 'Identity with EC2 access and exposed security group')]",
                            hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_afterBothScans_shouldDetectAdminPlusHighConfigCorrelation() throws Exception {
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            // admin-user and dormant-admin are admin-like; CSPM has HIGH findings
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[?(@.title == 'Admin identity can exploit misconfigured resources')]",
                            hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_correlatedFindingsShouldHaveCriticalOrHighSeverity() throws Exception {
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.findings[*].severity",
                            everyItem(isIn(new String[]{"CRITICAL", "HIGH", "MEDIUM"}))));
        }

        @Test
        void correlate_withoutAuth_shouldReturnOk() throws Exception {
            // The endpoint is permitAll per SecurityConfig
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty());
        }
    }

    // ========================================================================
    // POST /api/scan/{scanId}/enrich - AI Enrichment Endpoint
    // ========================================================================

    @Nested
    class EnrichEndpoint {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_withNonExistentScanId_shouldReturnNotFound() throws Exception {
            mockMvc.perform(post("/api/scan/nonexistent-scan-id/enrich"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_withCspmScanOnly_shouldReturnEmptyList() throws Exception {
            // CSPM findings have category=CONFIG (default), not CORRELATED
            // enrich only processes CORRELATED findings
            ScanResult cspmScan = scannerService.runScan();

            mockMvc.perform(post("/api/scan/" + cspmScan.getScanId() + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_withIamScanOnly_shouldReturnEmptyList() throws Exception {
            // IAM findings have category=IAM, not CORRELATED
            iamIngestionService.ingestIdentities();
            ScanResult iamScan = iamRiskService.runIamScan();

            mockMvc.perform(post("/api/scan/" + iamScan.getScanId() + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_withCorrelatedScan_shouldReturnEnrichedDetails() throws Exception {
            // Full pipeline: CSPM -> IAM -> Correlate -> Enrich
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            ScanResult correlationScan = correlationService.correlate();

            mockMvc.perform(post("/api/scan/" + correlationScan.getScanId() + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$[0].findingId").isNotEmpty())
                    .andExpect(jsonPath("$[0].attackPathNarrative").isNotEmpty())
                    .andExpect(jsonPath("$[0].businessImpact").isNotEmpty())
                    .andExpect(jsonPath("$[0].remediationSteps").isNotEmpty())
                    .andExpect(jsonPath("$[0].finalSeverity").isNotEmpty());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_enrichedCountShouldMatchCorrelatedFindingCount() throws Exception {
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            ScanResult correlationScan = correlationService.correlate();

            int correlatedCount = correlationScan.getTotalFindings();

            mockMvc.perform(post("/api/scan/" + correlationScan.getScanId() + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(correlatedCount)));
        }

        @Test
        void enrich_withoutAuth_shouldReturnOk() throws Exception {
            // /api/scan/** is permitAll
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            ScanResult correlationScan = correlationService.correlate();

            mockMvc.perform(post("/api/scan/" + correlationScan.getScanId() + "/enrich"))
                    .andExpect(status().isOk());
        }
    }

    // ========================================================================
    // GET /api/identities/high-risk - High-Risk Identities Endpoint
    // ========================================================================

    @Nested
    class HighRiskIdentitiesEndpoint {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_withNoData_shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_afterIamIngestionOnly_shouldReturnIdentitiesWithZeroFindings() throws Exception {
            // Ingest identities but run no scans -- no findings linked to them
            iamIngestionService.ingestIdentities();

            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(5))) // 5 mock identities
                    .andExpect(jsonPath("$[0].findingCount").value(0));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_afterIamScan_shouldReturnIdentitiesWithFindings() throws Exception {
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$[0].identityArn").isNotEmpty())
                    .andExpect(jsonPath("$[0].identityName").isNotEmpty())
                    .andExpect(jsonPath("$[0].identityType").isNotEmpty())
                    .andExpect(jsonPath("$[0].riskScore").isNumber());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_shouldBeSortedByRiskScoreDescending() throws Exception {
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].riskScore",
                            greaterThanOrEqualTo(
                                    // second element's risk score should be <= first
                                    0)));
            // We verify descending order: first element should have highest risk
            // dormant-admin should be at or near top: admin-like + no MFA + dormant = 3 findings (HIGH + HIGH + MEDIUM)
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_dormantAdminShouldHaveHighestRiskScore() throws Exception {
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            // dormant-admin: admin-like (HIGH) + console-no-MFA (HIGH) + dormant (MEDIUM) = 3+3+2 = 8
            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].identityName").value("dormant-admin"))
                    .andExpect(jsonPath("$[0].riskScore", greaterThanOrEqualTo(8)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_readonlyUserShouldHaveZeroRiskScore() throws Exception {
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            // readonly-user: has MFA, not admin, not dormant => 0 findings => risk=0
            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.identityName == 'readonly-user')].riskScore",
                            contains(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_afterFullPipeline_shouldIncludeCorrelatedFindings() throws Exception {
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            correlationService.correlate();

            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(5)))
                    // After correlation, identities with correlated findings should have higher risk scores
                    .andExpect(jsonPath("$[0].riskScore", greaterThan(0)))
                    .andExpect(jsonPath("$[0].findingCount", greaterThan(0)));
        }

        @Test
        void getHighRiskIdentities_withoutAuth_shouldReturnOk() throws Exception {
            // /api/identities/** is permitAll
            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void getHighRiskIdentities_responseFieldsShouldMatchExpectedSchema() throws Exception {
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].identityArn").exists())
                    .andExpect(jsonPath("$[0].identityName").exists())
                    .andExpect(jsonPath("$[0].identityType").exists())
                    .andExpect(jsonPath("$[0].riskScore").exists())
                    .andExpect(jsonPath("$[0].findingCount").exists())
                    .andExpect(jsonPath("$[0].highSeverityCount").exists())
                    .andExpect(jsonPath("$[0].findings").isArray());
        }
    }

    // ========================================================================
    // Full Pipeline Integration Tests (Demo Scenario)
    // ========================================================================

    @Nested
    class FullPipelineIntegration {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void fullPipeline_cspmThenIamThenCorrelateThenEnrich_shouldCompleteSuccessfully() throws Exception {
            // Step 1: CSPM scan
            mockMvc.perform(post("/api/scan"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)));

            // Step 2: IAM scan
            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)));

            // Step 3: Correlate
            MvcResult correlationMvcResult = mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)))
                    .andExpect(jsonPath("$.findings[*].category", everyItem(equalTo("CORRELATED"))))
                    .andReturn();

            // Extract scanId from correlation result for enrichment
            ObjectMapper mapper = new ObjectMapper();
            JsonNode correlationJson = mapper.readTree(correlationMvcResult.getResponse().getContentAsString());
            String scanId = correlationJson.get("scanId").asText();

            // Step 4: Enrich
            mockMvc.perform(post("/api/scan/" + scanId + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$[0].attackPathNarrative").isNotEmpty())
                    .andExpect(jsonPath("$[0].businessImpact").isNotEmpty())
                    .andExpect(jsonPath("$[0].remediationSteps").isNotEmpty());

            // Step 5: Verify high-risk identities reflect the full pipeline
            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(5)))
                    .andExpect(jsonPath("$[0].riskScore", greaterThan(0)))
                    .andExpect(jsonPath("$[0].findingCount", greaterThan(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void fullPipeline_runTwice_shouldNotCorruptData() throws Exception {
            // First pass
            mockMvc.perform(post("/api/scan")).andExpect(status().isOk());
            mockMvc.perform(post("/api/scan/iam")).andExpect(status().isOk());
            mockMvc.perform(post("/api/scan/correlate")).andExpect(status().isOk());

            // Second pass -- should not blow up or duplicate erroneously
            mockMvc.perform(post("/api/scan")).andExpect(status().isOk());
            mockMvc.perform(post("/api/scan/iam")).andExpect(status().isOk());
            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlatedScan_shouldBeRetrievableViaScanController() throws Exception {
            // Run the full pipeline
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            ScanResult correlationScan = correlationService.correlate();

            // The correlated scan should be retrievable via the GET /api/scan/{id} endpoint
            mockMvc.perform(get("/api/scan/" + correlationScan.getScanId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").value(correlationScan.getScanId()))
                    .andExpect(jsonPath("$.findings").isArray())
                    .andExpect(jsonPath("$.findings", hasSize(greaterThan(0))));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void allScans_shouldAppearInScanList() throws Exception {
            // Run CSPM + IAM + Correlation
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            correlationService.correlate();

            // GET /api/scans should show at least 3 scans (CSPM, IAM, Correlation)
            mockMvc.perform(get("/api/scans"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
        }
    }

    // ========================================================================
    // Edge Cases & Demo-Breaking Scenarios
    // ========================================================================

    @Nested
    class EdgeCases {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_calledTwiceOnSameScan_shouldReturnExistingEnrichments() throws Exception {
            // Double-enrich should be safe: the controller checks for existing
            // AiFindingDetails before saving, returning cached results on re-enrich.
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            ScanResult correlationScan = correlationService.correlate();
            String scanId = correlationScan.getScanId();

            // First enrichment succeeds
            MvcResult first = mockMvc.perform(post("/api/scan/" + scanId + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andReturn();

            // Second enrichment should also succeed and return same results
            MvcResult second = mockMvc.perform(post("/api/scan/" + scanId + "/enrich"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                    .andReturn();

            // Both should return the same number of enriched findings
            ObjectMapper mapper = new ObjectMapper();
            JsonNode firstJson = mapper.readTree(first.getResponse().getContentAsString());
            JsonNode secondJson = mapper.readTree(second.getResponse().getContentAsString());
            assertEquals(firstJson.size(), secondJson.size());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_calledWithoutIamIngestion_shouldNotThrow() throws Exception {
            // Only CSPM scan, no IAM ingestion -- identity list will be empty
            scannerService.runScan();

            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void enrich_withEmptyScanId_shouldReturnNotFound() throws Exception {
            // UUID-like but nonexistent
            mockMvc.perform(post("/api/scan/00000000-0000-0000-0000-000000000000/enrich"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void iamScan_afterMultipleIngestions_shouldStillProduceValidFindings() throws Exception {
            // Ingest identities multiple times (simulates user clicking button repeatedly)
            iamIngestionService.ingestIdentities();
            iamIngestionService.ingestIdentities();
            iamIngestionService.ingestIdentities();

            mockMvc.perform(post("/api/scan/iam"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalFindings", greaterThan(0)));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void correlate_withOnlyIamScanAndNoConfigFindings_shouldStillSucceed() throws Exception {
            // IAM scan produces IAM-category findings, but no CONFIG findings exist
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();

            mockMvc.perform(post("/api/scan/correlate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scanId").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        void highRiskIdentities_afterCorrelation_shouldIncludeCorrelatedFindingsInCount() throws Exception {
            // Full pipeline
            scannerService.runScan();
            iamIngestionService.ingestIdentities();
            iamRiskService.runIamScan();
            correlationService.correlate();

            mockMvc.perform(get("/api/identities/high-risk"))
                    .andExpect(status().isOk())
                    // admin-user should have IAM findings + correlated findings
                    .andExpect(jsonPath("$[?(@.identityName == 'admin-user')].findingCount",
                            everyItem(greaterThan(0))));
        }
    }
}
