#!/usr/bin/env bash
# Cron: Daily scan + alert on HIGH/CRITICAL findings
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

echo "Running daily PostureIQ scan..."

SCAN_RESULT=$(api_post "/api/scan" "{}") || exit $?
SCAN_ID=$(echo "$SCAN_RESULT" | jq -r '.scanId')
STATUS=$(echo "$SCAN_RESULT" | jq -r '.status')

if [[ "$STATUS" != "COMPLETED" ]]; then
  SCAN_RESULT=$(poll_scan "$SCAN_ID" 600) || exit $?
fi

CRITICAL=$(echo "$SCAN_RESULT" | jq '[.findings[] | select(.severity == "CRITICAL")] | length')
HIGH=$(echo "$SCAN_RESULT" | jq -r '.highSeverity // 0')
MEDIUM=$(echo "$SCAN_RESULT" | jq -r '.mediumSeverity // 0')
LOW=$(echo "$SCAN_RESULT" | jq -r '.lowSeverity // 0')
TOTAL=$(echo "$SCAN_RESULT" | jq -r '.totalFindings // 0')
HIGH_CRITICAL=$(( CRITICAL + HIGH ))

if (( HIGH_CRITICAL > 0 )); then
  cat <<EOF
🚨 **PostureIQ Daily Scan Alert**

Scan \`${SCAN_ID}\` found **${HIGH_CRITICAL} HIGH/CRITICAL** findings that require attention.

| Severity | Count |
|----------|-------|
| $(format_severity CRITICAL) | ${CRITICAL} |
| $(format_severity HIGH) | ${HIGH} |
| $(format_severity MEDIUM) | ${MEDIUM} |
| $(format_severity LOW) | ${LOW} |
| **Total** | **${TOTAL}** |

Run \`/postureiq findings ${SCAN_ID}\` for details.
EOF
else
  cat <<EOF
✅ **PostureIQ Daily Scan — All Clear**

Scan \`${SCAN_ID}\` completed with **${TOTAL}** findings — none HIGH or CRITICAL.

| Severity | Count |
|----------|-------|
| $(format_severity MEDIUM) | ${MEDIUM} |
| $(format_severity LOW) | ${LOW} |
| **Total** | **${TOTAL}** |
EOF
fi
