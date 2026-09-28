#!/usr/bin/env bash
# Skillama Java backend (prwatech) — deploy on app server
# Usage: bash scripts/deploy-backend.sh
# Optional env:
#   APP_DIR=~/Prwatech/Webservices/prwatech
#   PM2_NAME=prwatech-java
#   GIT_BRANCH=adminDevelopment
#   SECRETS_FILE=~/secrets/prwatech.env
#   AI_BASE_URL=https://ai.prwatech.com   # used only for KB sync smoke test
#
# Order (live app untouched until the end):
#   pull → ./gradlew clean build (runs tests first) → kb secret preflight →
#   pm2 restart → process env + /kb/sync smoke → save
# If tests or build fail, set -e stops the script — PM2 keeps serving the old JAR.
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/Prwatech/Webservices/prwatech}"
PM2_NAME="${PM2_NAME:-prwatech-java}"
GIT_BRANCH="${GIT_BRANCH:-adminDevelopment}"
SECRETS_FILE="${SECRETS_FILE:-$HOME/secrets/prwatech.env}"
AI_BASE_URL="${AI_BASE_URL:-https://ai.prwatech.com}"

# Read KEY=value from an env file. Prints value only (no KEY=). Empty if missing.
read_env_file_value() {
  local file="$1" key="$2" line val
  [ -f "$file" ] || return 0
  line="$(grep -E "^${key}=" "$file" | tail -1 || true)"
  [ -n "$line" ] || return 0
  val="${line#*=}"
  # strip optional surrounding quotes
  val="${val%\"}"
  val="${val#\"}"
  val="${val%\'}"
  val="${val#\'}"
  printf '%s' "$val"
}

require_nonempty_in_secrets_file() {
  local key="$1" val
  if [ ! -f "$SECRETS_FILE" ]; then
    echo "ERROR: secrets file not found: $SECRETS_FILE"
    echo "       Create it (chmod 600) with KB_SYNC_SECRET matching ai-tutor systemd."
    exit 1
  fi
  val="$(read_env_file_value "$SECRETS_FILE" "$key")"
  if [ -z "$val" ]; then
    echo "ERROR: $key is missing or empty in $SECRETS_FILE"
    echo "       Set the same non-empty value as ai-tutor's systemd Environment=\"$key=...\"."
    echo "       Then re-run this script (do not restart with an empty secret)."
    exit 1
  fi
  echo "    $key is set in secrets file (length ${#val})"
}

cd "$APP_DIR"

echo "==> [1/8] Malware / miner cleanup (same host as frontend — keep enabled)"
pkill -9 -f xmrig 2>/dev/null || true
pkill -9 -f scanner_linux 2>/dev/null || true
pkill -9 -f wget 2>/dev/null || true
rm -f scanner_linux xmrig.tar.gz
rm -rf xmrig-6.21.0
rm -f data.log exploited.log failed.log monitor.log scanner_deployed.log

echo "==> [2/8] Pull latest code ($GIT_BRANCH)"
git fetch origin "$GIT_BRANCH"
git pull origin "$GIT_BRANCH"

echo "==> [3/8] Preflight checks"
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java not found on PATH"
  exit 1
fi
java -version

if [[ ! -x ./gradlew ]]; then
  echo "ERROR: ./gradlew not found or not executable in $APP_DIR"
  exit 1
fi

echo "==> [4/8] Run tests + build (clean build includes test task)"
# Fails deploy if tests fail — catches security regressions before restart
./gradlew clean build --no-daemon

JAR_FILE="$(ls -1 build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1 || true)"
if [[ -z "$JAR_FILE" || ! -f "$JAR_FILE" ]]; then
  echo "ERROR: No runnable JAR found under build/libs/"
  exit 1
fi
echo "    Built: $JAR_FILE"

echo "==> [5/8] KB sync secret preflight (must match ai-tutor; value not printed)"
require_nonempty_in_secrets_file "KB_SYNC_SECRET"

echo "==> [6/8] Restart PM2 ($PM2_NAME)"
if ! pm2 describe "$PM2_NAME" >/dev/null 2>&1; then
  echo "ERROR: PM2 process '$PM2_NAME' not found."
  echo "       Create it once on this server, then re-run this script."
  exit 1
fi
pm2 restart "$PM2_NAME"
sleep 3

echo "==> [7/8] Verify process KB_SYNC_SECRET + smoke /kb/sync"
JAVA_PID="$(pgrep -f 'prwatech-0.0.1-SNAPSHOT.jar' | head -1 || true)"
if [ -z "$JAVA_PID" ]; then
  echo "ERROR: Could not find prwatech JAR PID after restart"
  pm2 logs "$PM2_NAME" --lines 40 --nostream || true
  exit 1
fi
PROC_ENV="$(mktemp)"
trap 'rm -f "$PROC_ENV"' EXIT
if ! tr '\0' '\n' < "/proc/$JAVA_PID/environ" > "$PROC_ENV" 2>/dev/null; then
  # process may be owned such that we need sudo
  if ! sudo tr '\0' '\n' < "/proc/$JAVA_PID/environ" > "$PROC_ENV"; then
    echo "ERROR: Could not read environ for PID $JAVA_PID"
    exit 1
  fi
fi
PROC_SECRET_LINE="$(grep -E '^KB_SYNC_SECRET=' "$PROC_ENV" | tail -1 || true)"
PROC_SECRET="${PROC_SECRET_LINE#*=}"
if [ -z "$PROC_SECRET" ]; then
  echo "ERROR: KB_SYNC_SECRET is missing or empty in running Java process env (PID $JAVA_PID)"
  echo "       PM2 must source $SECRETS_FILE (see: pm2 show $PM2_NAME)."
  exit 1
fi
echo "    Process KB_SYNC_SECRET is set (length ${#PROC_SECRET})"

# Smoke: empty body is fine; we only care that the internal key is accepted (not 401).
# Network / 5xx → warn (other host or Bedrock), do not roll back a good Java deploy.
KB_SECRET="$(read_env_file_value "$SECRETS_FILE" "KB_SYNC_SECRET")"
# Prefer SKILLAMA_AI_BASE_URL from secrets if present
SECRETS_AI_URL="$(read_env_file_value "$SECRETS_FILE" "SKILLAMA_AI_BASE_URL")"
SMOKE_BASE="${SECRETS_AI_URL:-$AI_BASE_URL}"
SMOKE_BASE="${SMOKE_BASE%/}"
SMOKE_OUT="$(mktemp)"
set +e
HTTP_CODE="$(curl -sS -m 20 -o "$SMOKE_OUT" -w '%{http_code}' \
  -X POST "${SMOKE_BASE}/kb/sync" \
  -H 'Content-Type: application/json' \
  -H "X-Internal-Key: ${KB_SECRET}" \
  -d '{}' 2>/dev/null)"
CURL_EC=$?
set -e
rm -f "$SMOKE_OUT"
# Unset so secret does not linger in this shell's environment listing
unset KB_SECRET PROC_SECRET

if [ "$CURL_EC" -ne 0 ] || [ -z "$HTTP_CODE" ] || [ "$HTTP_CODE" = "000" ]; then
  echo "    WARN: could not reach ${SMOKE_BASE}/kb/sync (curl exit $CURL_EC, http=${HTTP_CODE:-none})"
  echo "          Deploy continues — verify ai-tutor is up and AI Dev Mode routing if needed."
elif [ "$HTTP_CODE" = "401" ]; then
  echo "ERROR: ${SMOKE_BASE}/kb/sync returned 401 — KB_SYNC_SECRET does not match ai-tutor"
  echo "       Align $SECRETS_FILE with ai-tutor systemd Environment=KB_SYNC_SECRET, then restart both."
  exit 1
else
  echo "    Smoke /kb/sync HTTP $HTTP_CODE (401 would fail deploy; other codes OK for this check)"
fi

echo "==> [8/8] Save PM2 process list + recent logs"
pm2 save
pm2 logs "$PM2_NAME" --lines 50 --nostream

echo "Deploy complete: $PM2_NAME"
