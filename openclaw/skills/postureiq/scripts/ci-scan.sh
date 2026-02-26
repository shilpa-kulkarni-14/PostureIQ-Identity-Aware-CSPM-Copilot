#!/usr/bin/env bash
# PostureIQ CI/CD scanner — standalone for GitHub Actions / GitLab CI
# Exit codes: 0 = pass, 1 = threshold exceeded, 2 = auth/API error
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

# ── Defaults ─────────────────────────────────────────────────────────
THRESHOLD=0
OUTPUT_FORMAT="summary"
OUTPUT_FILE=""

# ── Argument Parsing ─────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --threshold)  THRESHOLD="$2"; shift 2 ;;
    --output)     OUTPUT_FORMAT="$2"; shift 2 ;;
    --output-file) OUTPUT_FILE="$2"; shift 2 ;;
    -h|--help)
      echo "Usage: ci-scan.sh [--threshold N] [--output json|sarif|summary] [--output-file PATH]"
      exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

# ── Run Scan ─────────────────────────────────────────────────────────
echo "::group::PostureIQ Scan" >&2
echo "Triggering scan..." >&2

SCAN_RESULT=$(api_post "/api/scan" "{}") || exit 2
SCAN_ID=$(echo "$SCAN_RESULT" | jq -r '.scanId')
STATUS=$(echo "$SCAN_RESULT" | jq -r '.status')

if [[ "$STATUS" != "COMPLETED" ]]; then
  echo "Polling scan ${SCAN_ID}..." >&2
  SCAN_RESULT=$(poll_scan "$SCAN_ID" 600) || exit 2
fi

echo "Scan ${SCAN_ID} completed." >&2
echo "::endgroup::" >&2

# ── Count Findings ───────────────────────────────────────────────────
CRITICAL=$(echo "$SCAN_RESULT" | jq '[.findings[] | select(.severity == "CRITICAL")] | length')
HIGH=$(echo "$SCAN_RESULT" | jq -r '.highSeverity // 0')
MEDIUM=$(echo "$SCAN_RESULT" | jq -r '.mediumSeverity // 0')
LOW=$(echo "$SCAN_RESULT" | jq -r '.lowSeverity // 0')
TOTAL=$(echo "$SCAN_RESULT" | jq -r '.totalFindings // 0')
HIGH_CRITICAL=$(( CRITICAL + HIGH ))

# ── Output ───────────────────────────────────────────────────────────
generate_summary() {
  cat <<EOF
## PostureIQ Scan Results

**Scan ID:** \`${SCAN_ID}\`

| Severity | Count |
|----------|-------|
| 🔴 CRITICAL | ${CRITICAL} |
| 🟠 HIGH | ${HIGH} |
| 🟡 MEDIUM | ${MEDIUM} |
| 🔵 LOW | ${LOW} |
| **Total** | **${TOTAL}** |

**Threshold:** ${THRESHOLD} | **HIGH+CRITICAL:** ${HIGH_CRITICAL} | **Result:** $(if (( HIGH_CRITICAL > THRESHOLD )); then echo "❌ FAIL"; else echo "✅ PASS"; fi)
EOF
}

generate_sarif() {
  local sarif_template="${SCRIPT_DIR}/../assets/sarif-template.json"
  local rules=""
  local results=""

  # Build SARIF rules and results from findings
  rules=$(echo "$SCAN_RESULT" | jq '[.findings[] | {
    id: .id,
    shortDescription: { text: .title },
    fullDescription: { text: .description },
    defaultConfiguration: {
      level: (if .severity == "CRITICAL" or .severity == "HIGH" then "error"
              elif .severity == "MEDIUM" then "warning"
              else "note" end)
    },
    properties: { severity: .severity, resourceType: .resourceType }
  }]')

  results=$(echo "$SCAN_RESULT" | jq '[.findings[] | {
    ruleId: .id,
    message: { text: (.title + " — " + .description) },
    level: (if .severity == "CRITICAL" or .severity == "HIGH" then "error"
            elif .severity == "MEDIUM" then "warning"
            else "note" end),
    locations: [{
      physicalLocation: {
        artifactLocation: { uri: (.resourceType + "/" + .resourceId) },
        region: { startLine: 1 }
      }
    }],
    properties: {
      severity: .severity,
      resourceType: .resourceType,
      resourceId: .resourceId,
      remediationStatus: (.remediationStatus // "OPEN")
    }
  }]')

  # Merge into SARIF template
  jq --argjson rules "$rules" --argjson results "$results" \
    '.runs[0].tool.driver.rules = $rules | .runs[0].results = $results' \
    "$sarif_template"
}

case "$OUTPUT_FORMAT" in
  json)
    OUTPUT=$(echo "$SCAN_RESULT" | jq '.')
    ;;
  sarif)
    OUTPUT=$(generate_sarif)
    ;;
  summary)
    OUTPUT=$(generate_summary)
    ;;
  *)
    echo "Unknown output format: ${OUTPUT_FORMAT}" >&2
    exit 2
    ;;
esac

if [[ -n "$OUTPUT_FILE" ]]; then
  echo "$OUTPUT" > "$OUTPUT_FILE"
  echo "Output written to ${OUTPUT_FILE}" >&2
else
  echo "$OUTPUT"
fi

# ── Threshold Check ──────────────────────────────────────────────────
if (( HIGH_CRITICAL > THRESHOLD )); then
  echo "::error::PostureIQ: ${HIGH_CRITICAL} HIGH+CRITICAL findings exceed threshold of ${THRESHOLD}" >&2
  exit 1
fi

echo "PostureIQ: ${HIGH_CRITICAL} HIGH+CRITICAL findings within threshold of ${THRESHOLD}" >&2
exit 0
