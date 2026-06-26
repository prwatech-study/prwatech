# Curriculum image migration — EC2 quick start

> **Full plan:** [`docs/curriculum-image-migration-plan.md`](../docs/curriculum-image-migration-plan.md)

Mongo URI is read automatically from `skillama.mongodb.uri` in `src/main/resources/application.properties` (same as backend).

## Run on EC2 (recommended)

```bash
cd ~/Prwatech/Webservices/prwatech
chmod +x scripts/migrate-curriculum-images-run.sh

# 1. Backup
bash scripts/migrate-curriculum-images-run.sh backup

# 2. Plan (pilot: Python Testing)
bash scripts/migrate-curriculum-images-run.sh plan --course-id=6a016428169c87139332057f

# 3. Dry-run
bash scripts/migrate-curriculum-images-run.sh dry-run

# 4. Execute (type YES when prompted)
bash scripts/migrate-curriculum-images-run.sh execute

# 5. Rollback if needed (type ROLLBACK)
bash scripts/migrate-curriculum-images-run.sh rollback
```

## All courses

```bash
bash scripts/migrate-curriculum-images-run.sh backup
bash scripts/migrate-curriculum-images-run.sh plan
bash scripts/migrate-curriculum-images-run.sh dry-run
bash scripts/migrate-curriculum-images-run.sh execute
```

## Backup location

`backups/curriculum-image-migration/<timestamp>/` — symlink `LATEST` points to most recent.

## Prerequisites

- Run on EC2 (or host with AWS CLI + IAM for `presentation-image-courses`)
- `mongosh`, `mongodump`, `mongorestore`, `jq`, `aws`
