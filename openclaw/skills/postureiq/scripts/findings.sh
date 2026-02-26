#!/usr/bin/env bash
# /postureiq findings [scanId] — List findings as a markdown table
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

SCAN_ID="${1:-}"
if [[ -z "$SCAN_ID" ]]; then
  SCAN_ID=$(get_latest_scan_id) || exit $?
  echo "_Using latest scan:_ \`${SCAN_ID}\`"
  echo
fi

RESULT=$(api_get "/api/scan/${SCAN_ID}") || exit $?

FINDING_COUNT=$(echo "$RESULT" | jq '.findings | length')
if (( FINDING_COUNT == 0 )); then
  echo "No findings for scan \`${SCAN_ID}\`."
  exit 0
fi

echo "## Findings for Scan \`${SCAN_ID}\`"
echo
echo "| # | Severity | Resource Type | Resource ID | Title | Status |"
echo "|---|----------|---------------|-------------|-------|--------|"

echo "$RESULT" | jq -r '.findings | to_entries[] | [
  (.key + 1),
  .value.severity,
  .value.resourceType,
  .value.resourceId,
  .value.title,
  (.value.remediationStatus // "OPEN")
] | @tsv' | while IFS=$'\t' read -r num sev rtype rid title status; do
  sev_fmt=$(format_severity "$sev")
  echo "| ${num} | ${sev_fmt} | ${rtype} | \`${rid}\` | ${title} | ${status} |"
done

echo
echo "_Total: ${FINDING_COUNT} findings_"
