# PostureIQ API Reference

Base URL: configured via `POSTUREIQ_API_URL` environment variable.

All endpoints except `/api/auth/*` require a `Bearer` JWT token in the `Authorization` header.

---

## Authentication

### POST `/api/auth/login`

Authenticate and obtain a JWT token.

**Request:**
```json
{ "username": "string", "password": "string" }
```

**Response:**
```json
{ "token": "string", "username": "string", "role": "string" }
```

Rate limited: 5 attempts per 60 seconds per username.

### POST `/api/auth/register`

Register a new user.

**Request:**
```json
{ "username": "string", "password": "string" }
```

**Response:** Same as login.

---

## Scanning

### POST `/api/scan`

Trigger a cloud configuration scan.

**Query Parameters:**
- `region` (optional) — AWS region to scan

**Response:** `ScanResult`

### GET `/api/scans`

List all scans.

**Response:** `ScanResult[]`

### GET `/api/scan/{scanId}`

Get scan results with findings.

**Response:** `ScanResult`

### GET `/api/scan/{scanId}/report`

Download PDF report.

**Response:** Binary PDF file.

### GET `/api/scan/{scanId}/export`

Export scan as JSON.

**Response:** `ScanResult`

---

## Remediation

### POST `/api/remediate`

Get AI-powered remediation guidance.

**Request:**
```json
{
  "findingId": "string",
  "resourceType": "string",
  "resourceId": "string",
  "title": "string",
  "description": "string"
}
```

**Response:**
```json
{ "findingId": "string", "remediation": "string" }
```

### POST `/api/remediate/auto`

Execute auto-remediation.

**Request:**
```json
{
  "findingId": "string",
  "scanId": "string (optional)",
  "sessionId": "string (optional)",
  "dryRun": true,
  "requireApproval": false
}
```

**Response:** `AutoRemediationResponse` — includes `status`, `sessionId`, `actions[]`, `summary`, `pendingApproval`, `approvalRequests[]`.

### GET `/api/remediate/stream/{sessionId}`

Server-Sent Events stream for remediation progress.

### POST `/api/remediate/approve`

Approve a pending remediation plan.

**Request:**
```json
{
  "findingId": "string",
  "sessionId": "string",
  "toolName": "string",
  "toolInput": "string",
  "resourceType": "string",
  "resourceId": "string",
  "description": "string",
  "approved": true
}
```

### GET `/api/remediate/audit/{findingId}`

Get remediation history for a finding.

### GET `/api/remediate/audit/session/{sessionId}`

Get remediation history for a session.

---

## PostureIQ (Identity & Compliance)

### POST `/api/scan/iam`

Ingest IAM data and run risk analysis.

### POST `/api/scan/correlate`

Correlate IAM risks with CSPM findings.

### POST `/api/scan/{scanId}/enrich`

AI-enrich findings with attack paths and regulatory mapping.

**Response:** `AiFindingDetails[]`

### GET `/api/identities/high-risk`

Get high-risk IAM identities ranked by risk score.

**Response:**
```json
[
  {
    "identityArn": "string",
    "identityName": "string",
    "identityType": "USER | ROLE | GROUP",
    "riskScore": 0,
    "findingCount": 0,
    "highSeverityCount": 0,
    "findings": []
  }
]
```

### GET `/api/scan/{scanId}/compliance-summary`

Get regulatory compliance violation summary.

**Response:**
```json
{
  "scanId": "string",
  "frameworkSummaries": [
    {
      "framework": "string",
      "violationCount": 0,
      "criticalControls": [],
      "overallRiskLevel": "CRITICAL | HIGH | MEDIUM | LOW"
    }
  ],
  "totalViolations": 0,
  "frameworksCovered": []
}
```

### GET `/api/findings/{findingId}/compliance`

Get finding-level regulatory compliance details.

---

## Dashboard

### GET `/api/dashboard/stats`

Get comprehensive dashboard statistics.

**Response:**
```json
{
  "totalScans": 0,
  "totalFindings": 0,
  "complianceScore": 0,
  "severityDistribution": { "HIGH": 0, "MEDIUM": 0, "LOW": 0 },
  "findingsByResourceType": {},
  "scanHistory": [],
  "remediationStats": {
    "totalRemediations": 0,
    "successfulRemediations": 0,
    "failedRemediations": 0,
    "successRate": 0,
    "remediationsByTool": {},
    "remediationsByResourceType": {},
    "recentRemediations": []
  }
}
```

---

## Data Models

### ScanResult
```json
{
  "scanId": "string",
  "timestamp": "ISO-8601",
  "status": "RUNNING | COMPLETED | FAILED",
  "findings": [],
  "totalFindings": 0,
  "highSeverity": 0,
  "mediumSeverity": 0,
  "lowSeverity": 0
}
```

### Finding
```json
{
  "id": "string",
  "resourceType": "S3 | IAM | EC2 | EBS",
  "resourceId": "string",
  "severity": "CRITICAL | HIGH | MEDIUM | LOW",
  "title": "string",
  "description": "string",
  "remediation": "string (optional)",
  "category": "CONFIG | IAM | CORRELATED",
  "primaryIdentityArn": "string (optional)",
  "remediationStatus": "OPEN | REMEDIATED | FAILED | PARTIAL"
}
```
