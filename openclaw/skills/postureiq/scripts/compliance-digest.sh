#!/usr/bin/env bash
# Cron: Daily compliance score digest
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

# ── Dashboard Stats ──────────────────────────────────────────────────
STATS=$(api_get "/api/dashboard/stats") || exit $?
COMPLIANCE=$(echo "$STATS" | jq -r '.complianceScore // "N/A"')
TOTAL_FINDINGS=$(echo "$STATS" | jq -r '.totalFindings // 0')
TOTAL_SCANS=$(echo "$STATS" | jq -r '.totalScans // 0')
SUCCESS_RATE=$(echo "$STATS" | jq -r '.remediationStats.successRate // 0')

# ── Compliance Summary from Latest Scan ──────────────────────────────
SCAN_ID=$(get_latest_scan_id) || exit $?
COMPLIANCE_DATA=$(api_get "/api/scan/${SCAN_ID}/compliance-summary" 2>/dev/null || echo '{}')

TOTAL_VIOLATIONS=$(echo "$COMPLIANCE_DATA" | jq -r '.totalViolations // "N/A"')

cat <<EOF
📋 **PostureIQ Daily Compliance Digest**

**Date:** $(date +%Y-%m-%d)
**Latest Scan:** \`${SCAN_ID}\`

### Overview

| Metric | Value |
|--------|-------|
| Compliance Score | **${COMPLIANCE}%** |
| Total Findings | ${TOTAL_FINDINGS} |
| Total Violations | ${TOTAL_VIOLATIONS} |
| Remediation Rate | ${SUCCESS_RATE}% |
| Total Scans | ${TOTAL_SCANS} |
EOF

# ── Framework Breakdown (if available) ───────────────────────────────
FRAMEWORK_COUNT=$(echo "$COMPLIANCE_DATA" | jq '.frameworkSummaries | length // 0' 2>/dev/null || echo 0)
if (( FRAMEWORK_COUNT > 0 )); then
  echo
  echo "### Framework Compliance"
  echo
  echo "| Framework | Violations | Risk Level |"
  echo "|-----------|------------|------------|"

  echo "$COMPLIANCE_DATA" | jq -r '.frameworkSummaries[] | [
    .framework,
    .violationCount,
    .overallRiskLevel
  ] | @tsv' | while IFS=$'\t' read -r fw vcount risk; do
    risk_fmt=$(format_severity "$risk")
    echo "| **${fw}** | ${vcount} | ${risk_fmt} |"
  done
fi

echo
echo "_Run \`/postureiq compliance ${SCAN_ID}\` for full details._"
