---
name: postureiq
description: Cloud Security Posture Management — trigger scans, view findings, auto-remediate, and track compliance from chat.
version: 1.0.0
metadata: {"openclaw":{"requires":{"env":["POSTUREIQ_API_URL","POSTUREIQ_USERNAME","POSTUREIQ_PASSWORD"],"bins":["curl","jq"]}}}
---

# PostureIQ — CSPM Skill for OpenClaw

PostureIQ is a Cloud Security Posture Management platform that scans AWS cloud configurations, identifies misconfigurations, correlates IAM risks, and provides AI-powered remediation. This skill brings PostureIQ's capabilities into any chat platform OpenClaw supports — Slack, Teams, Discord, and more.

## Available Commands

| Command | Description |
|---------|-------------|
| `/postureiq scan [region]` | Trigger a cloud security scan and show severity summary |
| `/postureiq status` | Show dashboard stats — total scans, findings, compliance score, remediation rate |
| `/postureiq findings [scanId]` | List findings as a table (defaults to latest scan) |
| `/postureiq remediate <findingId>` | Get AI-powered remediation guidance for a specific finding |
| `/postureiq fix <findingId> [--apply]` | Auto-remediate a finding (dry-run by default, `--apply` to execute with approval) |
| `/postureiq identities` | List high-risk IAM identities with risk scores |
| `/postureiq compliance [scanId]` | Show regulatory compliance summary by framework |
| `/postureiq report [scanId]` | Download a PDF report for a scan |

## Authentication

The skill authenticates against PostureIQ using environment variables:

| Variable | Description |
|----------|-------------|
| `POSTUREIQ_API_URL` | Base URL of the PostureIQ API (e.g., `https://postureiq.example.com`) |
| `POSTUREIQ_USERNAME` | PostureIQ username |
| `POSTUREIQ_PASSWORD` | PostureIQ password |

JWT tokens are cached for 23 hours at `/tmp/.postureiq_token_<uid>` with `600` permissions.

## Output Formatting

All commands output GitHub-flavored Markdown suitable for chat rendering:

- **Tables** for structured data (findings, stats, compliance)
- **Severity emojis:** 🔴 CRITICAL, 🟠 HIGH, 🟡 MEDIUM, 🔵 LOW
- **Code spans** for IDs, ARNs, and resource identifiers
- **Bold** for emphasis on key values

## Automated Workflows

### Daily Security Scan (7 AM UTC)

Runs a full scan and alerts the channel if HIGH or CRITICAL findings are detected.

```bash
openclaw cron add --name "postureiq-daily-scan" --cron "0 7 * * *" \
  --script scripts/daily-scan.sh --announce --channel slack
```

### Daily Compliance Digest (8 AM UTC)

Posts a compliance score summary with framework-level violation breakdown.

```bash
openclaw cron add --name "postureiq-compliance-digest" --cron "0 8 * * *" \
  --script scripts/compliance-digest.sh --announce --channel slack
```

## CI/CD Integration

### Standalone Scanner

The `scripts/ci-scan.sh` script runs in any CI/CD environment:

```bash
# Fail if more than 5 HIGH+CRITICAL findings
./scripts/ci-scan.sh --threshold 5 --output summary

# Generate SARIF for GitHub Security tab
./scripts/ci-scan.sh --output sarif --output-file results.sarif

# JSON output for custom processing
./scripts/ci-scan.sh --output json --output-file results.json
```

**Exit codes:** `0` = pass, `1` = threshold exceeded, `2` = auth/API error.

### GitHub Actions

Copy `assets/github-action.yml` to `.github/workflows/postureiq.yml` in your repository. Configure these secrets:

- `POSTUREIQ_API_URL`
- `POSTUREIQ_USERNAME`
- `POSTUREIQ_PASSWORD`

Optionally set the `POSTUREIQ_THRESHOLD` repository variable to control the maximum allowed HIGH+CRITICAL findings (defaults to 0).

The workflow will:
1. Run a PostureIQ scan on push/PR to `main`
2. Upload SARIF results to the GitHub Security tab
3. Generate a human-readable summary in the PR check
4. Fail the build if the threshold is exceeded

## API Reference

See `references/api-reference.md` for complete endpoint documentation. The agent can use this as context when answering follow-up questions about PostureIQ's capabilities.
