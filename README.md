```markdown
# Cloud Security Posture Scanner (CSPM Mini‑Tool) + PostureIQ Extension

Scan AWS configurations and infrastructure code for security risks (public S3 buckets, weak IAM policies, exposed secrets), **correlate them with IAM entitlements**, and get AI‑powered attack‑path narratives and remediation suggestions via Claude.

The base **CSPM Scanner** focuses on cloud misconfigurations and compliance reporting.  
The new **PostureIQ** extension adds an identity‑aware risk layer on top, so you see not just *what* is misconfigured, but *who* can actually exploit it and what to fix first.

---

## Why This Project?

- Leverages CASB/CSPM domain expertise—highly relevant for cloud security roles (AWS/GCP).
- Demonstrates practical AI integration for security automation.
- Mirrors real-world enterprise security workflows (Symantec CASB patterns).
- **New with PostureIQ:** Shows how to combine CSPM with IAM analysis and LLMs to produce identity‑aware attack paths and prioritized remediation, similar to modern CSPM + CIEM patterns.

---

## Tech Stack

| Layer       | Technology                |
|------------|---------------------------|
| Frontend   | Angular 17+               |
| Backend    | Spring Boot 3.x           |
| Cloud SDK  | AWS SDK for Java          |
| AI         | Claude API                |
| Database   | PostgreSQL                |
| Deployment | Vercel (UI) + Railway (API)|

---

## Core Features

### CSPM Scanner (Existing)

- **AWS Configuration Scanning**  
  Input AWS credentials/config files to scan for Top 10 cloud security risks.
- **AI‑Powered Remediation**  
  Claude generates fix snippets (e.g., “Secure this S3 bucket policy”).
- **Compliance Dashboard**  
  Visual charts showing risk distribution and compliance status.
- **Historical Scan Tracking**  
  Track security posture improvements over time.
- **Export Reports**  
  Generate PDF/JSON reports for stakeholders.

### PostureIQ – Identity‑Aware Posture Extension (New)

- **IAM Ingestion**  
  Ingest IAM users, roles, groups, and attached policies from AWS.
- **IAM Risk Checks**  
  Detect admin‑like policies, console users without MFA, dormant high‑privilege identities, and over‑permissive service accounts.
- **Correlation Engine (IAM + CSPM)**  
  Tie IAM risks to CSPM findings to create attack‑path scenarios (e.g., “Role X with S3:*, three public S3 buckets with sensitive data”).
- **AI Risk Narratives**  
  Use Claude to generate human‑readable attack paths, business impact, and step‑by‑step remediation plans for each scenario.
- **Identity‑Centric Prioritization**  
  Rank identities and misconfigurations by blast radius and exploitability, not just count of alerts.

---

## Security Risks Detected

### Cloud / Configuration Risks (Existing CSPM)

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

---

## Skills Learning Path

### Phase 1: Foundations (Week 1)

#### Angular (Frontend)

- Angular CLI setup and project structure  
- Components, modules, and routing  
- Reactive forms for credential input  
- HTTP client for API calls  
- Angular Material for UI components  

Resources:  
- [Angular Official Tutorial](https://angular.io/tutorial)  
- [Angular Material Docs](https://material.angular.io/)

#### Spring Boot (Backend)

- Spring Initializr project setup  
- REST controller basics  
- Service layer architecture  
- Exception handling  
- Configuration management (`application.yml`)  

Resources:  
- [Spring Boot Getting Started](https://spring.io/guides/gs/spring-boot/)  
- [Building REST Services](https://spring.io/guides/tutorials/rest/)

---

### Phase 2: Cloud & Security (Week 2)

#### AWS SDK & Security

- AWS SDK for Java setup  
- IAM programmatic access  
- S3 bucket policy inspection  
- EC2 security group analysis  
- AWS Config rules understanding  

Resources:  
- [AWS SDK for Java Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)  
- [AWS Security Best Practices](https://docs.aws.amazon.com/wellarchitected/latest/security-pillar/)  
- [CIS AWS Benchmarks](https://www.cisecurity.org/benchmark/amazon_web_services)

#### Claude API Integration

- Anthropic API setup and authentication  
- Prompt engineering for security analysis  
- Structured output for remediation code  
- Rate limiting and error handling  

Resources:  
- [Claude API Documentation](https://docs.anthropic.com/)  
- [Prompt Engineering Guide](https://docs.anthropic.com/claude/docs/prompt-engineering)

---

### Phase 3: Polish & Deploy (Week 3)

#### Database & Persistence

- PostgreSQL setup  
- Spring Data JPA entities  
- Scan history storage  
- Query optimization  

#### Deployment

- Vercel deployment for Angular  
- Railway deployment for Spring Boot  
- Environment variable management  
- CI/CD pipeline (GitHub Actions)  

#### Security Hardening

- JWT authentication  
- Credential encryption at rest  
- Input validation  
- CORS configuration  

---

## PostureIQ Extension – Design Overview

### Data Model Additions

- `IamIdentity`  
  - user/role/group, console access, MFA flag, last used, tags.
- `IamPolicy`  
  - policy document, attached identities, flags for admin‑like/wildcard actions.
- Extended `Finding`  
  - `category`: `CONFIG`, `IAM`, `CORRELATED`  
  - optional primary identity for correlated findings.
- Optional `AiFindingDetails`  
  - final severity, attack‑path narrative, business impact, remediation steps.

### Backend Modules (New)

- `service/iam/`  
  - IAM ingestion (list users/roles/groups, fetch policies).  
  - IAM rules (admin‑like, no MFA, dormant accounts, etc.).
- `service/correlation/`  
  - Joins IAM + CSPM findings into correlated scenarios.
- `service/claude/` (extended)  
  - Existing remediation prompts.  
  - New prompts for attack‑path narratives and prioritized remediation.

### New API Endpoints

- `POST /api/scan/iam`  
  Run IAM‑only scan, store IAM entities and IAM findings.

- `POST /api/scan/correlate`  
  Run correlation between IAM and existing CSPM findings to generate correlated findings.

- `POST /api/scan/{id}/enrich`  
  Use Claude to enrich correlated findings with attack paths and remediation.

- `GET /api/identities/high-risk`  
  List identities sorted by combined risk (number & severity of correlated findings).

---

## Frontend (Angular) Enhancements

Existing components:

- `scanner/` – trigger scans and show basic results.  
- `dashboard/` – compliance charts and risk overview.  
- `reports/` – export PDF/JSON reports.

New UI capabilities:

- Dashboard widgets:
  - “Top 5 high‑risk identities” (PostureIQ).  
  - Breakdown of CONFIG vs IAM vs CORRELATED findings.
- Findings view:
  - Filter by category (CONFIG/IAM/CORRELATED).  
  - For correlated findings, show linked identity + misconfigured resources + AI summary.
- Reports:
  - New section: “Identity‑Aware Risk Stories” summarizing key attack paths and recommended actions.

---

## Project Structure

```text
cspm-scanner/
├── frontend/                 # Angular application
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/
│   │   │   │   ├── scanner/
│   │   │   │   ├── dashboard/
│   │   │   │   └── reports/
│   │   │   ├── services/
│   │   │   └── models/
│   │   └── environments/
│   └── angular.json
│
├── backend/                  # Spring Boot application
│   ├── src/main/java/
│   │   └── com/cspm/
│   │       ├── controller/
│   │       ├── service/
│   │       │   ├── scanner/
│   │       │   ├── claude/
│   │       │   ├── iam/          # NEW – IAM ingestion & rules
│   │       │   └── correlation/  # NEW – IAM + CSPM correlation
│   │       ├── model/
│   │       │   ├── iam/          # NEW – IAM entities
│   │       │   └── correlation/  # NEW – correlated findings (optional)
│   │       └── config/
│   └── pom.xml
│
└── README.md
```

---

## Quick Start

### Docker (Recommended)

The fastest way to run the full stack:

```bash
docker compose up --build
```

This starts PostgreSQL, the Spring Boot backend, and the Angular frontend:

- Frontend: http://localhost:4200  
- Backend API: http://localhost:8080  
- Health check: http://localhost:8080/actuator/health  

To pass API keys or override defaults, create a `.env` file in the project root:

```env
ANTHROPIC_API_KEY=your-key-here
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
- Click **“Run Security Scan”** to analyze mock or real AWS infrastructure.  
- View security findings with severity levels (HIGH/MEDIUM/LOW).  
- Click **“Get Fix”** on any finding to get Claude‑powered remediation.  
- (With PostureIQ) open the Identity view to see high‑risk identities and correlated attack‑path scenarios.

---

## API Endpoints

| Method | Endpoint               | Description                          |
|--------|------------------------|--------------------------------------|
| POST   | `/api/scan`           | Trigger core CSPM security scan      |
| GET    | `/api/scan/{id}`      | Get scan results by ID               |
| POST   | `/api/remediate`      | Get AI-powered fix for a finding     |
| POST   | `/api/scan/iam`       | Run IAM scan (PostureIQ)             |
| POST   | `/api/scan/correlate` | Correlate IAM + CSPM findings        |
| POST   | `/api/scan/{id}/enrich`| AI enrichment for correlated findings|
| GET    | `/api/identities/high-risk` | List high-risk identities       |

---

## Claude API Integration

To enable real Claude API calls for remediation and risk narratives, set the `ANTHROPIC_API_KEY` environment variable:

```bash
export ANTHROPIC_API_KEY=your-api-key-here
```

If no API key is set, the application uses pre-built mock remediation responses.

---

## Demo Highlights

- **Live AWS Scanning:** Run real-time scans during interviews.  
- **AI Remediation:** Show Claude generating security fixes.  
- **Identity-Aware Attack Paths (PostureIQ):** Demonstrate how misconfigs and IAM combine into real-world exploit scenarios.  
- **Compliance Metrics:** Display risk reduction over time across scans.

---

## Future Enhancements

- GCP and Azure support.  
- Terraform/CloudFormation scanning.  
- Slack/Teams notifications.  
- Custom policy rules engine.  
- Multi-account scanning.  
- Deeper CIEM‑style analysis (per‑resource access graph).  
- SOC/SIEM integration for sending high‑risk identity scenarios into alerting pipelines.
```