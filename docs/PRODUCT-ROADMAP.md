# PostureIQ — Product Roadmap & Gap Analysis
**Document Type:** Senior PM Review | **Date:** February 25, 2026

---

## 1. Executive Summary

PostureIQ is a well-architected, full-stack CSPM platform combining infrastructure scanning (AWS S3, IAM, EC2, EBS, CloudTrail, VPC), identity-aware risk correlation (CIEM), AI-powered attack narrative generation (Claude Sonnet 4), agentic auto-remediation with SSE streaming and approval workflow, and a RAG-based regulatory compliance engine (Voyage AI + pgvector). The codebase demonstrates strong engineering patterns throughout.

The platform is production-capable for a single-account AWS deployment with demo mode. To become a commercially viable enterprise CSPM product, **27 distinct gaps** must be addressed. Estimated engineering effort to close all gaps: 12–18 months with a 4-person team.

---

## 2. Current State Assessment

### What Is Working (Production-Grade)

| Domain | Feature |
|--------|---------|
| Auth | JWT, BCrypt, rate limiting (5/60s), route guards, auth interceptor |
| Scanning | 13 CSPM rules across S3, IAM, EC2, EBS, CloudTrail, VPC |
| IAM Analysis | Identity ingestion, 4 risk checks (admin policy, no MFA, dormant, wildcard) |
| Correlation | 3 patterns: S3+S3 access (CRITICAL), EC2+EC2 access (HIGH), admin+HIGH finding (HIGH) |
| AI Enrichment | Claude attack narratives, business impact, remediation steps, regulatory RAG |
| Agentic Remediation | Claude agentic loop, demo synthetic mode, approval workflow, SSE streaming, audit trail |
| MCP Tools | 6 write tools + 3 read tools in McpToolRegistry |
| Dashboard | 6 charts, high-risk identity table, remediation analytics |
| Reports | PDF (OpenPDF) + JSON export |
| Deployment | Docker Compose (3 containers), GitHub Actions CI/CD |

### Critical Observations from Code Review

**1. Unauthenticated API endpoints (`SecurityConfig.java`):**
`/api/scan/**`, `/api/identities/**`, `/api/dashboard/**`, and `/api/remediate` are all publicly accessible without a JWT token. Any unauthenticated actor can trigger AWS scans, read all findings, and execute remediations.

**2. In-memory pending approvals (`AgenticRemediationService.java` line 46):**
```java
private final Map<String, PendingApprovalSession> pendingApprovals = new ConcurrentHashMap<>();
```
Server restarts silently lose all pending approvals. Cannot scale horizontally.

**3. EC2 RDP remediation bug (`AgenticRemediationService.java` lines 691–694):**
Port 22 (SSH) is hardcoded even for RDP (port 3389) findings.

**4. SSE/agentic timeout mismatch:** `SseEmitter` has a 30-second timeout; the agentic loop is configured for 120 seconds.

**5. Dashboard category chart never populated:** `categoryChartData` is initialized at `[0, 0, 0]` and `updateCharts()` never sets it.

**6. `ddl-auto: update` in production:** Schema changes are applied automatically and irreversibly.

**7. Fake compliance score:** `(totalFindings - totalHigh) / totalFindings * 100` is a severity ratio, not a compliance score.

**8. Frontend type mismatch:** `finding.model.ts` missing `'CloudTrail'` and `'VPC'` resource types.

---

## 3. Gap Analysis

### 3.1 Cloud Coverage Gaps

| Gap | Enterprise Impact |
|-----|-------------------|
| GCP not supported | 60%+ enterprise customers are multi-cloud |
| Azure not supported | Microsoft shops are a major CSPM buyer segment |
| AWS Lambda not scanned | Fastest-growing attack surface |
| AWS RDS not scanned | Database exposure is top compliance violation |
| AWS Secrets Manager / KMS rotation | Leading breach cause |
| AWS Organizations / multi-account scanning | Enterprises run dozens–hundreds of accounts |
| Kubernetes / EKS cluster security | Container orchestration blind spot |
| Terraform / CloudFormation IaC scanning | Shift-left is fastest-growing CSPM use case |

### 3.2 Security Rule Depth Gaps (Currently 17 rules total)

| Service | Current Rules | Missing High-Priority Rules |
|---------|-------------|----------------------------|
| S3 | 3 | Versioning disabled, object lock, MFA delete, replication encryption, logging disabled, cross-account access |
| IAM | 5 | Password policy, root account MFA, inline policies, permission boundaries, cross-account trust |
| EC2 | 3 | IMDSv1 enabled (metadata takeover), public IPs on instances, unrestricted ICMP |
| EBS | 1 | Public snapshots, cross-account snapshot sharing |
| VPC | 1 | Flow logs disabled, NACL allows all |
| CloudTrail | 2 | Log file validation disabled, not integrated with CloudWatch |
| RDS | **0** | All checks missing |
| Lambda | **0** | All checks missing |
| Secrets Manager | **0** | All checks missing |

### 3.3 Compliance Framework Gaps

- No CIS Benchmark score visible in UI
- Single aggregate compliance number — not per-framework breakdown
- No SOC 2, PCI DSS v4.0, HIPAA, FedRAMP, or ISO 27001 mappings
- No compliance report export for auditors
- No evidence collection (API call log, screenshot)
- Continuous compliance monitoring absent — point-in-time scans only

### 3.4 Correlation Engine Gaps

| Gap |
|-----|
| No blast radius calculation |
| No multi-hop lateral movement paths |
| N×M finding explosion (1 admin × 10 HIGH findings = 10 identical CORRELATED records) |
| No network reachability (open SG + running instance + public IP = internet-reachable) |
| No data sensitivity classification (PII bucket vs log bucket) |
| No deduplication logic for findings |

### 3.5 Agentic Remediation Gaps

| Gap | Risk |
|-----|------|
| Pending approvals lost on restart | HIGH |
| No rollback capability | HIGH |
| IAM policy repair tool missing | HIGH |
| MFA enforcement tool missing | HIGH |
| No post-remediation verification | MEDIUM |
| No bulk remediation or campaign management | MEDIUM |
| No scheduling ("fix in maintenance window") | MEDIUM |
| No change management integration (Jira/ServiceNow) | MEDIUM |
| RDP remediation hardcoded to port 22 bug | CRITICAL |

### 3.6 Dashboard & UX Gaps

| Gap |
|-----|
| Compliance score is a fake metric |
| No MTTR tracking (key CISO KPI) |
| No finding age / firstSeenAt tracking |
| No finding assignment / ownership model |
| No SLA tracking or breach alerts |
| No notification system (Slack, email, PagerDuty) |
| No scan scheduling UI |
| No finding suppression / false positive management |
| No multi-user RBAC (role field exists but is not enforced) |
| Dashboard category chart always zero |
| Findings list not paginated (full list in DOM) |

### 3.7 Integration Gaps

| Integration | Priority |
|-------------|---------|
| Slack / Microsoft Teams | HIGH |
| Jira / ServiceNow | HIGH |
| SAML/OIDC SSO (Okta, Azure AD) | HIGH |
| PagerDuty / OpsGenie | HIGH |
| AWS Security Hub (bi-directional sync) | MEDIUM |
| SIEM (Splunk, Datadog, Elastic) | MEDIUM |
| GitHub / GitLab (IaC CI integration) | MEDIUM |
| Webhook system (generic events) | MEDIUM |

### 3.8 Observability Gaps

| Gap |
|-----|
| No application metrics (Micrometer/Prometheus) |
| No structured/JSON logging |
| No distributed tracing |
| No rate limiting on scan/remediation endpoints |
| Scans run synchronously in HTTP thread |
| SSE emitter timeout (30s) shorter than agentic timeout (120s) |

---

## 4. Prioritized Roadmap

### Phase 1 — Critical (Months 1–3): Security Hardening & Stability

| ID | Feature | Effort | Description |
|----|---------|--------|-------------|
| P1.1 | Authenticate All API Endpoints | 1 week | Require JWT for all `/api/**` paths except `/api/auth/**` and `/actuator/health` |
| P1.2 | Persist Pending Approvals to DB | 1 week | Replace `ConcurrentHashMap` with JPA entity + TTL cleanup |
| P1.3 | Fix EC2 RDP Remediation Bug | 1 day | Detect RDP vs SSH from finding title, use correct port |
| P1.4 | Replace `ddl-auto: update` with Flyway | 2 weeks | Add Flyway migrations, set `ddl-auto=validate` |
| P1.5 | Fix Dashboard Category Chart | 2 days | Wire `findingsByCategory` from backend to chart data |
| P1.6 | Real Compliance Scoring | 3 weeks | Per-framework scores (CIS, SOC 2, NIST, PCI DSS) via regulatory mappings |
| P1.7 | Finding Deduplication | 1 week | Deterministic IDs + `firstSeenAt`/`lastSeenAt` columns |
| P1.8 | Fix SSE Emitter Timeout | 3 days | Match SSE timeout to agentic timeout + heartbeat events |
| P1.9 | API Rate Limiting | 1 week | Bucket4j: 1 scan/min, 5 remediations/hour per user |
| P1.10 | Move API Keys to Secrets Manager | 1 week | AWS Secrets Manager integration with env var fallback |
| P1.11 | Fix Frontend Resource Types | 1 day | Add CloudTrail/VPC to type union and icon mappings |

### Phase 2 — Important (Months 4–8): Enterprise Feature Completeness

| ID | Feature | Effort | Description |
|----|---------|--------|-------------|
| P2.1 | IAM Policy Remediation Tool | 2 weeks | `restrict_iam_policy` + `enforce_mfa_policy` MCP tools |
| P2.2 | Multi-Account Scanning | 4 weeks | AWS Organizations support with `StsClient.assumeRole()` |
| P2.3 | RDS & Lambda Scanning | 3 weeks | 8+ new security rules across RDS and Lambda |
| P2.4 | Scheduled Recurring Scans | 2 weeks | Cron-based scheduling with Spring TaskScheduler |
| P2.5 | Finding Lifecycle Management | 3 weeks | Status machine: OPEN → IN_REMEDIATION → RESOLVED/SUPPRESSED |
| P2.6 | Notification System | 3 weeks | Slack, email, webhook integrations |
| P2.7 | Correlation Deduplication & Blast Radius | 2 weeks | Group N×M findings, add blast radius scoring |
| P2.8 | Role-Based Access Control | 3 weeks | ADMIN/SECURITY_ANALYST/READ_ONLY with `@PreAuthorize` |
| P2.9 | Async Scan Processing | 2 weeks | `@Async` + SSE progress stream for scans |
| P2.10 | SAML/OIDC SSO | 3 weeks | Okta, Azure AD integration |
| P2.11 | IaC Scanning (Terraform/CloudFormation) | 4 weeks | Shift-left scanning with CI template |
| P2.12 | Pagination & Virtual Scrolling | 2 weeks | Server-side pagination + Angular CDK virtual scroll |

### Phase 3 — Nice to Have (Months 9–18): Market Differentiation

| Feature | Effort | Differentiator |
|---------|--------|----------------|
| GCP Cloud Support | 8–10 weeks | Multi-cloud coverage |
| Azure Cloud Support | 8–10 weeks | Microsoft shop coverage |
| Bulk Remediation Campaigns | 3 weeks | One approval for 200 findings |
| Post-Remediation Verification | 2 weeks | Re-scan to confirm fix, auto-RESOLVED |
| Data Classification (Macie) | 4 weeks | PII/PHI bucket elevation to CRITICAL |
| SIEM Integration | 3 weeks | Splunk/Elastic/Datadog export |
| Visual Attack Path Graph | 5 weeks | D3.js/Cytoscape graph visualization |
| Jira/ServiceNow Ticketing | 2 weeks | Finding → ticket automation |
| Kubernetes/EKS Scanning | 5 weeks | Container workload security |
| Prometheus Metrics | 2 weeks | Scan duration, API cost tracking |
| Secrets Manager Checks | 2 weeks | Lambda env vars, rotation policies |

---

## 5. Technical Debt Register (Top 20)

| ID | Item | Severity | File |
|----|------|---------|------|
| TD-01 | `ddl-auto: update` in production | **Critical** | `application.yml` |
| TD-02 | Pending approvals in `ConcurrentHashMap` | **Critical** | `AgenticRemediationService.java:46` |
| TD-03 | RDP finding remediates port 22, not 3389 | **Critical** | `AgenticRemediationService.java:693` |
| TD-04 | Scan/remediate endpoints unauthenticated | **Critical** | `SecurityConfig.java` |
| TD-05 | Fake compliance score formula | **High** | `DashboardController.java:44` |
| TD-06 | Category chart never populated | **High** | `dashboard.component.ts:updateCharts()` |
| TD-07 | Random UUID per finding (no dedup) | **High** | All scanner services |
| TD-08 | SSE 30s timeout < agentic 120s timeout | **High** | `RemediationProgressEmitter.java` |
| TD-09 | `RestTemplate` blocks threads for AI calls | **Medium** | `ClaudeService.java`, `EmbeddingService.java` |
| TD-10 | `ClaudeService` creates `RestTemplate` inline | **Medium** | `ClaudeService.java:28` |
| TD-11 | `Finding.resourceType` missing CloudTrail/VPC | **Medium** | `finding.model.ts:3` |
| TD-12 | `CorrelationService` loads all scans into memory | **Medium** | `CorrelationService.java:32` |
| TD-13 | JWT in localStorage (XSS risk) | **Medium** | Auth interceptor |
| TD-14 | API keys in plain env vars | **High** | `application.yml` |
| TD-15 | No structured JSON logging | **Medium** | All services |
| TD-16 | No input validation on scan endpoints | **Medium** | `ScanController.java` |
| TD-17 | `ObjectMapper` created inline in scanner | **Low** | `AwsScannerService.java:227` |
| TD-18 | IAM Groups not ingested | **Low** | `AwsIamIngestionService.java` |
| TD-19 | `Finding.description` capped at 2000 chars | **Low** | `Finding.java:24` |
| TD-20 | No async scan progress reporting | **Medium** | `ScanController.java` |

---

## 6. Success Metrics

| Metric | Current | 3-Month Target | 12-Month Target |
|--------|---------|----------------|-----------------|
| API auth coverage | ~30% of endpoints | 100% | 100% |
| Backend test coverage | ~40% | 70% | 85% |
| End-to-end tests | 0 | 10 flows | 50 flows |
| AWS services scanned | 6 | 10 | 18 |
| Security rules | 17 | 50 | 150+ |
| Compliance frameworks | 1 (partial RAG) | 4 | 8 |
| Cloud providers | 1 (AWS) | 1 (hardened) | 3 |
| Notification integrations | 0 | 3 | 6 |
| Remediation coverage | 35% | 60% | 80% |
| MTTR for CRITICAL findings | Not tracked | < 4 hours | < 2 hours |

---

## 7. Recommendations

1. **Ship a security hardening sprint before any external demo.** Unauthenticated endpoints (P1.1) and the RDP port bug (P1.3) will immediately kill credibility with security-savvy evaluators.

2. **Rebuild the compliance score before sales conversations.** The current formula is rejected immediately by CISOs. CIS AWS Foundations Benchmark Level 1 scoring should be the Q1 deliverable.

3. **Deepen correlation before adding new cloud providers.** Blast radius and multi-hop paths add depth that wins enterprise deals over breadth.

4. **Replace `RestTemplate` with `WebClient` in the AI layer.** Blocking Spring MVC threads on high-latency AI calls will cause thread pool exhaustion under concurrent load.

5. **Formalize a `FindingStatus` state machine.** Unlocks MTTR tracking, SLA enforcement, assignment workflows, and "Recently Resolved" dashboard — all with a single schema change.

6. **Add Playwright/Cypress E2E tests before Phase 2.** Zero E2E tests is a regression time bomb. Automate the 5 critical flows first.

7. **Move to Flyway immediately.** `ddl-auto: update` is dangerous at this codebase size. One-time migration that pays off every subsequent feature.
