#!/usr/bin/env bash
# /postureiq scan — Trigger a cloud security scan and report summary
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

REGION="${1:-}"

echo "⏳ Triggering PostureIQ scan..."

BODY="{}"
[[ -n "$REGION" ]] && BODY="{\"region\":\"${REGION}\"}"

SCAN_RESULT=$(api_post "/api/scan" "$BODY") || exit $?
SCAN_ID=$(echo "$SCAN_RESULT" | jq -r '.scanId')
STATUS=$(echo "$SCAN_RESULT" | jq -r '.status')

if [[ "$STATUS" != "COMPLETED" ]]; then
  echo "Scan **${SCAN_ID}** started — polling for completion..."
  SCAN_RESULT=$(poll_scan "$SCAN_ID") || exit $?
fi

# ── Format output ────────────────────────────────────────────────────
TOTAL=$(echo "$SCAN_RESULT" | jq -r '.totalFindings // 0')
CRITICAL=$(echo "$SCAN_RESULT" | jq '[.findings[] | select(.severity == "CRITICAL")] | length')
HIGH=$(echo "$SCAN_RESULT" | jq -r '.highSeverity // 0')
MEDIUM=$(echo "$SCAN_RESULT" | jq -r '.mediumSeverity // 0')
LOW=$(echo "$SCAN_RESULT" | jq -r '.lowSeverity // 0')

cat <<EOF
## Scan Complete

**Scan ID:** \`${SCAN_ID}\`

| Severity | Count |
|----------|-------|
| $(format_severity CRITICAL) | ${CRITICAL} |
| $(format_severity HIGH) | ${HIGH} |
| $(format_severity MEDIUM) | ${MEDIUM} |
| $(format_severity LOW) | ${LOW} |
| **Total** | **${TOTAL}** |

Use \`/postureiq findings ${SCAN_ID}\` to view details.
EOF
