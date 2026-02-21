# PostureIQ – Identity-Aware Cloud Security Posture Scanner

## Complete Architecture & Talking Points

---

## 1. What Is This Project?

PostureIQ is a **full-stack Cloud Security Posture Management (CSPM) tool** that scans real AWS infrastructure for security misconfigurations, correlates them with IAM (Identity and Access Management) entitlements, and uses **Claude AI** to generate human-readable attack-path narratives and remediation plans.

Think of it as combining three enterprise security product categories into one:

| Category | What It Does | Industry Examples |
|----------|-------------|-------------------|
| **CSPM** | Finds cloud misconfigurations (public S3, open security groups) | Prisma Cloud, Wiz, AWS Config |
| **CIEM** | Analyzes IAM identities for excessive permissions | CrowdStrike, Ermetic, CloudKnox |
| **AI Copilot** | Generates attack narratives and remediation steps | GitHub Copilot for Security, Wiz AI |

PostureIQ merges all three — it doesn't just tell you *what's wrong*, it tells you *who can exploit it* and *exactly how to fix it*.

---

## 2. Tech Stack

```
┌──────────────────────────────────────────────────────────────┐
│  FRONTEND          Angular 21 · Material Design · Chart.js  │
├──────────────────────────────────────────────────────────────┤
│  REVERSE PROXY     Nginx Alpine (SPA routing + API proxy)   │
├──────────────────────────────────────────────────────────────┤
│  BACKEND           Spring Boot 3.2 · Java 17 · Spring Data  │
├──────────────────────────────────────────────────────────────┤
│  DATABASE          PostgreSQL 16 (prod) · H2 (dev)          │
├──────────────────────────────────────────────────────────────┤
│  CLOUD SDK         AWS SDK v2 (S3, IAM, EC2, EBS, STS)      │
├──────────────────────────────────────────────────────────────┤
│  AI ENGINE         Claude Sonnet 4 via Anthropic REST API   │
├──────────────────────────────────────────────────────────────┤
│  INFRASTRUCTURE    Docker Compose · GitHub Actions CI/CD    │
└──────────────────────────────────────────────────────────────┘
```

### Why These Choices?

- **Angular 21** — Latest version with standalone components and signals API. No NgModules, cleaner reactive state management. Material Design gives a professional enterprise look out of the box.
- **Spring Boot 3.2** — Industry standard for enterprise Java backends. Spring Security + JPA + AWS SDK integrations are battle-tested. `@ConditionalOnProperty` lets us swap real AWS for mock services with a single env var.
- **PostgreSQL** — Production-grade relational database. TEXT columns for large policy documents and AI narratives. JPA/Hibernate handles schema evolution.
- **AWS SDK v2** — Direct programmatic access to AWS APIs. No Terraform or CloudFormation dependencies — reads live infrastructure state.
- **Claude API** — Generates context-aware security analysis. The prompt engineering extracts structured attack paths, business impact, and step-by-step remediation.
- **Docker Compose** — One command (`docker compose up`) spins up the entire stack: database, backend, frontend, all wired together with health checks.

---

## 3. Architecture Overview

### High-Level Data Flow

```
 Browser (User)
     │
     ▼
 ┌─────────┐    Static files     ┌─────────────────────────┐
 │  Nginx   │ ──────────────────► │   Angular SPA           │
 │  :4200   │                     │   (Login, Scanner,      │
 │          │    /api/* proxy     │    PostureIQ, Dashboard) │
 │          │ ──────────────────► └─────────────────────────┘
 └────┬─────┘
      │ HTTP
      ▼
 ┌──────────────────────────────────────────────────────┐
 │  Spring Boot Backend (:8080)                         │
 │                                                      │
 │  ┌─────────────┐  ┌──────────────┐  ┌────────────┐ │
 │  │ Auth        │  │ Scan         │  │ PostureIQ  │ │
 │  │ Controller  │  │ Controller   │  │ Controller │ │
 │  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘ │
 │         │                │                 │        │
 │  ┌──────▼──────┐  ┌──────▼───────┐  ┌─────▼──────┐ │
 │  │ JWT Service │  │ AWS Scanner  │  │ IAM Risk   │ │
 │  │             │  │ Service      │  │ Service    │ │
 │  └─────────────┘  └──────┬───────┘  └─────┬──────┘ │
 │                          │                 │        │
 │                   ┌──────▼───────┐  ┌──────▼──────┐ │
 │                   │ Claude       │  │ Correlation │ │
 │                   │ Service      │  │ Service     │ │
 │                   └──────┬───────┘  └─────────────┘ │
 │                          │                          │
 │  ┌───────────────────────▼──────────────────────┐   │
 │  │     Spring Data JPA Repositories             │   │
 │  └───────────────────────┬──────────────────────┘   │
 └──────────────────────────┼──────────────────────────┘
                            │
           ┌────────────────┼────────────────┐
           ▼                ▼                ▼
    ┌────────────┐   ┌────────────┐   ┌────────────┐
    │ PostgreSQL │   │ AWS Cloud  │   │ Claude API │
    │ (cspmdb)   │   │ S3/IAM/EC2 │   │ (Anthropic)│
    └────────────┘   └────────────┘   └────────────┘
```

### Layer-by-Layer Breakdown

#### Layer 1: Frontend (Angular 21)

The frontend is a **single-page application** served by Nginx. It uses Angular's latest **standalone components** (no NgModules) and the **signals API** for reactive state management.

**Four main pages:**

| Page | Route | Purpose |
|------|-------|---------|
| Login | `/login` | Authentication with demo credentials quick-login |
| Scanner | `/` | Trigger AWS scans, view/filter/export findings |
| PostureIQ | `/postureiq` | IAM analysis + correlation + AI enrichment pipeline |
| Dashboard | `/dashboard` | Charts, trends, compliance score, high-risk identities |

**Key architectural decisions:**
- **Standalone components** — Each component declares its own imports. No shared module tree to manage. Faster compilation, clearer dependencies.
- **Signals** — `signal()` and `computed()` replace BehaviorSubjects for component state. More predictable, fewer subscriptions to manage.
- **Auth interceptor** — Automatically injects JWT Bearer token into every API request. Catches 401s and redirects to login.
- **Route guards** — `authGuard` protects all routes except `/login`. Checks token expiry by decoding the JWT payload client-side.

#### Layer 2: Nginx (Reverse Proxy)

Nginx serves two roles:
1. **Static file server** — Serves the compiled Angular build with `try_files $uri /index.html` for SPA routing
2. **API reverse proxy** — Forwards `/api/*` and `/actuator/*` to the backend container on port 8080

This means in production, the browser only talks to one origin (port 4200). No CORS issues, clean URL structure.

#### Layer 3: Backend (Spring Boot 3.2)

The backend follows a clean **Controller → Service → Repository** architecture.

**Controllers (4):**

| Controller | Base Path | Endpoints | Purpose |
|-----------|-----------|-----------|---------|
| `AuthController` | `/api/auth` | POST /register, POST /login | User authentication with rate limiting |
| `ScanController` | `/api` | POST /scan, GET /scan/{id}, POST /remediate, GET /report | CSPM scanning + remediation |
| `PostureIqController` | `/api` | POST /scan/iam, POST /scan/correlate, POST /scan/{id}/enrich, GET /identities/high-risk | IAM analysis pipeline |
| `DashboardController` | `/api/dashboard` | GET /stats | Aggregated metrics |

**Services (10):**

| Service | Responsibility |
|---------|---------------|
| `AwsScannerService` | Scans S3, IAM, EC2, EBS via AWS SDK. Creates findings with severity. |
| `MockAwsScanner` | Returns realistic mock findings when AWS is disabled. |
| `AwsIamIngestionService` | Ingests IAM users, roles, groups, and their attached policies. |
| `MockIamIngestionService` | Returns 5 mock identities for demo mode. |
| `IamRiskService` | Analyzes identities for: admin-like policies, missing MFA, dormant accounts, wildcard access. |
| `CorrelationService` | Cross-references IAM + CSPM findings to produce attack-path scenarios. |
| `ClaudeService` | Calls Claude API for remediation text and attack-path narratives. Falls back to templates. |
| `ReportService` | Generates PDF reports with OpenPDF (executive summary, findings table, remediation). |
| `JwtService` | JWT token generation, validation, and claim extraction using HMAC-SHA. |
| `DemoDataSeeder` | Seeds demo user and historical scan data on startup for demo environments. |

**The Conditional Bean Pattern:**

A key architectural choice — the backend uses `@ConditionalOnProperty` to swap implementations:

```java
@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "true")
public class AwsScannerService implements ScannerService { ... }

@ConditionalOnProperty(name = "aws.scanner.enabled", havingValue = "false", matchIfMissing = true)
public class MockAwsScanner implements ScannerService { ... }
```

Set `AWS_SCANNER_ENABLED=true` → scans your real AWS account.
Set `AWS_SCANNER_ENABLED=false` (or omit it) → uses realistic mock data for demos.

Same pattern for `IamIngestionService`. This means the entire app works perfectly as a demo without any AWS credentials.

#### Layer 4: Database (PostgreSQL 16)

Seven tables managed by JPA/Hibernate with `ddl-auto: update`:

```
┌─────────────────┐       ┌─────────────────┐
│     users        │       │  scan_results    │
│─────────────────│       │─────────────────│
│ id (PK)          │       │ scan_id (PK)     │
│ username (unique)│       │ timestamp        │
│ password (bcrypt)│       │ status           │
│ email (unique)   │       │ total_findings   │
│ role             │       │ high/med/low     │
└─────────────────┘       └────────┬─────────┘
                                   │ 1:N
                          ┌────────▼─────────┐
                          │    findings       │
                          │─────────────────│
                          │ id (PK)           │
                          │ resource_type     │
                          │ severity          │
                          │ category          │◄── CONFIG | IAM | CORRELATED
                          │ primary_identity  │
                          │ scan_id (FK)      │
                          └────────┬─────────┘
                                   │ 1:1
                          ┌────────▼─────────┐
                          │ ai_finding_details│
                          │─────────────────│
                          │ id (PK, auto)     │
                          │ finding_id (FK)   │
                          │ final_severity    │
                          │ attack_narrative  │◄── TEXT (Claude AI output)
                          │ business_impact   │◄── TEXT
                          │ remediation_steps │◄── TEXT
                          └──────────────────┘

┌─────────────────┐    N:M    ┌──────────────────┐
│ iam_identities   │◄────────►│  iam_policies     │
│─────────────────│  (join)   │──────────────────│
│ id (PK)          │           │ id (PK)           │
│ identity_type    │           │ policy_name       │
│ name, arn        │           │ policy_document   │◄── TEXT (full JSON)
│ console_access   │           │ is_admin_like     │
│ mfa_enabled      │           │ has_wildcard      │
│ last_used        │           └──────────────────┘
└─────────────────┘
```

#### Layer 5: External Integrations

**AWS SDK v2** — Four clients configured in `AwsConfig.java`:

| Client | Region | What It Scans |
|--------|--------|---------------|
| `S3Client` | Configured region | Bucket policies, public access blocks, encryption |
| `IamClient` | `AWS_GLOBAL` | Users, roles, groups, policies, MFA, access keys |
| `Ec2Client` | Configured region | Security group ingress/egress rules |
| (implicit) | Configured region | EBS volume encryption status |

**Claude API** — REST integration with `claude-sonnet-4-20250514`:
- **Remediation**: Given a finding → generates AWS CLI commands, Terraform snippets, and explanations
- **Attack Path Narratives**: Given correlated findings + identity → generates step-by-step exploitation scenario, business impact, and prioritized fix
- **Fallback**: If no API key is set, returns pre-built templates for each resource type

---

## 4. The Five Core Workflows

### Workflow 1: User Authentication

```
Browser → POST /api/auth/register (username, email, password)
       → Backend validates, BCrypt-hashes password, saves to users table
       → JwtService.generateToken() → signs JWT with HMAC-SHA
       → Returns { token, username, role }
       → Frontend stores token in localStorage
       → authInterceptor adds "Bearer {token}" to all subsequent requests
       → JwtAuthenticationFilter validates on every request
```

**Security features:**
- BCrypt password hashing (salt rounds built-in)
- Rate limiting: 5 login attempts per 60 seconds per username
- JWT expiry: 24 hours (configurable)
- Stateless sessions (no server-side session storage)
- 401 auto-logout on the frontend

### Workflow 2: AWS Security Scanning (CSPM)

```
User clicks "Run Security Scan"
  → POST /api/scan
  → AwsScannerService.runScan()
      ├── scanS3Buckets()
      │   ├── List all buckets
      │   ├── Check GetPublicAccessBlock → missing = HIGH
      │   ├── Check GetBucketAcl → public grants = HIGH
      │   └── Check encryption config → missing = MEDIUM
      │
      ├── scanIamPolicies()
      │   ├── List all policies → check for Action:* + Resource:* = HIGH
      │   ├── List users → check MFA status = MEDIUM
      │   └── List access keys → check rotation >90 days = MEDIUM
      │
      ├── scanSecurityGroups()
      │   ├── SSH (port 22) from 0.0.0.0/0 = HIGH
      │   ├── RDP (port 3389) from 0.0.0.0/0 = HIGH
      │   └── All traffic egress to 0.0.0.0/0 = MEDIUM
      │
      └── scanEbsVolumes()
          └── Unencrypted volumes = MEDIUM

  → Creates ScanResult + List<Finding>
  → Saves to database
  → Returns to frontend with severity counts
```

**10 security risk categories:**

| # | Risk | Severity | Resource |
|---|------|----------|----------|
| 1 | Public S3 buckets | HIGH | S3 |
| 2 | S3 missing encryption | MEDIUM | S3 |
| 3 | Admin-like IAM policies (Action:*/Resource:*) | HIGH | IAM |
| 4 | Users without MFA | MEDIUM | IAM |
| 5 | Unrotated access keys (>90 days) | MEDIUM | IAM |
| 6 | SSH open to internet (0.0.0.0/0:22) | HIGH | EC2 |
| 7 | RDP open to internet (0.0.0.0/0:3389) | HIGH | EC2 |
| 8 | Overly permissive egress rules | MEDIUM | EC2 |
| 9 | Unencrypted EBS volumes | MEDIUM | EBS |
| 10 | Default VPC usage | LOW | EC2 |

### Workflow 3: PostureIQ – IAM Risk Analysis

```
Step 1: IAM Scan
  → POST /api/scan/iam
  → AwsIamIngestionService.ingestIdentities()
      ├── listUsers() → for each: check console access, MFA, list attached policies
      ├── listRoles() → for each: list attached policies
      └── listGroups() → (future)
  → IamRiskService.runIamScan()
      ├── Check 1: Admin-like policies (Action:* or full-admin) → HIGH
      ├── Check 2: Console access without MFA → HIGH
      ├── Check 3: Dormant high-privilege (>90 days unused) → MEDIUM
      └── Check 4: Wildcard service access on roles → MEDIUM
  → Returns ScanResult with IAM findings
```

### Workflow 4: PostureIQ – Correlation Engine

```
Step 2: Correlate
  → POST /api/scan/correlate
  → CorrelationService.correlate()
      ├── Fetch latest CSPM scan findings (category = CONFIG)
      ├── Fetch latest IAM scan findings (category = IAM)
      │
      ├── Pattern A: S3 exposure + S3 access permissions
      │   → "Data exfiltration path" → CRITICAL
      │
      ├── Pattern B: Open security group + EC2 access permissions
      │   → "Remote compromise and lateral movement" → HIGH
      │
      └── Pattern C: Admin identity + any HIGH config finding
          → "Broad exploitation risk via over-privileged identity" → HIGH
      │
  → Creates CORRELATED findings linking identity ARN to misconfigured resource
  → Returns ScanResult with correlated findings
```

**Why correlation matters:**

Without correlation, a security team sees two separate alerts:
- "S3 bucket is public" (CSPM finding)
- "Role X has S3:* permissions" (IAM finding)

With correlation, they see one **actionable scenario**:
- "Role X with S3:* can access 3 public S3 buckets containing sensitive data → potential data exfiltration path" (CRITICAL)

This is exactly how modern CSPM+CIEM tools like Wiz and Prisma Cloud work — identity-aware risk prioritization.

### Workflow 5: PostureIQ – AI Enrichment

```
Step 3: AI Enrich
  → POST /api/scan/{scanId}/enrich
  → For each CORRELATED finding:
      → ClaudeService.enrichFinding(finding)
          → Builds prompt with:
              - Finding title, description, severity
              - Associated identity (from primaryIdentityArn)
              - Related findings in the same scan
          → Claude returns structured response:
              ┌─────────────────────────────────────────────────┐
              │ ATTACK_PATH:                                     │
              │ 1. Attacker compromises Role X credentials       │
              │ 2. Uses S3:* permissions to list all buckets     │
              │ 3. Identifies 3 public buckets with PII data     │
              │ 4. Exfiltrates data via S3:GetObject              │
              │                                                   │
              │ BUSINESS_IMPACT:                                  │
              │ - PII exposure affecting ~50K customer records    │
              │ - Regulatory fines (GDPR/CCPA)                   │
              │ - Reputational damage                             │
              │                                                   │
              │ REMEDIATION_STEPS:                                │
              │ 1. Restrict Role X to specific bucket ARNs       │
              │ 2. Enable S3 Block Public Access                  │
              │ 3. Enable CloudTrail logging for S3 operations   │
              │ 4. Implement least-privilege with conditions      │
              └─────────────────────────────────────────────────┘
      → Parsed into AiFindingDetails entity and saved
  → Returns list of enriched details for frontend display
```

---

## 5. Security Architecture

### Authentication Flow

```
                                    ┌──────────────┐
                                    │  JWT Token   │
                                    │  (24h TTL)   │
                                    └──────┬───────┘
                                           │
  ┌──────────┐   credentials   ┌───────────▼───────────┐
  │  Browser  │ ──────────────►│   AuthController       │
  │           │◄────────────── │   - BCrypt verify      │
  │  stores   │   JWT token    │   - Rate limit 5/60s   │
  │  in       │                │   - JwtService.sign()  │
  │  local    │                └───────────────────────┘
  │  Storage  │
  └──────┬────┘
         │ every request
         ▼
  ┌──────────────────┐         ┌───────────────────────┐
  │ authInterceptor  │────────►│ JwtAuthenticationFilter│
  │ adds Bearer token│         │ - Extract from header  │
  │ catches 401      │         │ - Validate signature   │
  └──────────────────┘         │ - Check expiry         │
                               │ - Set SecurityContext   │
                               └───────────────────────┘
```

### What's Protected vs Public

| Endpoint Pattern | Auth Required? | Reason |
|-----------------|----------------|--------|
| `/api/auth/**` | No | Login/register must be accessible |
| `/api/scan/**` | No | Scan endpoints are demo-friendly |
| `/api/identities/**` | No | PostureIQ data endpoints |
| `/api/dashboard/**` | No | Dashboard stats |
| `/api/remediate` | No | AI remediation |
| `/actuator/health` | No | Container health checks |
| Everything else | Yes | Protected by default |

Frontend route guards provide an additional layer — unauthenticated users are redirected to `/login`.

---

## 6. Database Schema (7 Tables)

| Table | Rows (Demo) | Purpose |
|-------|-------------|---------|
| `users` | 1 (demo user) | Authentication |
| `scan_results` | ~7 (seeded) | Scan history with severity counts |
| `findings` | ~25+ | Individual security findings |
| `iam_identities` | 3-5 | AWS IAM users, roles, groups |
| `iam_policies` | 5-10 | IAM policy documents |
| `iam_identity_policies` | N:M join | Which identities have which policies |
| `ai_finding_details` | ~2-5 | Claude AI enrichment output |

**Key relationships:**
- `scan_results` 1:N `findings` (one scan produces many findings)
- `findings` 1:1 `ai_finding_details` (optional AI enrichment)
- `iam_identities` N:M `iam_policies` (many-to-many via join table)

---

## 7. API Reference (13 Endpoints)

### Authentication
```
POST /api/auth/register   { username, email, password } → { token, username, role }
POST /api/auth/login      { username, password }        → { token, username, role }
```

### CSPM Scanning
```
POST /api/scan                                          → ScanResult (full scan)
GET  /api/scan/{scanId}                                 → ScanResult (by ID)
GET  /api/scans                                         → ScanResult[] (all scans)
POST /api/remediate       { findingId, resourceType, title, description }
                                                        → { findingId, remediation }
GET  /api/scan/{scanId}/report                          → PDF blob
GET  /api/scan/{scanId}/export                          → JSON blob
```

### PostureIQ
```
POST /api/scan/iam                                      → ScanResult (IAM findings)
POST /api/scan/correlate                                → ScanResult (correlated findings)
POST /api/scan/{scanId}/enrich                          → AiFindingDetails[]
GET  /api/identities/high-risk                          → HighRiskIdentity[]
```

### Dashboard
```
GET  /api/dashboard/stats                               → DashboardStats
```

---

## 8. Frontend Component Architecture

```
AppComponent (Root)
├── Toolbar (brand, nav links, logout)
│
├── LoginComponent
│   ├── Login tab (username, password)
│   ├── Register tab (username, email, password)
│   └── "Sign in as demo" button
│
├── ScannerComponent
│   ├── Welcome state (radar animation, scope cards)
│   ├── Scanning state (progress bar, cycling messages)
│   ├── Results state
│   │   ├── Severity filter chips (HIGH, MEDIUM, LOW)
│   │   ├── Resource type dropdown
│   │   ├── Search input
│   │   ├── FindingCard[] (staggered entrance animation)
│   │   │   └── "Get Remediation" → RemediationDialog
│   │   ├── PDF / JSON export buttons
│   │   └── "Next: PostureIQ" CTA
│   └── FindingCardComponent (reusable)
│       └── RemediationDialogComponent (Material dialog)
│
├── PostureIqComponent
│   ├── Step 1: IAM Scan (trigger + results)
│   ├── Step 2: Correlate (with CSPM prerequisite warning)
│   ├── Step 3: AI Enrich (Claude analysis)
│   ├── Step 4: Results
│   │   ├── High-risk identities table
│   │   ├── Correlated findings cards
│   │   └── Expandable AI enrichment details
│   └── "Next: Dashboard" CTA
│
└── DashboardComponent
    ├── Stat cards (total scans, findings, compliance %)
    ├── Severity distribution pie chart
    ├── Findings by resource type bar chart
    ├── Category breakdown doughnut chart
    ├── Scan history trend line chart
    ├── Top 5 high-risk identities table
    └── "Go to Scanner" CTA (empty state)
```

---

## 9. Deployment Architecture

### Docker Compose (3 Containers)

```
┌──────────────────────────────────────────────────────┐
│  docker-compose.yml                                   │
│                                                       │
│  ┌────────────────┐                                  │
│  │  postgres:16    │ Port 5432                        │
│  │  alpine         │ DB: cspmdb                       │
│  │                 │ Healthcheck: pg_isready           │
│  └───────┬─────────┘                                  │
│          │ depends_on (healthy)                        │
│  ┌───────▼─────────┐                                  │
│  │  backend         │ Port 8080                        │
│  │  (Spring Boot)   │ Multi-stage: JDK 17 → JRE 17   │
│  │                  │ Healthcheck: /actuator/health    │
│  │  Env vars:       │                                  │
│  │  - DB_HOST=postgres                                │
│  │  - AWS_SCANNER_ENABLED                             │
│  │  - ANTHROPIC_API_KEY                               │
│  │  - DEMO_SEED_DATA=true                             │
│  └───────┬──────────┘                                  │
│          │ depends_on (healthy)                        │
│  ┌───────▼──────────┐                                  │
│  │  frontend         │ Port 4200 → 80                  │
│  │  (Nginx + Angular)│ Multi-stage: Node 22 → Nginx   │
│  │                   │ SPA routing + API proxy         │
│  └──────────────────┘                                  │
│                                                       │
│  Volume: pgdata (persistent PostgreSQL data)          │
└──────────────────────────────────────────────────────┘
```

### Build Process

**Backend Dockerfile (multi-stage):**
1. `eclipse-temurin:17-jdk` — copies source, runs `./mvnw package -DskipTests`
2. `eclipse-temurin:17-jre` — copies JAR, runs `java -jar app.jar`

**Frontend Dockerfile (multi-stage):**
1. `node:22-alpine` — runs `npm ci && npm run build` (AOT compilation, tree-shaking)
2. `nginx:alpine` — copies built files + `nginx.conf`, serves on port 80

### CI/CD (GitHub Actions)

Two workflows run on push to master:
- **Backend CI**: Maven build + test
- **Frontend CI**: npm install + `ng build --configuration production`

---

## 10. Key Design Patterns

### Pattern 1: Strategy Pattern (Scanner Services)

```java
interface ScannerService { ScanResult runScan(); }

@ConditionalOnProperty("aws.scanner.enabled", havingValue = "true")
class AwsScannerService implements ScannerService { /* real AWS */ }

@ConditionalOnProperty("aws.scanner.enabled", havingValue = "false", matchIfMissing = true)
class MockAwsScanner implements ScannerService { /* mock data */ }
```

Spring injects the right implementation based on configuration. No `if/else` in business logic.

### Pattern 2: Correlation Engine (Cross-Domain Join)

The correlation service performs an in-memory join across two finding domains:
- CONFIG findings (from CSPM scans)
- IAM findings (from identity analysis)

It matches them by resource type (S3↔S3 permissions, EC2↔EC2 permissions) and produces new CORRELATED findings with elevated severity.

### Pattern 3: AI Enrichment Pipeline

Findings flow through a 3-stage pipeline:
1. **Detection** → raw findings with severity
2. **Correlation** → linked identity + resource findings
3. **Enrichment** → Claude generates narrative, impact, and remediation

Each stage produces new data, building on the previous stage's output.

### Pattern 4: Frontend Signals (Angular 21)

```typescript
// Reactive state without RxJS subscriptions
isScanning = signal(false);
scanResult = signal<ScanResult | null>(null);
filteredFindings = computed(() => {
  const result = this.scanResult();
  const filter = this.severityFilter();
  return result?.findings.filter(f => !filter || f.severity === filter);
});
```

Signals provide fine-grained reactivity. No `subscribe()` calls to manage or memory leaks to worry about.

### Pattern 5: Demo Data Seeding

```java
@ConditionalOnProperty(name = "demo.seed-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    // Creates demo user + historical scans on startup
}
```

Set `DEMO_SEED_DATA=true` → app starts with a pre-populated demo user and scan history. Perfect for interviews and presentations.

---

## 11. Talking Points for Interviews/Demos

### Opening Pitch (30 seconds)

> "PostureIQ is a full-stack cloud security posture scanner I built that connects to a real AWS account, scans for misconfigurations like public S3 buckets and open security groups, and then does something most CSPM tools don't — it correlates those findings with IAM identities to show you not just what's misconfigured, but who can actually exploit it. Then it uses Claude AI to generate human-readable attack-path narratives and prioritized remediation steps."

### Architecture Decision Questions

**"Why Spring Boot instead of Node.js?"**
> "AWS SDK v2 for Java is the most mature and feature-complete SDK. Spring Security gives me production-grade JWT auth with minimal code. And Spring Data JPA handles the complex relationships between findings, identities, and AI enrichments cleanly. Plus, most enterprise cloud security tools are Java-based — Prisma Cloud, CrowdStrike Falcon — so the stack matches the domain."

**"Why Angular instead of React?"**
> "Angular 21 with standalone components is actually very modern — no more NgModules. The signals API gives you React-like reactivity. But the real reason is Angular Material — it provides a full enterprise design system out of the box with accessibility, theming, and components like steppers and data tables that are perfect for this security dashboard use case."

**"How does the AI integration work?"**
> "I call the Claude API with a structured prompt that includes the finding details, the associated IAM identity, and related findings for context. Claude returns a structured response with three sections: an attack path narrative describing step-by-step exploitation, a business impact assessment, and prioritized remediation steps. If no API key is configured, I fall back to pre-built templates so the app always works."

**"How do you handle real vs mock data?"**
> "I use Spring's @ConditionalOnProperty annotation. One env var — AWS_SCANNER_ENABLED — swaps the entire scanning implementation. Set it to true and you get real AWS API calls via SDK v2. Set it to false and you get realistic mock data. Same interface, same API contracts. The frontend doesn't know or care which one is active."

### Demo Script

1. **Login** → Click "Sign in as demo" → instant access
2. **Dashboard** → Show pre-seeded historical data, compliance trend improving over time
3. **Scanner** → Click "Run Security Scan" → watch real AWS findings appear
4. **Remediation** → Click "Get Fix" on a finding → Claude generates AWS CLI + Terraform fix
5. **PostureIQ** → Step through the pipeline:
   - IAM Scan → shows identities ingested from real AWS
   - Correlate → shows how IAM + CSPM findings combine into attack paths
   - AI Enrich → Claude generates narratives
   - Results → high-risk identity table with expandable attack paths
6. **Export** → Download PDF report

### Technical Depth Questions

**"What security risks does it detect?"**
> "Ten categories across four AWS services. For S3: public buckets, missing encryption. For IAM: admin-like policies, missing MFA, unrotated keys. For EC2: SSH and RDP open to the internet, overly permissive egress. For EBS: unencrypted volumes. The PostureIQ extension adds four more identity-specific checks and three correlation patterns."

**"What's the correlation engine doing exactly?"**
> "It takes CONFIG findings from the CSPM scan and IAM findings from the identity scan, then performs pattern matching. For example: if it finds a public S3 bucket AND an identity with S3:* permissions, it creates a CRITICAL correlated finding — because that identity can both access and exfiltrate data from those exposed buckets. Same logic for open security groups + EC2 access, and admin identities + any high-severity config issue."

**"How is this different from AWS Config or Security Hub?"**
> "AWS Config tells you what's misconfigured. Security Hub aggregates findings. Neither tells you WHO can exploit the misconfiguration. PostureIQ bridges that gap — it's identity-aware, which is the direction the entire CSPM market is moving. Companies like Wiz, Ermetic, and CrowdStrike all added CIEM (Cloud Infrastructure Entitlement Management) on top of CSPM. PostureIQ demonstrates that same pattern."

---

## 12. Project Statistics

| Metric | Value |
|--------|-------|
| Backend Java classes | ~30 |
| Frontend TypeScript files | ~25 |
| API endpoints | 13 |
| Database tables | 7 |
| Docker containers | 3 |
| Backend tests | 42 (PostureIQ) + existing |
| AWS services scanned | 4 (S3, IAM, EC2, EBS) |
| Security risk categories | 10 (CSPM) + 4 (IAM) + 3 (correlated) |
| Lines of code | ~5,000+ |
| External integrations | AWS SDK v2, Claude API |

---

## 13. Future Roadmap

| Enhancement | Difficulty | Impact |
|-------------|-----------|--------|
| GCP and Azure support | High | Multi-cloud coverage |
| Terraform/CloudFormation scanning | Medium | Shift-left security |
| Slack/Teams notifications | Low | Alerting pipeline |
| Custom policy rules engine | Medium | Extensibility |
| Multi-account scanning | Medium | Enterprise readiness |
| Per-resource access graph (CIEM) | High | Deep identity analysis |
| SOC/SIEM integration | Medium | Security operations |
| Scheduled recurring scans | Low | Continuous monitoring |
