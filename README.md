# Cloud Security Posture Scanner (CSPM Mini-Tool)

[![Backend CI](https://github.com/brcm-shilpa/cspm-scanner/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/brcm-shilpa/cspm-scanner/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/brcm-shilpa/cspm-scanner/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/brcm-shilpa/cspm-scanner/actions/workflows/frontend-ci.yml)

Scan AWS configurations and infrastructure code for security risks (public S3 buckets, weak IAM policies, exposed secrets), generate compliance reports, and get AI-powered remediation suggestions via Claude.

## Why This Project?

- Leverages CASB/CSPM domain expertise—highly relevant for cloud security roles (AWS/GCP)
- Demonstrates practical AI integration for security automation
- Mirrors real-world enterprise security workflows (Symantec CASB patterns)

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Angular 17+ |
| Backend | Spring Boot 3.x |
| Cloud SDK | AWS SDK for Java |
| AI Integration | Claude API |
| Database | PostgreSQL |
| Deployment | Vercel (UI) + Railway (API) |

## Core Features

1. **AWS Configuration Scanning** - Input AWS credentials/config files to scan for Top 10 cloud security risks
2. **AI-Powered Remediation** - Claude generates fix snippets (e.g., "Secure this S3 bucket policy")
3. **Compliance Dashboard** - Visual charts showing risk distribution and compliance status
4. **Historical Scan Tracking** - Track security posture improvements over time
5. **Export Reports** - Generate PDF/JSON reports for stakeholders

## Security Risks Detected

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

---

## Skills Learning Path

### Phase 1: Foundations (Week 1)

#### Angular (Frontend)
- [ ] Angular CLI setup and project structure
- [ ] Components, modules, and routing
- [ ] Reactive forms for credential input
- [ ] HTTP client for API calls
- [ ] Angular Material for UI components

**Resources:**
- [Angular Official Tutorial](https://angular.io/tutorial)
- [Angular Material Docs](https://material.angular.io/)

#### Spring Boot (Backend)
- [ ] Spring Initializr project setup
- [ ] REST controller basics
- [ ] Service layer architecture
- [ ] Exception handling
- [ ] Configuration management (application.yml)

**Resources:**
- [Spring Boot Getting Started](https://spring.io/guides/gs/spring-boot/)
- [Building REST Services](https://spring.io/guides/tutorials/rest/)

### Phase 2: Cloud & Security (Week 2)

#### AWS SDK & Security
- [ ] AWS SDK for Java setup
- [ ] IAM programmatic access
- [ ] S3 bucket policy inspection
- [ ] EC2 security group analysis
- [ ] AWS Config rules understanding

**Resources:**
- [AWS SDK for Java Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [AWS Security Best Practices](https://docs.aws.amazon.com/wellarchitected/latest/security-pillar/)
- [CIS AWS Benchmarks](https://www.cisecurity.org/benchmark/amazon_web_services)

#### Claude API Integration
- [ ] Anthropic API setup and authentication
- [ ] Prompt engineering for security analysis
- [ ] Structured output for remediation code
- [ ] Rate limiting and error handling

**Resources:**
- [Claude API Documentation](https://docs.anthropic.com/)
- [Prompt Engineering Guide](https://docs.anthropic.com/claude/docs/prompt-engineering)

### Phase 3: Polish & Deploy (Week 3)

#### Database & Persistence
- [ ] PostgreSQL setup
- [ ] Spring Data JPA entities
- [ ] Scan history storage
- [ ] Query optimization

#### Deployment
- [ ] Vercel deployment for Angular
- [ ] Railway deployment for Spring Boot
- [x] Environment variable management
- [x] CI/CD pipeline (GitHub Actions)

#### Security Hardening
- [ ] JWT authentication
- [ ] Credential encryption at rest
- [ ] Input validation
- [ ] CORS configuration

---

## Development Roadmap

### Week 1: Project Setup & Core Structure
- [ ] Initialize Angular project with Angular CLI
- [ ] Initialize Spring Boot project with Spring Initializr
- [ ] Set up project structure and basic routing
- [ ] Create AWS credential input form
- [ ] Implement basic REST endpoints

### Week 2: Core MVP Features
- [ ] Integrate AWS SDK for scanning
- [ ] Implement 3-5 security checks (S3, IAM, Security Groups)
- [ ] Connect Claude API for remediation suggestions
- [ ] Build results display component
- [ ] Add scan history storage

### Week 3: Polish & Launch
- [ ] Add authentication (JWT)
- [ ] Build compliance dashboard with charts (Chart.js/ng2-charts)
- [ ] Implement PDF report export
- [ ] Deploy to Vercel + Railway
- [ ] Write documentation and create demo video

---

## Project Structure

```
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
│   │       │   └── claude/
│   │       ├── model/
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
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080
- **Health check**: http://localhost:8080/actuator/health

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

- **Node.js 22+** and npm
- **Java 17+**
- **Angular CLI**: `npm install -g @angular/cli`

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

### Using the Application

1. Open `http://localhost:4200` in your browser
2. Click "Run Security Scan" to analyze mock AWS infrastructure
3. View security findings with severity levels (HIGH/MEDIUM/LOW)
4. Click "Get Fix" on any finding to get Claude-powered remediation

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/scan` | Trigger security scan |
| GET | `/api/scan/{id}` | Get scan results by ID |
| POST | `/api/remediate` | Get AI-powered fix |

### Verification

```bash
# Health check
curl http://localhost:8080/actuator/health

# Run a scan
curl -X POST http://localhost:8080/api/scan
```

## Claude API Integration

To enable real Claude API calls for remediation, set the `ANTHROPIC_API_KEY` environment variable:

```bash
export ANTHROPIC_API_KEY=your-api-key-here
```

If no API key is set, the application uses pre-built mock remediation responses.

---

## Demo Highlights

- **Live AWS Scanning**: Run real-time scans during interviews
- **AI Remediation**: Show Claude generating security fixes
- **Compliance Metrics**: Display risk reduction over time

---

## Future Enhancements

- GCP and Azure support
- Terraform/CloudFormation scanning
- Slack/Teams notifications
- Custom policy rules engine
- Multi-account scanning

---

## License

MIT
