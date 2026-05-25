#!/usr/bin/env bash
# Backfill legacy Skillama users (planTier null) to FREEMIUM.
#
# Requires OWNER JWT. Defaults to dry-run (no DB writes).
#
# Usage:
#   export BASE_URL="https://your-api.example.com"
#   export OWNER_TOKEN="eyJ..."
#   ./scripts/backfill-legacy-freemium.sh           # dry-run
#   ./scripts/backfill-legacy-freemium.sh --apply   # write changes
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OWNER_TOKEN="${OWNER_TOKEN:-}"

if [[ -z "$OWNER_TOKEN" ]]; then
  echo "ERROR: Set OWNER_TOKEN to an OWNER account JWT." >&2
  exit 1
fi

DRY_RUN="true"
INCLUDE_INACTIVE="true"
ALLOW_MISSING_PHONE="true"
if [[ "${1:-}" == "--apply" ]]; then
  DRY_RUN="false"
  echo "Applying backfill (dryRun=false)..."
else
  echo "Dry-run only (pass --apply to write). Response:"
fi

URL="${BASE_URL%/}/skillama/api/admin/maintenance/backfill-legacy-to-freemium?dryRun=${DRY_RUN}&includeInactive=${INCLUDE_INACTIVE}&allowMissingPhone=${ALLOW_MISSING_PHONE}"

curl -sS -X POST "$URL" \
  -H "Authorization: Bearer ${OWNER_TOKEN}" \
  -H "Content-Type: application/json" | python3 -m json.tool 2>/dev/null || cat

echo ""
