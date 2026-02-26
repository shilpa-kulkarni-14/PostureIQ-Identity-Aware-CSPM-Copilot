#!/usr/bin/env bash
# /postureiq identities — List high-risk IAM identities
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_lib.sh"

RESPONSE=$(api_get "/api/identities/high-risk") || exit $?

COUNT=$(echo "$RESPONSE" | jq 'length')
if (( COUNT == 0 )); then
  echo "No high-risk identities found."
  exit 0
fi

echo "## High-Risk IAM Identities"
echo
echo "| Identity | Type | Risk Score | Findings | High Severity |"
echo "|----------|------|------------|----------|---------------|"

echo "$RESPONSE" | jq -r '.[] | [
  .identityName,
  .identityType,
  .riskScore,
  .findingCount,
  .highSeverityCount
] | @tsv' | while IFS=$'\t' read -r name itype score findings high; do
  echo "| \`${name}\` | ${itype} | **${score}** | ${findings} | ${high} |"
done

echo
echo "_Total: ${COUNT} high-risk identities_"
