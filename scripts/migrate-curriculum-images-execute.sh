#!/usr/bin/env bash
# Execute curriculum image migration plan (S3 server-side copy + MongoDB imagePath update).
#
# Prerequisites:
#   - aws CLI configured (IAM: s3:GetObject, s3:PutObject, s3:CopyObject on presentation-image-courses)
#   - mongosh access to skillamaDB
#   - Plan JSON from migrate-curriculum-images-plan.js
#
# Usage:
#   # 1) Generate plan (pilot one course)
#   COURSE_ID=6a016428169c87139332057f mongosh "$MONGO_URI" \
#     --quiet --file scripts/migrate-curriculum-images-plan.js > /tmp/plan.json
#
#   # 2) Dry-run execute
#   MONGO_URI='mongodb://...' bash scripts/migrate-curriculum-images-execute.sh /tmp/plan.json --dry-run
#
#   # 3) Real run (after reviewing dry-run log)
#   MONGO_URI='mongodb://...' bash scripts/migrate-curriculum-images-execute.sh /tmp/plan.json --execute
#
set -euo pipefail

PLAN_FILE="${1:?Usage: $0 plan.json [--dry-run|--execute]}"
MODE="${2:---dry-run}"
BUCKET="${S3_BUCKET:-presentation-image-courses}"
REGION="${AWS_REGION:-ap-south-1}"
MONGO_URI="${MONGO_URI:?Set MONGO_URI}"

if [[ ! -f "$PLAN_FILE" ]]; then
  echo "ERROR: Plan file not found: $PLAN_FILE"
  exit 1
fi

if [[ "$MODE" != "--dry-run" && "$MODE" != "--execute" ]]; then
  echo "ERROR: Second arg must be --dry-run or --execute"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required"
  exit 1
fi

SAFE=$(jq -r '.summary.safeToExecute' "$PLAN_FILE")
if [[ "$SAFE" != "true" ]]; then
  echo "ERROR: Plan is not safe to execute (summary.safeToExecute != true). Fix planning errors first."
  jq '.errors' "$PLAN_FILE"
  exit 1
fi

TO_MIGRATE=$(jq '[.plan[] | select(.action=="copy")] | length' "$PLAN_FILE")
echo "==> Mode: $MODE | bucket: $BUCKET | migrations: $TO_MIGRATE"

OK=0
SKIP=0
FAIL=0

while IFS= read -r item; do
  recordId=$(echo "$item" | jq -r '.recordId')
  action=$(echo "$item" | jq -r '.action')
  courseName=$(echo "$item" | jq -r '.courseName // "?"')
  label=$(echo "$item" | jq -r '.label // "?"')

  if [[ "$action" != "copy" ]]; then
    ((SKIP++)) || true
    continue
  fi

  sourceKey=$(echo "$item" | jq -r '.sourceKey')
  newKey=$(echo "$item" | jq -r '.newKey')
  newImagePath=$(echo "$item" | jq -r '.newImagePath')
  moduleId=$(echo "$item" | jq -r '.moduleId')
  submoduleIdx=$(echo "$item" | jq -r '.submoduleIdx')
  shared=$(echo "$item" | jq -r '.sharedLegacyUrl')
  fixes=$(echo "$item" | jq -r '.fixesLegacyCollision')

  src="s3://${BUCKET}/${sourceKey}"
  dest="s3://${BUCKET}/${newKey}"

  echo ""
  echo "── $courseName / $label ($recordId)"
  echo "   copy: $sourceKey"
  echo "     → $newKey"
  [[ "$shared" == "true" ]] && echo "   (split shared legacy URL — own canonical copy)"
  [[ "$fixes" == "true" ]] && echo "   (fixes legacy order-path collision)"

  if ! aws s3api head-object --bucket "$BUCKET" --key "$sourceKey" --region "$REGION" >/dev/null 2>&1; then
    echo "   ERROR: source object missing in S3"
    ((FAIL++)) || true
    continue
  fi

  if aws s3api head-object --bucket "$BUCKET" --key "$newKey" --region "$REGION" >/dev/null 2>&1; then
    echo "   WARN: destination key already exists — will overwrite with same lesson image"
  fi

  if [[ "$MODE" == "--dry-run" ]]; then
    echo "   [dry-run] would copy + update MongoDB"
    ((OK++)) || true
    continue
  fi

  if ! aws s3 cp "$src" "$dest" --region "$REGION" --only-show-errors; then
    echo "   ERROR: S3 copy failed"
    ((FAIL++)) || true
    continue
  fi

  # Update only the targeted submodule (array index) — never batch-update by imagePath (avoids re-collision).
  newPathJson=$(jq -n --arg p "$newImagePath" '$p')
  UPDATE_JS=$(cat <<EOF
const moduleId = "$moduleId";
const idx = $submoduleIdx;
const newPath = $newPathJson;
const res = db.course_curricula.updateOne(
  { _id: moduleId },
  { \$set: { ["submodules." + idx + ".imagePath"]: newPath } }
);
if (res.matchedCount !== 1 || res.modifiedCount !== 1) {
  print("MONGO_UPDATE_WARN matched=" + res.matchedCount + " modified=" + res.modifiedCount);
  quit(2);
}
print("MONGO_OK");
EOF
)

  if ! mongosh "$MONGO_URI" --quiet --eval "$UPDATE_JS" | grep -q MONGO_OK; then
    echo "   ERROR: MongoDB update failed (S3 object copied — manual fix or restore from backup)"
    ((FAIL++)) || true
    continue
  fi

  echo "   OK"
  ((OK++)) || true
done < <(jq -c '.plan[]' "$PLAN_FILE")

echo ""
echo "==> Done: ok=$OK skipped=$SKIP failed=$FAIL"
[[ "$MODE" == "--dry-run" ]] && echo "Re-run with --execute when ready."

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
