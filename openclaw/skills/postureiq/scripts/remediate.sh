#!/usr/bin/env bash
# /postureiq remediate <findingId> — Get AI remediation guidance
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

FINDING_ID="${1:?Usage: /postureiq remediate <findingId>}"

# First, fetch the finding details from the latest scan
SCAN_ID=$(get_latest_scan_id) || exit $?
SCAN_RESULT=$(api_get "/api/scan/${SCAN_ID}") || exit $?

FINDING=$(echo "$SCAN_RESULT" | jq --arg fid "$FINDING_ID" '.findings[] | select(.id == $fid)')
if [[ -z "$FINDING" ]]; then
  echo "Error: Finding \`${FINDING_ID}\` not found in scan \`${SCAN_ID}\`." >&2
  exit 1
fi

RESOURCE_TYPE=$(echo "$FINDING" | jq -r '.resourceType')
RESOURCE_ID=$(echo "$FINDING" | jq -r '.resourceId')
TITLE=$(echo "$FINDING" | jq -r '.title')
DESCRIPTION=$(echo "$FINDING" | jq -r '.description')

BODY=$(jq -n \
  --arg fid "$FINDING_ID" \
  --arg rt "$RESOURCE_TYPE" \
  --arg rid "$RESOURCE_ID" \
  --arg t "$TITLE" \
  --arg d "$DESCRIPTION" \
  '{findingId: $fid, resourceType: $rt, resourceId: $rid, title: $t, description: $d}')

RESPONSE=$(api_post "/api/remediate" "$BODY") || exit $?

REMEDIATION=$(echo "$RESPONSE" | jq -r '.remediation // "No remediation advice available."')
SEVERITY=$(echo "$FINDING" | jq -r '.severity')

cat <<EOF
## Remediation Guidance

**Finding:** ${TITLE}
**Severity:** $(format_severity "$SEVERITY")
**Resource:** \`${RESOURCE_TYPE}\` / \`${RESOURCE_ID}\`

---

${REMEDIATION}

---

_To auto-remediate, run:_ \`/postureiq fix ${FINDING_ID}\`
EOF
