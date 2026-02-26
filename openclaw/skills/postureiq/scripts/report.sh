#!/usr/bin/env bash
# /postureiq report <scanId> — Download PDF report
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

SCAN_ID="${1:-}"
if [[ -z "$SCAN_ID" ]]; then
  SCAN_ID=$(get_latest_scan_id) || exit $?
fi

OUTPUT_FILE="/tmp/postureiq-report-${SCAN_ID}.pdf"

echo "📥 Downloading report for scan \`${SCAN_ID}\`..."

api_download "/api/scan/${SCAN_ID}/report" "$OUTPUT_FILE" || exit $?

if [[ -f "$OUTPUT_FILE" && -s "$OUTPUT_FILE" ]]; then
  FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
  cat <<EOF
## Report Downloaded

**Scan ID:** \`${SCAN_ID}\`
**File:** \`${OUTPUT_FILE}\`
**Size:** ${FILE_SIZE}

The PDF report has been saved to the path above.
EOF
else
  echo "Error: Report download produced an empty file." >&2
  rm -f "$OUTPUT_FILE"
  exit 1
fi
