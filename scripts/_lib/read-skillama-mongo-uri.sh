#!/usr/bin/env bash
# Read skillama.mongodb.uri from Spring application.properties (same as Java backend).
# Usage: source scripts/_lib/read-skillama-mongo-uri.sh
# Sets: SKILLAMA_MONGO_URI, PRWATECH_ROOT

_read_skillama_mongo_uri() {
  local root="${PRWATECH_ROOT:-}"
  if [[ -z "$root" ]]; then
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
    root="$(cd "$script_dir/../.." && pwd)"
  fi
  PRWATECH_ROOT="$root"

  local props="$PRWATECH_ROOT/src/main/resources/application.properties"
  if [[ ! -f "$props" ]]; then
    echo "ERROR: application.properties not found at $props" >&2
    return 1
  fi

  local line
  line="$(grep -E '^skillama\.mongodb\.uri=' "$props" | tail -1)"
  if [[ -z "$line" ]]; then
    echo "ERROR: skillama.mongodb.uri not set in $props" >&2
    return 1
  fi

  SKILLAMA_MONGO_URI="${line#skillama.mongodb.uri=}"
  SKILLAMA_MONGO_URI="${SKILLAMA_MONGO_URI//$'\r'/}"

  if [[ -z "$SKILLAMA_MONGO_URI" ]]; then
    echo "ERROR: skillama.mongodb.uri is empty" >&2
    return 1
  fi

  export SKILLAMA_MONGO_URI PRWATECH_ROOT
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  _read_skillama_mongo_uri || exit 1
  # Print URI with password redacted (for sanity check only)
  echo "$SKILLAMA_MONGO_URI" | sed -E 's#(mongodb(\+srv)?://)[^:@]+:[^@]+@#\1***:***@#'
fi
