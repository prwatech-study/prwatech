#!/usr/bin/env bash
# Resolve the Skillama MongoDB URI without reading credentials from source.
# Preference:
#   1. SKILLAMA_MONGODB_URI (or SKILLAMA_MONGO_URI) environment variable
#   2. skillama.mongodb.uri in application.properties, if it is already a URI
#      (not an unresolved ${ENV} placeholder)
# Usage: source scripts/_lib/read-skillama-mongo-uri.sh
# Sets: SKILLAMA_MONGO_URI, PRWATECH_ROOT

_read_skillama_mongo_uri() {
  local root="${PRWATECH_ROOT:-}"
  if [[ -z "$root" ]]; then
    local lib_dir
    lib_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    root="$(cd "$lib_dir/../.." && pwd)"
  fi
  PRWATECH_ROOT="$root"

  if [[ -n "${SKILLAMA_MONGODB_URI:-}" ]]; then
    SKILLAMA_MONGO_URI="$SKILLAMA_MONGODB_URI"
  elif [[ -n "${SKILLAMA_MONGO_URI:-}" ]]; then
    : # already set
  else
    local props="$PRWATECH_ROOT/src/main/resources/application.properties"
    if [[ ! -f "$props" ]]; then
      echo "ERROR: application.properties not found at $props" >&2
      echo "       Set SKILLAMA_MONGODB_URI in the environment instead." >&2
      return 1
    fi

    local line
    line="$(grep -E '^skillama\.mongodb\.uri=' "$props" | tail -1)"
    if [[ -z "$line" ]]; then
      echo "ERROR: skillama.mongodb.uri not set in $props and SKILLAMA_MONGODB_URI is empty" >&2
      return 1
    fi

    SKILLAMA_MONGO_URI="${line#skillama.mongodb.uri=}"
    SKILLAMA_MONGO_URI="${SKILLAMA_MONGO_URI//$'\r'/}"

    # Resolve ${VAR:default} / ${VAR} from the environment; never print values.
    if [[ "$SKILLAMA_MONGO_URI" == \$\{* ]]; then
      local inner="${SKILLAMA_MONGO_URI#\$\{}"
      inner="${inner%\}}"
      local var_name="${inner%%:*}"
      local default_val=""
      if [[ "$inner" == *:* ]]; then
        default_val="${inner#*:}"
      fi
      SKILLAMA_MONGO_URI="${!var_name:-$default_val}"
    fi
  fi

  if [[ -z "$SKILLAMA_MONGO_URI" ]]; then
    echo "ERROR: Mongo URI is empty. Export SKILLAMA_MONGODB_URI." >&2
    return 1
  fi

  export SKILLAMA_MONGO_URI PRWATECH_ROOT
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  _read_skillama_mongo_uri || exit 1
  # Print URI with password redacted (for sanity check only)
  echo "$SKILLAMA_MONGO_URI" | sed -E 's#(mongodb(\+srv)?://)[^:@]+:[^@]+@#\1***:***@#'
fi
