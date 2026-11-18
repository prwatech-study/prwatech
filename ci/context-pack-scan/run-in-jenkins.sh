#!/usr/bin/env bash
# P4 stage body for deploy-skillama / deploy-java / deploy-tutor (dec_axybn6ity2).
# Expects cwd = product repo checkout (Jenkins RelativeTargetDirectory `src`).
set -euo pipefail

SERVICE_ID="${SERVICE_ID:?SERVICE_ID required (lms|backend|ai_tutor)}"
BASE_BRANCH="${BASE_BRANCH:?BASE_BRANCH required}"
BRANCH_NAME="${BRANCH_NAME:-${BRANCH:-}}"
DECISION_ID="${DECISION_ID:-}"
PR_BODY="${PR_BODY:-Deploy ${BRANCH_NAME} (dec citation via branch/DECISION_ID)}"

if [ -z "${HUB_BASE_URL:-}" ] || [ -z "${HUB_CI_TOKEN:-}" ]; then
  echo "context-pack-scan FAIL: HUB_BASE_URL and HUB_CI_TOKEN must be set (Jenkins credentials hub-base-url / hub-ci-token)" >&2
  exit 1
fi

# Live-branch deploys without Decision context skip (do not block routine rollouts).
if [ -z "${DECISION_ID}" ] && ! echo "${BRANCH_NAME}" | grep -Eq 'ctx/dec_[a-z0-9]+'; then
  echo "P4 skipped: no Decision context (BRANCH=${BRANCH_NAME:-empty}, DECISION_ID empty)"
  exit 0
fi

git fetch --no-tags origin "+refs/heads/${BASE_BRANCH}:refs/remotes/origin/${BASE_BRANCH}" || true
DIFF_FILE="$PWD/.ci-pr.diff"
if git rev-parse --verify "origin/${BASE_BRANCH}" >/dev/null 2>&1; then
  git diff --unified=0 "origin/${BASE_BRANCH}...HEAD" > "$DIFF_FILE" || true
else
  git diff --unified=0 HEAD~1 > "$DIFF_FILE" || git diff --unified=0 > "$DIFF_FILE" || true
fi

if [ ! -s "$DIFF_FILE" ]; then
  echo "P4: empty diff vs ${BASE_BRANCH}; writing citation stub for Decision gate"
  {
    echo "diff --git a/HUB_DECISION.txt b/HUB_DECISION.txt"
    echo "+++ b/HUB_DECISION.txt"
    if [ -n "${DECISION_ID}" ]; then
      echo "+${DECISION_ID}"
    else
      echo "+${BRANCH_NAME}"
    fi
  } > "$DIFF_FILE"
fi

export SERVICE_ID BRANCH_NAME PR_BODY DECISION_ID
export GIT_DIFF_FILE="$DIFF_FILE"

if command -v node >/dev/null 2>&1; then
  node ci/context-pack-scan/scan.mjs
elif command -v docker >/dev/null 2>&1; then
  docker run --rm \
    -v "$PWD:/w" -w /w \
    -e HUB_BASE_URL -e HUB_CI_TOKEN -e SERVICE_ID -e DECISION_ID \
    -e BRANCH_NAME -e PR_BODY -e GIT_DIFF_FILE=/w/.ci-pr.diff \
    node:20-alpine \
    node ci/context-pack-scan/scan.mjs
else
  echo "context-pack-scan FAIL: node or docker required on Jenkins agent" >&2
  exit 1
fi
