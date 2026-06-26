/**
 * Audit imagePath storage folder for EVERY course in skillamaDB.
 *
 *   mongosh "mongodb://USER:PASS@HOST:27017/skillamaDB?authSource=admin" \
 *     --file scripts/mongo-audit-all-courses-image-folders.js
 *
 * Canonical target structure (current backend): courses/{courseId}/modules/{moduleId}/submodules/{idx}/slides/01.png
 * Legacy paths flagged for optional re-upload in admin.
 */

const CANONICAL = "courses/submodules/slides";
const LEGACY_ORDER = "courses/lessons/slides";
const LEGACY_GENERIC = "curriculum/images";

function classifyFolder(imagePath) {
  const p = (imagePath || "").split("?")[0];
  if (!p) return "empty";
  if (p.includes("/curriculum/images/")) return LEGACY_GENERIC;
  if (p.includes("/courses/") && p.includes("/submodules/") && p.includes("/slides/"))
    return CANONICAL;
  if (p.includes("/courses/") && p.includes("/lessons/") && p.includes("/slides/"))
    return LEGACY_ORDER;
  if (p.includes("/courses/") && p.includes("/social/")) return "courses/social";
  if (p.includes("/files/") || p.startsWith("uploads/")) return "local";
  if (p.includes("amazonaws.com") || p.includes(".s3.")) return "s3-other";
  return "external";
}

const courseNameById = {};
db.courses.find({}, { _id: 1, name: 1, active: 1 }).forEach((c) => {
  courseNameById[String(c._id)] = {
    name: c.name || "(unnamed)",
    active: c.active !== false,
  };
});

const rows = db.course_curricula
  .aggregate([
    { $unwind: { path: "$submodules", includeArrayIndex: "submoduleIdx" } },
    {
      $match: {
        "submodules.imagePath": { $exists: true, $nin: [null, ""] },
      },
    },
    {
      $project: {
        courseId: 1,
        moduleId: "$_id",
        moduleName: 1,
        label: "$submodules.label",
        submoduleIdx: 1,
        imagePath: "$submodules.imagePath",
      },
    },
  ])
  .toArray();

const byCourse = {};
const globalCounts = {};

rows.forEach((r) => {
  const folder = classifyFolder(r.imagePath);
  globalCounts[folder] = (globalCounts[folder] || 0) + 1;

  const cid = r.courseId || "unknown";
  if (!byCourse[cid]) {
    byCourse[cid] = {
      courseId: cid,
      courseName: courseNameById[cid]?.name || "(no course row)",
      active: courseNameById[cid]?.active,
      folders: {},
      total: 0,
      needsReupload: 0,
    };
  }
  byCourse[cid].folders[folder] = (byCourse[cid].folders[folder] || 0) + 1;
  byCourse[cid].total += 1;
  if (folder !== CANONICAL) byCourse[cid].needsReupload += 1;
});

print("=== Platform-wide image folder summary ===");
print("Submodules with imagePath: " + rows.length);
Object.entries(globalCounts)
  .sort((a, b) => b[1] - a[1])
  .forEach(([folder, count]) => {
    const pct = rows.length ? ((count / rows.length) * 100).toFixed(1) : "0";
    const tag = folder === CANONICAL ? " [TARGET]" : " [legacy — re-upload optional]";
    print("  " + folder + ": " + count + " (" + pct + "%)" + tag);
  });

const legacyTotal = rows.length - (globalCounts[CANONICAL] || 0);
print(
  "\nLegacy / non-canonical: " +
    legacyTotal +
    " of " +
    rows.length +
    " (" +
    (rows.length ? ((legacyTotal / rows.length) * 100).toFixed(1) : "0") +
    "%)"
);
print("Target structure: " + CANONICAL);
print("  courses/{courseId}/modules/{moduleId}/submodules/{idx}/slides/01.png\n");

print("=== Per course (sorted by legacy count desc) ===\n");
const courseList = Object.values(byCourse).sort((a, b) => b.needsReupload - a.needsReupload);

courseList.forEach((c) => {
  const canonical = c.folders[CANONICAL] || 0;
  const pctCanon = c.total ? ((canonical / c.total) * 100).toFixed(0) : "0";
  print(
    "── " +
      c.courseName +
      " (" +
      c.courseId +
      ")" +
      (c.active === false ? " [inactive]" : "") +
      " ──"
  );
  print("  Total images: " + c.total + " | canonical: " + canonical + " (" + pctCanon + "%) | needs re-upload: " + c.needsReupload);
  Object.entries(c.folders)
    .sort((a, b) => b[1] - a[1])
    .forEach(([folder, count]) => print("    " + folder + ": " + count));
  print("");
});

const coursesWithNoImages = Object.keys(courseNameById).filter((id) => !byCourse[id]);
if (coursesWithNoImages.length) {
  print("=== Courses with NO submodule images in DB ===");
  coursesWithNoImages.forEach((id) => {
    const c = courseNameById[id];
    print("  " + (c?.name || id) + " (" + id + ")");
  });
}

print("\n=== Re-upload checklist (non-canonical only) ===");
print("In Admin → Curriculum, open each course below and re-save lesson images for listed topics.\n");

courseList
  .filter((c) => c.needsReupload > 0)
  .forEach((c) => {
    print("▸ " + c.courseName + " — " + c.needsReupload + " lesson(s) to migrate");
    rows
      .filter((r) => r.courseId === c.courseId && classifyFolder(r.imagePath) !== CANONICAL)
      .slice(0, 15)
      .forEach((r) => {
        print(
          "    [" +
            classifyFolder(r.imagePath) +
            "] " +
            r.moduleName +
            " / " +
            r.label
        );
      });
    const extra =
      c.needsReupload -
      rows.filter(
        (r) => r.courseId === c.courseId && classifyFolder(r.imagePath) !== CANONICAL
      ).slice(0, 15).length;
    if (extra > 0) print("    … and " + extra + " more");
    print("");
  });
