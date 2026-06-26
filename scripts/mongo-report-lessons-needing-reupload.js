/**
 * List lessons whose imagePath shared the same legacy S3 object (need re-upload).
 *
 * Uses BACKUP DB (before migration) — restore once:
 *   mongorestore --uri="$MONGO_URI" --db=skillamaDB_backup --drop \
 *     backups/curriculum-image-migration/20260626-080622/skillamaDB
 *
 *   mongosh "$MONGO_URI" --quiet --file scripts/mongo-report-lessons-needing-reupload.js
 *
 * Optional: COURSE_ID=6a016428... to filter one course
 */

const SOURCE_DB = process.env.SOURCE_DB || "skillamaDB_backup";
const COURSE_FILTER = process.env.COURSE_ID || null;

const source = db.getSiblingDB(SOURCE_DB);

if (!source.getCollectionNames().includes("course_curricula")) {
  print("ERROR: Restore backup to skillamaDB_backup first (see script header).");
  quit(1);
}

const courseNameById = {};
source.courses.find({}, { _id: 1, name: 1 }).forEach((c) => {
  courseNameById[String(c._id)] = c.name || "(unnamed)";
});

const matchStage = COURSE_FILTER ? { $match: { courseId: COURSE_FILTER } } : null;

const rows = source.course_curricula
  .aggregate([
    ...(matchStage ? [matchStage] : []),
    { $unwind: { path: "$submodules", includeArrayIndex: "idx" } },
    { $match: { "submodules.imagePath": { $exists: true, $nin: [null, ""] } } },
    {
      $project: {
        courseId: 1,
        moduleId: { $toString: "$_id" },
        moduleName: 1,
        submoduleIdx: 1,
        label: "$submodules.label",
        imagePath: "$submodules.imagePath",
        normPath: {
          $arrayElemAt: [{ $split: ["$submodules.imagePath", "?"] }, 0],
        },
        folder: {
          $switch: {
            branches: [
              {
                case: {
                  $regexMatch: { input: "$submodules.imagePath", regex: "/curriculum/images/" },
                },
                then: "curriculum/images",
              },
              {
                case: {
                  $regexMatch: {
                    input: "$submodules.imagePath",
                    regex: "/modules/[0-9]{2}/lessons/",
                  },
                },
                then: "legacy-lessons (high collision risk)",
              },
              {
                case: {
                  $regexMatch: {
                    input: "$submodules.imagePath",
                    regex: "/submodules/[0-9]{2}/slides/",
                  },
                },
                then: "canonical",
              },
            ],
            default: "other",
          },
        },
      },
    },
  ])
  .toArray();

const byNorm = {};
rows.forEach((r) => {
  if (!byNorm[r.normPath]) byNorm[r.normPath] = [];
  byNorm[r.normPath].push(r);
});

const needsReupload = [];
const okUnique = [];

rows.forEach((r) => {
  const group = byNorm[r.normPath];
  const sharedLegacy = group.length > 1;
  const legacyLessons = r.folder === "legacy-lessons (high collision risk)";
  const mustReupload = sharedLegacy || legacyLessons;

  const entry = {
    courseId: r.courseId,
    courseName: courseNameById[r.courseId] || r.courseId,
    moduleName: r.moduleName,
    label: r.label,
    submoduleIdx: r.submoduleIdx,
    reason: sharedLegacy
      ? "shared legacy S3 path with " + group.length + " lessons"
      : legacyLessons
        ? "legacy order-based S3 path (collision-prone)"
        : "unique curriculum/images — verify visually",
    imagePath: r.imagePath,
  };

  if (mustReupload) needsReupload.push(entry);
  else okUnique.push(entry);
});

const byCourse = {};
needsReupload.forEach((e) => {
  const k = e.courseId;
  if (!byCourse[k]) {
    byCourse[k] = { courseName: e.courseName, count: 0, lessons: [] };
  }
  byCourse[k].count += 1;
  byCourse[k].lessons.push(e);
});

print("=== Lessons that SHOULD be re-uploaded in admin (from backup analysis) ===\n");
print("Total with images in backup: " + rows.length);
print("Need re-upload (shared path OR legacy lessons/ path): " + needsReupload.length);
print("Likely OK (unique curriculum/images only): " + okUnique.length);
print("");

Object.values(byCourse)
  .sort((a, b) => b.count - a.count)
  .forEach((c) => {
    print("── " + c.courseName + " ── " + c.count + " lesson(s)");
    c.lessons.slice(0, 8).forEach((l) => {
      print("  • " + l.moduleName + " / " + l.label);
      print("    " + l.reason);
    });
    if (c.lessons.length > 8) print("  … +" + (c.lessons.length - 8) + " more");
    print("");
  });

if (COURSE_FILTER) {
  print("=== Full list for course " + COURSE_FILTER + " ===");
  needsReupload.forEach((l) => {
    print(l.moduleName + " / " + l.label + " [" + l.reason + "]");
  });
}
