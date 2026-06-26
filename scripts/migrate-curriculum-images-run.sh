#!/usr/bin/env bash
# One entry point: backup → plan → dry-run → execute (curriculum image migration).
# Mongo URI is read from skillama.mongodb.uri in application.properties (same as backend).
#
# Usage:
#   cd ~/Prwatech/Webservices/prwatech
#   bash scripts/migrate-curriculum-images-run.sh backup
#   bash scripts/migrate-curriculum-images-run.sh plan
#   bash scripts/migrate-curriculum-images-run.sh plan --course-id=6a016428169c87139332057f
#   bash scripts/migrate-curriculum-images-run.sh dry-run
#   bash scripts/migrate-curriculum-images-run.sh execute
#   bash scripts/migrate-curriculum-images-run.sh rollback
#   bash scripts/migrate-curriculum-images-run.sh rollback /path/to/specific/backup
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_lib/read-skillama-mongo-uri.sh
source "$SCRIPT_DIR/_lib/read-skillama-mongo-uri.sh"
_read_skillama_mongo_uri

MONGO_URI="$SKILLAMA_MONGO_URI"
BACKUP_ROOT="${BACKUP_ROOT:-$PRWATECH_ROOT/backups/curriculum-image-migration}"
LATEST_LINK="$BACKUP_ROOT/LATEST"
PLAN_DIR="$BACKUP_ROOT/plans"
mkdir -p "$BACKUP_ROOT" "$PLAN_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

cmd="${1:-help}"
shift || true

course_id=""
plan_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --course-id=*)
      course_id="${1#*=}"
      shift
      ;;
    --course-id)
      course_id="${2:?--course-id requires value}"
      shift 2
      ;;
    --plan=*)
      plan_file="${1#*=}"
      shift
      ;;
    --plan)
      plan_file="${2:?--plan requires path}"
      shift 2
      ;;
    *)
      if [[ -z "$plan_file" && -f "$1" ]]; then
        plan_file="$1"
        shift
      else
        echo "Unknown argument: $1" >&2
        exit 1
      fi
      ;;
  esac
done

timestamp() { date +%Y%m%d-%H%M%S; }

do_backup() {
  local ts dir
  ts="$(timestamp)"
  dir="$BACKUP_ROOT/$ts"
  mkdir -p "$dir"

  echo "==> MongoDB backup (course_curricula only)"
  echo "    URI: $(echo "$MONGO_URI" | sed -E 's#(mongodb(\+srv)?://)[^:@]+:[^@]+@#\1***:***@#')"
  echo "    Dir: $dir"

  mongodump \
    --uri="$MONGO_URI" \
    --db=skillamaDB \
    --collection=course_curricula \
    --out="$dir"

  if [[ ! -f "$dir/skillamaDB/course_curricula.bson" ]]; then
    echo -e "${RED}ERROR: backup file missing${NC}" >&2
    exit 1
  fi

  cat > "$dir/BACKUP_INFO.txt" <<EOF
backup_time=$(date -Iseconds)
collection=skillamaDB.course_curricula
host=$(echo "$MONGO_URI" | sed -E 's#mongodb(\+srv)?://[^@]+@([^/]+)/.*#\2#')
rollback_command=bash scripts/migrate-curriculum-images-run.sh rollback $dir
EOF

  ln -sfn "$dir" "$LATEST_LINK"
  echo -e "${GREEN}Backup OK: $dir${NC}"
  echo "Latest symlink: $LATEST_LINK"
}

resolve_plan_file() {
  if [[ -n "$plan_file" ]]; then
    echo "$plan_file"
    return
  fi
  if [[ -L "$PLAN_DIR/LATEST.json" || -f "$PLAN_DIR/LATEST.json" ]]; then
    echo "$PLAN_DIR/LATEST.json"
    return
  fi
  local newest
  newest="$(ls -t "$PLAN_DIR"/*.json 2>/dev/null | head -1 || true)"
  if [[ -n "$newest" ]]; then
    echo "$newest"
    return
  fi
  echo ""
}

do_plan() {
  if [[ ! -L "$LATEST_LINK" && ! -d "$LATEST_LINK" ]]; then
    echo "==> No backup found — creating backup first..."
    do_backup
  else
    echo "==> Using existing backup: $(readlink -f "$LATEST_LINK" 2>/dev/null || echo "$LATEST_LINK")"
  fi

  local ts out
  ts="$(timestamp)"
  out="$PLAN_DIR/plan-${ts}.json"
  if [[ -n "$course_id" ]]; then
    export COURSE_ID="$course_id"
    echo "==> Planning for courseId=$course_id"
  else
    unset COURSE_ID 2>/dev/null || true
    echo "==> Planning for ALL courses"
  fi

  mongosh "$MONGO_URI" --quiet --file "$SCRIPT_DIR/migrate-curriculum-images-plan.js" > "$out"
  ln -sfn "$(basename "$out")" "$PLAN_DIR/LATEST.json"

  echo "==> Plan written: $out"
  jq '.summary' "$out"

  if [[ "$(jq -r '.summary.safeToExecute' "$out")" != "true" ]]; then
    echo -e "${RED}Planning errors — fix before execute:${NC}"
    jq '.errors' "$out"
    exit 1
  fi
}

do_dry_run() {
  local pf
  pf="$(resolve_plan_file)"
  if [[ -z "$pf" || ! -f "$pf" ]]; then
    echo "ERROR: No plan file. Run: bash scripts/migrate-curriculum-images-run.sh plan" >&2
    exit 1
  fi
  echo "==> Dry-run with plan: $pf"
  MONGO_URI="$MONGO_URI" bash "$SCRIPT_DIR/migrate-curriculum-images-execute.sh" "$pf" --dry-run
}

do_execute() {
  local pf backup_path
  pf="$(resolve_plan_file)"
  if [[ -z "$pf" || ! -f "$pf" ]]; then
    echo "ERROR: No plan file. Run: bash scripts/migrate-curriculum-images-run.sh plan" >&2
    exit 1
  fi

  if [[ -L "$LATEST_LINK" || -d "$LATEST_LINK" ]]; then
    backup_path="$(readlink -f "$LATEST_LINK" 2>/dev/null || echo "$LATEST_LINK")"
  else
    echo -e "${RED}ERROR: No backup at $LATEST_LINK — run backup first${NC}" >&2
    exit 1
  fi

  echo ""
  echo "=========================================="
  echo " EXECUTE migration (writes S3 + MongoDB)"
  echo " Plan:   $pf"
  echo " Backup: $backup_path"
  echo " Rollback: bash scripts/migrate-curriculum-images-run.sh rollback"
  echo "=========================================="
  echo ""
  read -r -p "Type YES to continue: " confirm
  if [[ "$confirm" != "YES" ]]; then
    echo "Aborted."
    exit 1
  fi

  MONGO_URI="$MONGO_URI" bash "$SCRIPT_DIR/migrate-curriculum-images-execute.sh" "$pf" --execute

  echo ""
  echo "==> Verify:"
  mongosh "$MONGO_URI" --quiet --file "$SCRIPT_DIR/mongo-audit-all-courses-image-folders.js" | head -40
}

do_rollback() {
  local backup_dir="${1:-}"
  if [[ -z "$backup_dir" ]]; then
    if [[ -L "$LATEST_LINK" || -d "$LATEST_LINK" ]]; then
      backup_dir="$(readlink -f "$LATEST_LINK" 2>/dev/null || echo "$LATEST_LINK")"
    else
      echo "ERROR: Pass backup dir or run backup first. Usage: rollback [/path/to/backup]" >&2
      exit 1
    fi
  fi

  if [[ ! -f "$backup_dir/skillamaDB/course_curricula.bson" ]]; then
    echo "ERROR: Invalid backup (missing course_curricula.bson): $backup_dir" >&2
    exit 1
  fi

  echo ""
  echo -e "${RED}ROLLBACK: restore course_curricula from $backup_dir${NC}"
  echo "This replaces the entire course_curricula collection."
  read -r -p "Type ROLLBACK to continue: " confirm
  if [[ "$confirm" != "ROLLBACK" ]]; then
    echo "Aborted."
    exit 1
  fi

  mongorestore \
    --uri="$MONGO_URI" \
    --db=skillamaDB \
    --collection=course_curricula \
    --drop \
    "$backup_dir/skillamaDB"

  echo -e "${GREEN}Rollback complete.${NC}"
  echo "Note: New S3 objects copied during migration are NOT deleted (harmless)."
  echo "Old imagePath values from backup are restored in MongoDB."
}

show_help() {
  cat <<EOF
Curriculum image migration — reads Mongo URI from application.properties

Commands:
  backup              mongodump course_curricula → backups/curriculum-image-migration/<timestamp>/
  plan                backup (if needed) + generate migration plan JSON
  dry-run             simulate S3 copy + Mongo updates (no writes)
  execute             run migration (requires backup + plan; prompts YES)
  rollback [dir]      restore course_curricula from latest or given backup

Options:
  --course-id=ID      limit plan to one course (pilot)

Examples:
  bash scripts/migrate-curriculum-images-run.sh backup
  bash scripts/migrate-curriculum-images-run.sh plan --course-id=6a016428169c87139332057f
  bash scripts/migrate-curriculum-images-run.sh dry-run
  bash scripts/migrate-curriculum-images-run.sh execute
  bash scripts/migrate-curriculum-images-run.sh rollback

Docs: docs/curriculum-image-migration-plan.md
EOF
}

case "$cmd" in
  backup) do_backup ;;
  plan) do_plan ;;
  dry-run) do_dry_run ;;
  execute) do_execute ;;
  rollback) do_rollback "$@" ;;
  help|-h|--help) show_help ;;
  *)
    echo "Unknown command: $cmd" >&2
    show_help
    exit 1
    ;;
esac
