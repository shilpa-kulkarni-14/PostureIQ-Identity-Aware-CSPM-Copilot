#!/usr/bin/env bash
# /postureiq fix <findingId> [--apply] — Auto-remediate (dry-run by default)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

FINDING_ID=""
APPLY=false

for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=true ;;
    *)       FINDING_ID="$arg" ;;
  esac
done

if [[ -z "$FINDING_ID" ]]; then
  echo "Usage: /postureiq fix <findingId> [--apply]" >&2
  exit 1
fi

if $APPLY; then
  DRY_RUN=false
  REQUIRE_APPROVAL=true
  echo "⚙️ Executing auto-remediation for \`${FINDING_ID}\` (requires approval)..."
else
  DRY_RUN=true
  REQUIRE_APPROVAL=false
  echo "🔍 Dry-run auto-remediation for \`${FINDING_ID}\`..."
fi

BODY=$(jq -n \
  --arg fid "$FINDING_ID" \
  --argjson dr "$DRY_RUN" \
  --argjson ra "$REQUIRE_APPROVAL" \
  '{findingId: $fid, dryRun: $dr, requireApproval: $ra}')

RESPONSE=$(api_post "/api/remediate/auto" "$BODY") || exit $?

STATUS=$(echo "$RESPONSE" | jq -r '.status')
SUMMARY=$(echo "$RESPONSE" | jq -r '.summary // "No summary available."')
SESSION_ID=$(echo "$RESPONSE" | jq -r '.sessionId')
DURATION=$(echo "$RESPONSE" | jq -r '.totalDurationMs // 0')
PENDING=$(echo "$RESPONSE" | jq -r '.pendingApproval')

echo
echo "## Auto-Remediation Result"
echo
echo "| Field | Value |"
echo "|-------|-------|"
echo "| Status | **${STATUS}** |"
echo "| Session | \`${SESSION_ID}\` |"
echo "| Duration | ${DURATION}ms |"
echo

if [[ "$PENDING" == "true" ]]; then
  echo "### Pending Approval"
  echo
  echo "$RESPONSE" | jq -r '.approvalRequests[]? | "- **\(.toolName)** on `\(.resourceId)`: \(.description)"'
  echo
  echo "_Review the actions above. The plan requires approval before execution._"
else
  echo "### Actions Taken"
  echo
  echo "$RESPONSE" | jq -r '.actions[]? | "- **\(.toolName)** → \(.status): \(.output // "done")"'
fi

echo
echo "### Summary"
echo
echo "$SUMMARY"
