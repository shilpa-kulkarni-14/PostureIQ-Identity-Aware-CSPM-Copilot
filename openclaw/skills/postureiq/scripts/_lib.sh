#!/usr/bin/env bash
# PostureIQ shared library — auth, HTTP helpers, formatting
set -euo pipefail

# ── Required Environment ─────────────────────────────────────────────
: "${POSTUREIQ_API_URL:?Environment variable POSTUREIQ_API_URL is required}"
: "${POSTUREIQ_USERNAME:?Environment variable POSTUREIQ_USERNAME is required}"
: "${POSTUREIQ_PASSWORD:?Environment variable POSTUREIQ_PASSWORD is required}"

TOKEN_FILE="/tmp/.postureiq_token_$(id -u)"
TOKEN_TTL=82800  # 23 hours in seconds

# ── Authentication ───────────────────────────────────────────────────

get_token() {
  # Return cached token if still valid
  if [[ -f "$TOKEN_FILE" ]]; then
    local age=$(( $(date +%s) - $(stat -f %m "$TOKEN_FILE" 2>/dev/null || stat -c %Y "$TOKEN_FILE" 2>/dev/null) ))
    if (( age < TOKEN_TTL )); then
      cat "$TOKEN_FILE"
      return 0
    fi
  fi

  local response
  response=$(curl -sf -X POST "${POSTUREIQ_API_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${POSTUREIQ_USERNAME}\",\"password\":\"${POSTUREIQ_PASSWORD}\"}" 2>&1) || {
    echo "Error: Authentication failed. Check POSTUREIQ_API_URL, POSTUREIQ_USERNAME, and POSTUREIQ_PASSWORD." >&2
    return 2
  }

  local token
  token=$(echo "$response" | jq -r '.token // empty')
  if [[ -z "$token" ]]; then
    echo "Error: No token in auth response." >&2
    return 2
  fi

  echo "$token" > "$TOKEN_FILE"
  chmod 600 "$TOKEN_FILE"
  echo "$token"
}

# ── HTTP Helpers ─────────────────────────────────────────────────────

api_get() {
  local endpoint="$1"
  local token
  token=$(get_token) || return $?

  local response
  response=$(curl -sf -X GET "${POSTUREIQ_API_URL}${endpoint}" \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: application/json" 2>&1) || {
    echo "Error: GET ${endpoint} failed." >&2
    return 2
  }
  check_error "$response" || return $?
  echo "$response"
}

api_post() {
  local endpoint="$1"
  local body="${2:-{}}"
  local token
  token=$(get_token) || return $?

  local response
  response=$(curl -sf -X POST "${POSTUREIQ_API_URL}${endpoint}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -d "$body" 2>&1) || {
    echo "Error: POST ${endpoint} failed." >&2
    return 2
  }
  check_error "$response" || return $?
  echo "$response"
}

api_download() {
  local endpoint="$1"
  local output_file="$2"
  local token
  token=$(get_token) || return $?

  curl -sf -X GET "${POSTUREIQ_API_URL}${endpoint}" \
    -H "Authorization: Bearer ${token}" \
    -o "$output_file" 2>&1 || {
    echo "Error: Download from ${endpoint} failed." >&2
    return 2
  }
}

# ── Formatting ───────────────────────────────────────────────────────

format_severity() {
  local sev
  sev=$(echo "$1" | tr '[:lower:]' '[:upper:]')
  case "$sev" in
    CRITICAL) echo "🔴 CRITICAL" ;;
    HIGH)     echo "🟠 HIGH" ;;
    MEDIUM)   echo "🟡 MEDIUM" ;;
    LOW)      echo "🔵 LOW" ;;
    *)        echo "⚪ $1" ;;
  esac
}

# ── Utilities ────────────────────────────────────────────────────────

get_latest_scan_id() {
  local scans
  scans=$(api_get "/api/scans") || return $?
  local scan_id
  scan_id=$(echo "$scans" | jq -r '.[0].scanId // empty')
  if [[ -z "$scan_id" ]]; then
    echo "Error: No scans found." >&2
    return 1
  fi
  echo "$scan_id"
}

check_error() {
  local response="$1"
  # Check if response is valid JSON
  if ! echo "$response" | jq empty 2>/dev/null; then
    echo "Error: Invalid JSON response." >&2
    return 2
  fi
  # Check for error fields in response (only on object responses)
  local error
  error=$(echo "$response" | jq -r 'if type == "object" then (.error // empty) else empty end')
  if [[ -n "$error" ]]; then
    local message
    message=$(echo "$response" | jq -r '.message // "Unknown error"')
    echo "Error: ${error} — ${message}" >&2
    return 2
  fi
  return 0
}

poll_scan() {
  local scan_id="$1"
  local max_wait="${2:-300}"
  local interval=5
  local elapsed=0

  while (( elapsed < max_wait )); do
    local result
    result=$(api_get "/api/scan/${scan_id}") || return $?
    local status
    status=$(echo "$result" | jq -r '.status // empty')

    case "$status" in
      COMPLETED) echo "$result"; return 0 ;;
      FAILED)    echo "Error: Scan ${scan_id} failed." >&2; return 1 ;;
    esac

    sleep "$interval"
    elapsed=$(( elapsed + interval ))
  done

  echo "Error: Scan ${scan_id} timed out after ${max_wait}s." >&2
  return 1
}
