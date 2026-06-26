# Curriculum image storage — migration plan

> **This is the main document.** Read this for background, decisions, safety checks, batch order, rollback, and FAQ.  
> For copy-paste commands only on EC2, use [`scripts/CURRICULUM_IMAGE_MIGRATION.md`](../scripts/CURRICULUM_IMAGE_MIGRATION.md).

This document explains how Skillama stores lesson images in S3 and MongoDB, why legacy paths caused collisions, what the **new canonical structure** is, and how to **migrate ~584 images automatically** without manual re-upload in the admin panel.

**Audience:** DevOps, backend developers, admins running one-time migration on EC2.

**Related code:**

| Area | Location |
|------|----------|
| S3 upload (canonical key) | `src/main/java/.../FileStorageServiceImpl.java` → `uploadImageForSubmoduleById` |
| Admin upload API | `AdminCurriculumImageController.java` |
| Frontend upload | `skillama-lms` → `src/app/admin/curriculum/page.js`, `src/store/Sagas/Saga.js` |
| Migration scripts | `scripts/migrate-curriculum-images-plan.js`, `scripts/migrate-curriculum-images-execute.sh` |
| Audit scripts | `scripts/mongo-audit-all-courses-image-folders.js` |

---

## 1. Overview

### What is stored where

| Layer | Field / path | Purpose |
|-------|----------------|---------|
| **MongoDB** | `course_curricula.submodules[].imagePath` | Full image URL the LMS loads for each lesson |
| **S3 bucket** | `presentation-image-courses` | Actual image bytes |

The LMS does **not** care which S3 folder is used — it reads whatever URL is in `imagePath`. Migration only changes that URL (and copies the file in S3 when needed).

### S3 bucket layout (high level)

```
presentation-image-courses/
├── curriculum/images/          ← legacy flat uploads
├── courses/{courseId}/         ← structured per course
│   ├── modules/{moduleId}/submodules/{idx}/slides/   ← NEW canonical
│   ├── modules/{MM}/lessons/{LL}/slides/               ← legacy order-based
│   └── {courseId}/social/                              ← course thumbnails
└── demo-video/                 ← demo videos (separate from lesson slides)
```

Study materials (PDF, etc.) use a **different bucket**: `skillama-course-materials`.

---

## 2. The problem: legacy collisions

### 2.1 Order-based paths (`courses/…/lessons/…`)

Older uploads used **module order** and **lesson order** (not unique IDs):

```
courses/{courseId}/modules/03/lessons/01/slides/01.png
```

If two modules both had `order: 3`, or two lessons shared the same order inside a module, they mapped to the **same S3 key**. Uploading/replacing one lesson’s image overwrote another lesson’s file.

### 2.2 Flat generic paths (`curriculum/images/…`)

Early uploads used random filenames:

```
curriculum/images/20260130-110535-000c1608.png
```

The **same URL** was often saved on **multiple** submodules in MongoDB. That did not collide in S3 (one file), but many unrelated lessons pointed at one object — confusing to manage and risky when deleting/replacing.

### 2.3 Symptoms

- Wrong image appears on a lesson after another lesson was edited
- Audit shows many submodules sharing identical `imagePath`
- Audit shows duplicate normalized paths under `courses/…/lessons/…`

---

## 3. The solution: canonical structure

### 3.1 New path pattern (since Jun 2026)

Every submodule gets a **unique, stable** S3 key:

```
courses/{courseId}/modules/{moduleId}/submodules/{submoduleIndex}/slides/01.{ext}
```

| Segment | Source | Example |
|---------|--------|---------|
| `{courseId}` | `course_curricula.courseId` | `6817bbe4cb6b8135daecc428` |
| `{moduleId}` | `course_curricula._id` (module document) | `6817d523d236429528da7e17` |
| `{submoduleIndex}` | Array index + 1, zero-padded (`01`, `02`, …) | `01` |
| `slides/01` | Slide number (usually `01`) | `01.png` |
| `{ext}` | From original file | `.png`, `.jpg`, … |

**Full URL example:**

```
https://presentation-image-courses.s3.ap-south-1.amazonaws.com/
  courses/6817bbe4cb6b8135daecc428/
    modules/6817d523d236429528da7e17/
      submodules/01/slides/01.png?v=1719491234567
```

### 3.2 Why this avoids collisions

- `{moduleId}` is unique per module (Mongo `_id`)
- `{submoduleIndex}` is unique within that module’s `submodules` array
- **No two submodules** can target the same canonical key unless data is corrupt (migration plan detects and aborts)

### 3.3 Ongoing uploads (after code change)

| Admin action | S3 destination |
|--------------|----------------|
| Edit existing submodule → upload image | Canonical `courses/…/submodules/…` |
| Add new submodule → upload image | Canonical (uses next array index) |
| Paste URL manually | Whatever URL is pasted |
| Course thumbnail | `courses/{courseId}/social/…` (different purpose) |

Frontend shows **amber** “legacy — re-upload recommended” for non-canonical `imagePath` values in the curriculum editor.

---

## 4. Current platform state (audit snapshot)

From MongoDB audit across all courses with images:

| Metric | Value |
|--------|------:|
| Total submodule images | **586** |
| Already canonical | **2** (~0.3%) |
| Legacy (to migrate) | **584** |

### Per course

| Course | Canonical | Legacy `lessons/slides` | Legacy `curriculum/images` | Total |
|--------|----------:|------------------------:|---------------------------:|------:|
| Python Course | 2 | 57 | 182 | 241 |
| Advanced Python | 0 | 124 | 8 | 132 |
| Python Testing | 0 | 122 | 0 | 122 |
| Data Science | 0 | 0 | 89 | 89 |
| Unknown (`69f5c54c…`) | 0 | 0 | 2 | 2 |

Re-run audit anytime:

```bash
mongosh "$MONGO_URI" --quiet --file scripts/mongo-audit-all-courses-image-folders.js
```

Or quick aggregate (all courses):

```javascript
db.course_curricula.aggregate([
  { $unwind: "$submodules" },
  { $match: { "submodules.imagePath": { $exists: true, $nin: [null, ""] } } },
  {
    $project: {
      folder: {
        $switch: {
          branches: [
            { case: { $regexMatch: { input: "$submodules.imagePath", regex: "/submodules/.*/slides/" } }, then: "canonical" },
            { case: { $regexMatch: { input: "$submodules.imagePath", regex: "/lessons/.*/slides/" } }, then: "legacy-lessons" },
            { case: { $regexMatch: { input: "$submodules.imagePath", regex: "/curriculum/images/" } }, then: "legacy-curriculum" }
          ],
          default: "other"
        }
      }
    }
  },
  { $group: { _id: "$folder", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
])
```

---

## 5. Migration strategy

### 5.1 Options compared

| Approach | Effort | Collision-safe | Recommended |
|----------|--------|----------------|-------------|
| **A. Automated S3 copy + Mongo update** | Low (~1–2 hours) | Yes (scripted) | **Yes** |
| B. Manual re-upload in admin (584×) | Very high (~5+ hours) | Yes if canonical upload | No |
| C. Leave legacy as-is | None | N/A (old URLs keep working) | OK short-term |

### 5.2 What automated migration does

For each submodule with a non-canonical `imagePath`:

1. Parse the **source S3 key** from the existing URL
2. Compute the **canonical target key** from `courseId` + `moduleId` + submodule array index
3. **Copy** object in S3 (`aws s3 cp` — server-side, fast)
4. **Update** only that submodule’s `imagePath` in MongoDB (by array index)

If **N submodules shared the same legacy URL**, the script creates **N separate S3 objects** at **N canonical keys** — fixing the shared-URL problem.

### 5.3 What we do **not** do

- We do **not** update MongoDB by matching `imagePath` (that would re-link many lessons to one row incorrectly)
- We do **not** delete old S3 objects during migration (rollback safety)
- We do **not** run execute if the plan detects two submodules targeting the same canonical key

---

## 6. Prerequisites

### AWS IAM (EC2 instance role or user)

On bucket `presentation-image-courses` in `ap-south-1`:

- `s3:GetObject`
- `s3:PutObject`
- `s3:CopyObject` (or use `cp` which needs read + write)
- `s3:ListBucket` (optional, for audits)

Include `skillama-course-materials` only if migrating study materials (not part of this plan).

### Tools on EC2 (or ops machine)

- `mongosh`
- `aws` CLI
- `jq`
- `bash`

### Backup (required before `--execute`)

```bash
mongodump --uri="$MONGO_URI" --db=skillamaDB --collection=course_curricula \
  --out=/backup/skillama-$(date +%Y%m%d)
```

---

## 7. Step-by-step runbook

### Recommended: wrapper script (auto URI from backend)

Mongo URI is read from `skillama.mongodb.uri` in `src/main/resources/application.properties` — no manual paste.

```bash
cd ~/Prwatech/Webservices/prwatech
chmod +x scripts/migrate-curriculum-images-run.sh

bash scripts/migrate-curriculum-images-run.sh backup
bash scripts/migrate-curriculum-images-run.sh plan --course-id=6a016428169c87139332057f
bash scripts/migrate-curriculum-images-run.sh dry-run
bash scripts/migrate-curriculum-images-run.sh execute    # prompts: type YES
# if needed:
bash scripts/migrate-curriculum-images-run.sh rollback     # prompts: type ROLLBACK
```

Backups: `backups/curriculum-image-migration/<timestamp>/` (symlink `LATEST`).

### Manual runbook (alternative)

#### Phase 0 — Confirm IAM and backup

```bash
aws s3 ls s3://presentation-image-courses/courses/ --region ap-south-1 | head
mongodump --uri="$MONGO_URI" --db=skillamaDB --collection=course_curricula --out=/backup/pre-image-migration
```

### Phase 1 — Generate migration plan

Set connection string:

```bash
export MONGO_URI='mongodb://USER:PASS@HOST:27017/skillamaDB?authSource=admin'
```

**Pilot one course** (recommended first: Python Testing — 122 images, single legacy type):

```bash
cd /path/to/prwatech

COURSE_ID=6a016428169c87139332057f mongosh "$MONGO_URI" \
  --quiet --file scripts/migrate-curriculum-images-plan.js \
  > /tmp/plan-python-testing.json
```

Review summary:

```bash
jq '.summary' /tmp/plan-python-testing.json
```

Required before execute:

```json
{
  "safeToExecute": true,
  "planningErrors": 0,
  "toMigrate": 122
}
```

Inspect shared legacy groups (will be split):

```bash
jq '.sharedLegacyUrlSamples, .sharedLegacyNormSamples' /tmp/plan-python-testing.json
```

**Full platform plan:**

```bash
mongosh "$MONGO_URI" --quiet --file scripts/migrate-curriculum-images-plan.js \
  > /tmp/plan-all.json
```

### Phase 2 — Dry-run execute

```bash
chmod +x scripts/migrate-curriculum-images-execute.sh

bash scripts/migrate-curriculum-images-execute.sh /tmp/plan-python-testing.json --dry-run
```

Confirm log shows expected `copy: oldKey → newKey` lines and no missing-source errors.

### Phase 3 — Execute migration

```bash
bash scripts/migrate-curriculum-images-execute.sh /tmp/plan-python-testing.json --execute
```

### Phase 4 — Verify

**MongoDB:**

```bash
COURSE_ID=6a016428169c87139332057f mongosh "$MONGO_URI" \
  --quiet --file scripts/mongo-audit-all-courses-image-folders.js
```

Expect `canonical` count = total images for that course; `needsReupload` = 0.

**LMS:** Open 5–10 random lessons in the course — images should load. Hard-refresh if cached.

### Phase 5 — Repeat per course

| Batch | Course ID | Images |
|-------|-----------|-------:|
| 1 | `6a016428169c87139332057f` (Python Testing) | 122 |
| 2 | `69c8b32519c6fc7027d828a0` (Data Science) | 89 |
| 3 | `69f98d0219c6fc7027d82f68` (Advanced Python) | 132 |
| 4 | `6817bbe4cb6b8135daecc428` (Python Course) | 239 |
| 5 | `69f5c54c19c6fc7027d82f4c` (orphan — verify course exists) | 2 |

Or run `plan-all.json` once if pilot succeeded.

### Phase 6 — Cleanup (optional, later)

After 1–2 weeks of stable LMS:

- Add S3 lifecycle rule to expire unused `curriculum/images/*` prefixes (only after confirming no `imagePath` still references them)
- Or leave old objects indefinitely (small storage cost)

---

## 8. Collision safety reference

| Scenario | Migration behavior |
|----------|-------------------|
| Submodule already canonical | **Skip** |
| URL not on `presentation-image-courses` | **Skip** (external / local) |
| N submodules, same legacy `imagePath` | **N copies** to N canonical keys |
| N submodules, same legacy `lessons/MM/LL` path | **N copies** to N canonical keys |
| Two submodules → same computed canonical key | **Plan aborts** (`DUPLICATE_CANONICAL_TARGET`) |
| Target S3 key already exists | Overwrite with this lesson’s copy (logged) |
| Source missing in S3 | **Fail** row; logged; continue others |
| MongoDB update | `updateOne` on `submodules.{idx}.imagePath` only |

---

## 9. Rollback

### Automatic (recommended)

```bash
cd ~/Prwatech/Webservices/prwatech
bash scripts/migrate-curriculum-images-run.sh rollback
```

Prompts `ROLLBACK`, then restores `course_curricula` from the latest backup in `backups/curriculum-image-migration/LATEST`.

Restore a specific backup:

```bash
bash scripts/migrate-curriculum-images-run.sh rollback backups/curriculum-image-migration/20260626-143000
```

### Manual mongorestore

```bash
# Use URI from application.properties or export MONGO_URI
mongorestore --uri="$MONGO_URI" --db=skillamaDB --collection=course_curricula --drop \
  /path/to/backup/skillamaDB
```

### After rollback

1. MongoDB `imagePath` values match pre-migration backup — LMS shows old URLs again.
2. Old S3 objects were **not** deleted — legacy URLs work immediately.
3. New canonical S3 copies (if any were created) remain in the bucket but are unused — optional cleanup later.

---

## 10. Troubleshooting

| Issue | Action |
|-------|--------|
| `safeToExecute: false` | Read `errors` in plan JSON; fix duplicate canonical targets in data |
| `source object missing in S3` | Submodule points at deleted file — fix or clear `imagePath` manually |
| `Access Denied` on S3 | Extend IAM role for `presentation-image-courses` |
| Images 403 in browser | Bucket policy / public read — separate from migration |
| Plan empty for course | No `imagePath` on submodules, or wrong `COURSE_ID` |

---

## 11. FAQ

**Q: Do learners need to do anything?**  
No. URLs in MongoDB change; the LMS loads the new URL on next visit.

**Q: Must we migrate?**  
No. Legacy URLs keep working. Migration is for consistency and to prevent future collision classes.

**Q: Will new uploads use canonical paths?**  
Yes — admin curriculum upload uses `uploadImageForSubmoduleById` when `moduleId` + index are known.

**Q: What about course thumbnails?**  
Separate path: `courses/{courseId}/social/…` — not part of this lesson migration.

**Q: How long does full migration take?**  
Roughly 10–30 minutes for 584 images (S3 copy + Mongo updates), depending on network.

---

## 12. Script reference

| File | Role |
|------|------|
| `scripts/migrate-curriculum-images-run.sh` | **Main entry** — backup, plan, dry-run, execute, rollback |
| `scripts/_lib/read-skillama-mongo-uri.sh` | Loads URI from `application.properties` |
| `scripts/migrate-curriculum-images-plan.js` | Build JSON plan; collision checks |
| `scripts/migrate-curriculum-images-execute.sh` | Low-level `--dry-run` or `--execute` |
| `scripts/mongo-audit-all-courses-image-folders.js` | Per-course folder breakdown + re-upload checklist |
| `scripts/mongo-audit-curriculum-image-folders.js` | Simpler per-course or all-courses audit |
| `scripts/mongo-find-duplicate-curriculum-images.js` | Duplicate `imagePath` / order collision analysis |

**Environment variables:**

| Variable | Used by | Default |
|----------|---------|---------|
| `skillama.mongodb.uri` | run.sh (from properties) | backend config |
| `MONGO_URI` | execute.sh (optional override) | from properties via run.sh |
| `COURSE_ID` | plan.js | all courses |
| `S3_BUCKET` | execute.sh | `presentation-image-courses` |
| `AWS_REGION` | execute.sh | `ap-south-1` |

---

## 13. Document history

| Date | Change |
|------|--------|
| 2026-06 | Canonical path (`moduleId` + submodule index); migration scripts; platform audit (586 images, 584 legacy) |
