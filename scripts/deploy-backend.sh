#!/usr/bin/env bash
# Skillama Java backend (prwatech) — deploy on app server
# Usage: bash scripts/deploy-backend.sh
# Optional env:
#   APP_DIR=~/Prwatech/Webservices/prwatech
#   PM2_NAME=prwatech-java
#   GIT_BRANCH=adminDevelopment
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/Prwatech/Webservices/prwatech}"
PM2_NAME="${PM2_NAME:-prwatech-java}"
GIT_BRANCH="${GIT_BRANCH:-adminDevelopment}"

cd "$APP_DIR"

echo "==> [1/7] Malware / miner cleanup (same host as frontend — keep enabled)"
pkill -9 -f xmrig 2>/dev/null || true
pkill -9 -f scanner_linux 2>/dev/null || true
pkill -9 -f wget 2>/dev/null || true
rm -f scanner_linux xmrig.tar.gz
rm -rf xmrig-6.21.0
rm -f data.log exploited.log failed.log monitor.log scanner_deployed.log

echo "==> [2/7] Pull latest code ($GIT_BRANCH)"
git fetch origin "$GIT_BRANCH"
git pull origin "$GIT_BRANCH"

echo "==> [3/7] Preflight checks"
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java not found on PATH"
  exit 1
fi
java -version

if [[ ! -x ./gradlew ]]; then
  echo "ERROR: ./gradlew not found or not executable in $APP_DIR"
  exit 1
fi

echo "==> [4/7] Run tests + build (clean build includes test task)"
# Fails deploy if tests fail — catches security regressions before restart
./gradlew clean build --no-daemon

JAR_FILE="$(ls -1 build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1 || true)"
if [[ -z "$JAR_FILE" || ! -f "$JAR_FILE" ]]; then
  echo "ERROR: No runnable JAR found under build/libs/"
  exit 1
fi
echo "    Built: $JAR_FILE"

echo "==> [5/7] Restart PM2 ($PM2_NAME)"
if ! pm2 describe "$PM2_NAME" >/dev/null 2>&1; then
  echo "ERROR: PM2 process '$PM2_NAME' not found."
  echo "       Create it once on this server, then re-run this script."
  exit 1
fi
pm2 restart "$PM2_NAME"

echo "==> [6/7] Save PM2 process list"
pm2 save

echo "==> [7/7] Recent logs (non-blocking)"
pm2 logs "$PM2_NAME" --lines 50 --nostream

echo "Deploy complete: $PM2_NAME"
