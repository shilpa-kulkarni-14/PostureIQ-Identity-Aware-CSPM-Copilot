# Personal Project
# Cloud Security Posture Scanner (CSPM Mini‑Tool) + PostureIQ Extension

Scan AWS configurations and infrastructure code for security risks (public S3 buckets, weak IAM policies, exposed secrets), **correlate them with IAM entitlements**, and get AI‑powered attack‑path narratives and remediation suggestions via Claude.

The base **CSPM Scanner** focuses on cloud misconfigurations and compliance reporting.
The **PostureIQ** extension adds an identity‑aware risk layer on top, so you see not just *what* is misconfigured, but *who* can actually exploit it and what to fix first.
The **RAG Compliance Engine** grounds every finding in specific regulatory controls (PCI‑DSS, HIPAA, FFIEC, NYDFS 500, SOX, CIS AWS) using pgvector similarity search and Voyage AI embeddings.

---

## Why This Project?

- Leverages CASB/CSPM domain expertise—highly relevant for cloud security roles (AWS/GCP).
- Demonstrates practical AI integration for security automation.
- Mirrors real-world enterprise security workflows (Symantec CASB patterns).
- **PostureIQ:** Shows how to combine CSPM with IAM analysis and LLMs to produce identity‑aware attack paths and prioritized remediation, similar to modern CSPM + CIEM patterns.
- **RAG Compliance Mapping:** Retrieves relevant regulatory controls via vector similarity search and injects them into Claude prompts, producing compliance‑aware enrichment with specific control citations and relevance scores.

---

## Tech Stack

| Layer       | Technology                                     |
|------------|-------------------------------------------------|
| Frontend   | Angular 21 (Standalone Components / Signals)    |
| Backend    | Spring Boot 3.2 / Java 17                       |
| Cloud SDK  | AWS SDK for Java v2                              |
| AI         | Claude API (Sonnet 4)                            |
| Embeddings | Voyage AI (voyage‑3, 1024‑dim)                  |
| Vector DB  | PostgreSQL 16 + pgvector (IVFFlat cosine index)  |
| Database   | PostgreSQL 16                                    |
| Chat-Ops   | OpenClaw Agent Gateway (Slack / Teams / Discord) |
| Deployment | Docker Compose · GitHub Actions CI/CD            |

---

## Core Features

### CSPM Scanner

- **AWS Configuration Scanning**
  Scan for 13+ security risk categories across S3, IAM, EC2, EBS, CloudTrail, and VPC. Supports real AWS credentials or realistic mock data for demos.
- **Expandable Data Table**
  Findings displayed in a high-density Material data table with expandable detail rows, severity/resource/search filters, and remediation status badges (REMEDIATED / FAILED / PARTIAL / OPEN). 3–5x more findings per screen than card-based layouts.
- **AI‑Powered Remediation**
  Claude generates fix snippets with AWS CLI commands, Terraform snippets, and step-by-step explanations for each finding.
- **Agentic Auto-Remediation**
  One-click auto-fix with an MCP tool registry (6 write tools: block S3 public access, restrict security groups, enable EBS encryption, enable CloudTrail, rotate access keys, delete unused credentials). Supports a human-in-the-loop approval workflow — planned actions are presented for review before execution. Real-time SSE streaming shows live progress with before/after state diffs.
- **Remediation Status Tracking**
  Findings persist remediation status (REMEDIATED / FAILED / PARTIAL) across page refreshes. Remediated findings are visually de-emphasized and can be filtered out. The correlation engine skips remediated findings so fixed issues no longer generate attack paths.
- **Compliance Dashboard**
  6 interactive charts (severity distribution, resource type breakdown, category doughnut, scan history trend, compliance score, remediation analytics). High-risk identity table with expandable details.
- **Historical Scan Tracking**
  Track security posture improvements over time with seeded historical data for demos.
- **Export Reports**
  Generate PDF reports (executive summary, findings table, remediation) and JSON exports for stakeholders.

### PostureIQ – Identity‑Aware Posture Extension

- **IAM Ingestion**
  Ingest IAM users, roles, groups, and attached policies from AWS.
- **IAM Risk Checks**
  Detect admin‑like policies, console users without MFA, dormant high‑privilege identities, and over‑permissive service accounts.
- **Correlation Engine (IAM + CSPM)**
  Tie IAM risks to CSPM findings to create attack‑path scenarios (e.g., "Role X with S3:*, three public S3 buckets with sensitive data").
- **AI Risk Narratives**
  Use Claude to generate human‑readable attack paths, business impact, and step‑by‑step remediation plans for each scenario.
- **Identity‑Centric Prioritization**
  Rank identities and misconfigurations by blast radius and exploitability, not just count of alerts. Risk scores exclude remediated findings for accurate prioritization.

### RAG Regulatory Compliance Engine

- **186 Curated Regulatory Controls**
  Pre‑embedded control chunks across 6 financial services frameworks: PCI‑DSS v4.0 (43), HIPAA Security Rule (25), FFIEC IT Handbook (35), 23 NYCRR 500 (20), SOX ITGC (15), CIS AWS Foundations v3.0 (48).
- **Vector Similarity Retrieval**
  pgvector cosine similarity search retrieves the top‑5 most relevant regulatory controls for each finding using Voyage AI embeddings (1024 dimensions, asymmetric document/query encoding).
- **Compliance‑Aware AI Enrichment**
  Retrieved regulatory context is injected into Claude prompts via RAG, producing regulatory analysis narratives and structured control violation mappings with relevance scores.
- **Compliance Summary Dashboard**
  Framework‑level violation counts, critical control citations, and risk levels displayed in a color‑coded grid on the PostureIQ results page.
- **Per‑Finding Compliance Badges**
  Each enriched finding shows inline framework badges (PCI‑DSS, HIPAA, FFIEC, etc.) with expandable details showing control ID, violation summary, and compliance‑specific remediation guidance.
- **Graceful Degradation**
  Without a Voyage API key, enrichment works exactly as before — mock responses include regulatory data for demo mode. If the embedding API is unavailable at runtime, the system logs a warning and continues without regulatory context.

### OpenClaw Chat-Ops Integration

- **Conversational Security from Slack, Teams, and Discord**
  Full OpenClaw skill suite with 8 commands: `scan`, `status`, `findings`, `remediate`, `fix`, `identities`, `compliance`, `report`. Talk to PostureIQ in natural language from any connected chat platform.
- **CI/CD Scanner**
  CLI scanner with JSON and SARIF output formats and configurable severity threshold gating for CI pipelines. Includes a GitHub Actions workflow template with SARIF upload to the GitHub Security tab.
- **Automated Alerts**
  Cron scripts for daily scan alert digests and compliance summary notifications via Slack/Teams.
- **Shared Auth Library**
  Reusable JWT authentication module with token caching and HTTP helpers for all skill scripts and cron jobs.

---

## Security Risks Detected

### Cloud / Configuration Risks (CSPM)

- Public S3 buckets
- Overly permissive IAM policies
- Unencrypted EBS volumes
- Open security groups (0.0.0.0/0)
- Missing MFA on root accounts
- Unused IAM credentials
- Unrotated access keys
- CloudTrail disabled
- Default VPC usage
- Missing encryption at rest

### Identity & Access Risks (PostureIQ)

- Admin‑like IAM roles and users (wildcard or full‑admin policies).
- Console users with high privileges and no MFA.
- Dormant high‑privilege accounts (unused for long periods).
- Service accounts with broad/wildcard access to critical services (e.g., S3, EC2, IAM).
- Identities that can modify or access misconfigured resources (e.g., public S3, open SGs).

### Correlated Attack‑Path Scenarios (PostureIQ)

Examples of scenarios the extension produces:

- Over‑privileged role + public S3 buckets → potential data exfiltration path.
- EC2 full‑access identity + SG with 0.0.0.0/0 on SSH/RDP → remote compromise and lateral movement.
- Unauthenticated API endpoint + identity with broad API Gateway permissions → deployment or abuse of insecure APIs.

### Regulatory Compliance Mappings (RAG)

Each correlated finding is mapped to specific regulatory controls:

- **PCI‑DSS:** Requirement 1 (network segmentation), Req 3 (stored data protection), Req 7 (least privilege), Req 8 (MFA/authentication), Req 10 (logging/monitoring).
- **HIPAA:** 164.312(a) access controls, 164.312(a)(2)(iv) encryption, 164.312(b) audit controls, 164.312(e) transmission security.
- **FFIEC:** Authentication controls, encryption requirements, network segmentation, logging/monitoring, vendor management.
- **NYDFS 500:** 500.07 access privileges, 500.12 multi‑factor authentication, 500.14 monitoring, 500.15 encryption of nonpublic information.
- **SOX:** ITGC access controls, segregation of duties, change management, audit logging.
- **CIS AWS:** IAM benchmarks, S3 Block Public Access, CloudTrail configuration, VPC/security group hardening.

---

## RAG Pipeline Architecture

```text
Finding detected
  → Build query string (severity + title + resource + description)
  → Generate embedding via Voyage AI (input_type: "query")
  → pgvector cosine similarity search (top-5, threshold > 0.3)
  → Inject retrieved regulatory chunks into Claude prompt
  → Claude returns REGULATORY_ANALYSIS + REGULATORY_MAPPINGS sections
  → Parse structured mappings (framework|controlId|title|violation|remediation|score)
  → Persist to regulatory_finding_mappings table (cascade via AiFindingDetails)
  → Return to frontend with compliance badges and detail panels
```

Key design decisions:
- **pgvector over dedicated vector DB** — regulatory corpus is small (~200 chunks), PostgreSQL handles it efficiently without adding infrastructure.
- **Voyage AI voyage‑3** — Anthropic‑recommended embedding model, 1024 dimensions, asymmetric document/query encoding for better retrieval quality.
- **Curated controls over raw PDF ingestion** — precision matters for compliance mapping; exact control citations are more valuable than fuzzy paragraph matches.
- **IVFFlat index (lists=20)** — optimal for small corpus, fast and simple.
- **Cache‑first pattern preserved** — regulatory mappings cascade‑persist with AiFindingDetails, so existing cache check avoids redundant API calls.

---

## Project Structure

```text
postureiq/
├── frontend/                         # Angular 21 application
│   ├── src/app/
│   │   ├── components/
│   │   │   ├── scanner/              # CSPM scan trigger + expandable data table
│   │   │   ├── finding-card/         # Reusable finding card (used by PostureIQ)
│   │   │   ├── dashboard/            # Charts, trends, remediation analytics
│   │   │   ├── postureiq/            # IAM scan, correlation, AI enrichment, compliance UI
│   │   │   ├── remediation-dialog/   # AI remediation guidance dialog
│   │   │   ├── auto-remediation-dialog/  # Agentic auto-fix with approval + SSE progress
│   │   │   └── login/                # Authentication with demo quick-login
│   │   ├── services/                 # API client services
│   │   └── models/                   # TypeScript interfaces
│   └── angular.json
│
├── backend/                          # Spring Boot 3.2 application
│   ├── src/main/java/com/cspm/
│   │   ├── controller/               # REST endpoints (Auth, Scan, PostureIQ, Remediation, Dashboard)
│   │   ├── service/
│   │   │   ├── ClaudeService          # AI enrichment + RAG prompt injection
│   │   │   ├── AgenticRemediationService  # Auto-remediation orchestration + approval workflow
│   │   │   ├── AgenticClaudeClient    # Anthropic Messages API with tool-use support
│   │   │   ├── McpToolRegistry        # MCP-style remediation tool registry (6 write + 3 read tools)
│   │   │   ├── RemediationProgressEmitter  # SSE emitter management for real-time progress
│   │   │   ├── EmbeddingService       # Voyage AI embeddings
│   │   │   ├── RegulatoryIngestionService  # Startup chunk loading + embedding
│   │   │   ├── RegulatoryRetrievalService  # pgvector similarity search
│   │   │   ├── CorrelationService     # IAM + CSPM correlation (remediation-aware)
│   │   │   ├── IamRiskService         # IAM risk scoring (excludes remediated findings)
│   │   │   ├── IamIngestionService    # AWS IAM ingestion
│   │   │   └── ScannerService         # CSPM scanning
│   │   ├── model/                     # JPA entities (Finding, AiFindingDetails, RegulatoryChunk, RemediationAudit, etc.)
│   │   └── repository/               # Spring Data JPA repositories
│   ├── src/main/resources/
│   │   ├── regulatory/               # 6 JSON files (186 curated controls)
│   │   └── application.yml
│   └── pom.xml
│
├── openclaw/                         # OpenClaw chat-ops skill suite
│   └── skills/postureiq/
│       ├── SKILL.md                   # Manifest with env requirements
│       ├── lib/                       # Shared auth + HTTP helpers
│       ├── commands/                  # 8 chat command scripts
│       ├── ci/                        # CI/CD scanner (JSON/SARIF output)
│       ├── cron/                      # Daily scan alerts + compliance digests
│       └── docs/                      # API reference for agent context
│
├── db/init/                          # PostgreSQL init scripts
│   └── 01-extensions.sql             # CREATE EXTENSION vector
│
├── docker-compose.yml                # pgvector/pgvector:pg16 + backend + frontend
├── docs/                             # Architecture docs, changelog, roadmap
└── README.md
```

---

## Quick Start

### Docker (Recommended)

The fastest way to run the full stack:

```bash
docker compose up --build
```

This starts PostgreSQL (with pgvector), the Spring Boot backend, and the Angular frontend:

- Frontend: http://localhost:4200
- Backend API: http://localhost:8080
- Health check: http://localhost:8080/actuator/health

To pass API keys or override defaults, create a `.env` file in the project root:

```env
ANTHROPIC_API_KEY=your-key-here
VOYAGE_API_KEY=your-voyage-key-here
JWT_SECRET=your-secret-here
```

To stop and clean up:

```bash
docker compose down        # stop containers
docker compose down -v     # stop and remove data volume
```

### Local Development

#### Prerequisites

- Node.js 22+ and npm
- Java 17+
- Angular CLI: `npm install -g @angular/cli`

#### Running the Application

```bash
# 1. Start the Backend
cd backend
./mvnw spring-boot:run
# Backend runs at http://localhost:8080

# 2. Start the Frontend (in a new terminal)
cd frontend
npm install
ng serve
# Frontend runs at http://localhost:4200
```

---

## Using the Application

- Open http://localhost:4200 in your browser.
- Log in with demo credentials: `demo` / `demo1234`.
- Click **"Run Security Scan"** to analyze mock or real AWS infrastructure.
- View findings in the expandable data table — filter by severity, resource type, remediation status, or search text.
- Click the action menu (three dots) on any finding:
  - **Remediate** — get Claude‑powered fix with AWS CLI commands and Terraform snippets.
  - **Auto-Fix** — one-click remediation with approval workflow and live SSE progress streaming.
- Navigate to **PostureIQ** to run the identity‑aware analysis pipeline:
  1. **IAM Scan** — discovers users, roles, policies, and IAM risks.
  2. **Correlate** — cross‑references IAM findings with CSPM infrastructure findings (skips remediated issues).
  3. **AI Enrich** — generates attack path narratives with regulatory compliance mappings.
- View the **Compliance Summary** grid showing violations across PCI‑DSS, HIPAA, FFIEC, NYDFS 500, SOX, and CIS AWS.
- Expand any finding to see compliance badges, regulatory analysis, and per‑control violation details with relevance scores.
- Check the **Dashboard** for charts, trends, high-risk identities, and remediation analytics.
- Use **OpenClaw** to interact via Slack/Teams: just type `postureiq scan`, `postureiq findings`, `postureiq fix`, etc.

---

## API Endpoints

### Authentication
| Method | Endpoint                              | Description                              |
|--------|---------------------------------------|------------------------------------------|
| POST   | `/api/auth/register`                 | Register new user (username, email, password) |
| POST   | `/api/auth/login`                    | Authenticate and receive JWT token       |

### CSPM Scanning
| Method | Endpoint                              | Description                              |
|--------|---------------------------------------|------------------------------------------|
| POST   | `/api/scan`                          | Trigger core CSPM security scan          |
| GET    | `/api/scan/{id}`                     | Get scan results by ID                   |
| GET    | `/api/scans`                         | List all scan results                    |
| POST   | `/api/remediate`                     | Get AI-powered fix for a finding         |
| GET    | `/api/scan/{id}/report`              | Download PDF report                      |
| GET    | `/api/scan/{id}/export`              | Download JSON export                     |

### PostureIQ (IAM + Correlation + AI)
| Method | Endpoint                              | Description                              |
|--------|---------------------------------------|------------------------------------------|
| POST   | `/api/scan/iam`                      | Run IAM scan (PostureIQ)                 |
| POST   | `/api/scan/correlate`                | Correlate IAM + CSPM findings            |
| POST   | `/api/scan/{id}/enrich`              | AI enrichment with RAG compliance mapping|
| GET    | `/api/identities/high-risk`          | List high-risk identities                |
| GET    | `/api/scan/{id}/compliance-summary`  | Regulatory compliance summary by scan    |
| GET    | `/api/findings/{id}/compliance`      | Per-finding regulatory control mappings  |
| POST   | `/api/admin/regulatory/ingest`       | Re-ingest regulatory control data        |

### Agentic Auto-Remediation
| Method | Endpoint                              | Description                              |
|--------|---------------------------------------|------------------------------------------|
| POST   | `/api/remediate/auto`                | Start auto-remediation (with optional approval gate) |
| POST   | `/api/remediate/approve`             | Approve and execute planned remediation actions |
| GET    | `/api/remediate/stream/{sessionId}`  | SSE stream for real-time remediation progress |
| GET    | `/api/remediate/audit/{findingId}`   | Get remediation audit trail for a finding |
| GET    | `/api/remediate/audit/session/{id}`  | Get remediation audit trail by session   |

### Dashboard
| Method | Endpoint                              | Description                              |
|--------|---------------------------------------|------------------------------------------|
| GET    | `/api/dashboard/stats`               | Aggregated dashboard stats + remediation analytics |

---

## Claude API & Voyage AI Integration

To enable real AI calls, set the following environment variables:

```bash
export ANTHROPIC_API_KEY=your-api-key-here    # Claude API for enrichment
export VOYAGE_API_KEY=your-voyage-key-here     # Voyage AI for RAG embeddings
```

- **Without API keys:** The application uses pre-built mock responses that include regulatory compliance data — fully functional for demos.
- **With ANTHROPIC_API_KEY only:** Live Claude enrichment without RAG (no regulatory context in prompts).
- **With both keys:** Full RAG pipeline — regulatory chunks are embedded on startup, similarity search retrieves relevant controls, and Claude produces compliance‑aware analysis.

---

## Demo Highlights

- **Live AWS Scanning:** Run real-time scans during interviews.
- **High-Density Findings Table:** Expandable data table with severity badges, filters, and inline detail rows.
- **AI Remediation:** Show Claude generating security fixes with AWS CLI and Terraform snippets.
- **Agentic Auto-Fix:** One-click remediation with approval workflow, live SSE progress, and before/after state diffs.
- **Remediation Tracking:** Findings show REMEDIATED/FAILED/PARTIAL status; fixed issues drop out of correlation and risk scores.
- **Identity-Aware Attack Paths (PostureIQ):** Demonstrate how misconfigs and IAM combine into real-world exploit scenarios.
- **RAG Regulatory Compliance:** Show how findings map to specific PCI‑DSS, HIPAA, FFIEC, NYDFS 500, SOX, and CIS AWS controls with relevance scores.
- **Compliance Summary Dashboard:** Framework‑level violation grid with critical control citations.
- **Chat-Ops via OpenClaw:** Run scans, view findings, and trigger fixes from Slack or Teams in natural language.
- **CI/CD Integration:** SARIF output for GitHub Security tab, threshold gating for pipelines.
- **Graceful Degradation:** Works fully in demo mode without any API keys.

---

## Future Enhancements

- GCP and Azure support (multi-cloud coverage).
- Terraform/CloudFormation scanning (shift-left security).
- Custom policy rules engine.
- Multi-account scanning.
- Deeper CIEM‑style analysis (per‑resource access graph).
- SOC/SIEM integration for sending high‑risk identity scenarios into alerting pipelines.
- Scheduled recurring scans for continuous monitoring.
