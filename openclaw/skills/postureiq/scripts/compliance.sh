#!/usr/bin/env bash
# /postureiq compliance <scanId> — Regulatory compliance summary
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

SCAN_ID="${1:-}"
if [[ -z "$SCAN_ID" ]]; then
  SCAN_ID=$(get_latest_scan_id) || exit $?
  echo "_Using latest scan:_ \`${SCAN_ID}\`"
  echo
fi

RESPONSE=$(api_get "/api/scan/${SCAN_ID}/compliance-summary") || exit $?

TOTAL_VIOLATIONS=$(echo "$RESPONSE" | jq -r '.totalViolations // 0')
FRAMEWORKS=$(echo "$RESPONSE" | jq -r '.frameworksCovered | join(", ")')

echo "## Compliance Summary for Scan \`${SCAN_ID}\`"
echo
echo "**Total Violations:** ${TOTAL_VIOLATIONS}  "
echo "**Frameworks Covered:** ${FRAMEWORKS}"
echo

echo "| Framework | Violations | Risk Level | Critical Controls |"
echo "|-----------|------------|------------|-------------------|"

echo "$RESPONSE" | jq -r '.frameworkSummaries[] | [
  .framework,
  .violationCount,
  .overallRiskLevel,
  (.criticalControls | join(", "))
] | @tsv' | while IFS=$'\t' read -r fw vcount risk controls; do
  risk_fmt=$(format_severity "$risk")
  echo "| **${fw}** | ${vcount} | ${risk_fmt} | ${controls} |"
done
