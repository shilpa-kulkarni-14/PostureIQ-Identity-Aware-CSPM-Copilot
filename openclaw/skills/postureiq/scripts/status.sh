#!/usr/bin/env bash
# /postureiq status — Show dashboard statistics
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

STATS=$(api_get "/api/dashboard/stats") || exit $?

TOTAL_SCANS=$(echo "$STATS" | jq -r '.totalScans // 0')
TOTAL_FINDINGS=$(echo "$STATS" | jq -r '.totalFindings // 0')
COMPLIANCE=$(echo "$STATS" | jq -r '.complianceScore // "N/A"')
HIGH=$(echo "$STATS" | jq -r '.severityDistribution.HIGH // 0')
MEDIUM=$(echo "$STATS" | jq -r '.severityDistribution.MEDIUM // 0')
LOW=$(echo "$STATS" | jq -r '.severityDistribution.LOW // 0')

TOTAL_REM=$(echo "$STATS" | jq -r '.remediationStats.totalRemediations // 0')
SUCCESS_REM=$(echo "$STATS" | jq -r '.remediationStats.successfulRemediations // 0')
SUCCESS_RATE=$(echo "$STATS" | jq -r '.remediationStats.successRate // 0')

cat <<EOF
## PostureIQ Dashboard

| Metric | Value |
|--------|-------|
| Total Scans | ${TOTAL_SCANS} |
| Total Findings | ${TOTAL_FINDINGS} |
| Compliance Score | ${COMPLIANCE}% |

### Severity Breakdown

| Severity | Count |
|----------|-------|
| $(format_severity HIGH) | ${HIGH} |
| $(format_severity MEDIUM) | ${MEDIUM} |
| $(format_severity LOW) | ${LOW} |

### Remediation

| Metric | Value |
|--------|-------|
| Total Remediations | ${TOTAL_REM} |
| Successful | ${SUCCESS_REM} |
| Success Rate | ${SUCCESS_RATE}% |
EOF
