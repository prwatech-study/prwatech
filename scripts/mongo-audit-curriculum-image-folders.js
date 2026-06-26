/**
 * Audit which S3/local folder each curriculum submodule image uses.
 *
 *   mongosh "mongodb://USER:PASS@HOST:27017/skillamaDB?authSource=admin" \
 *     --file scripts/mongo-audit-curriculum-image-folders.js
 *
 * Optional: COURSE_ID=... or COURSE_NAME=Python
 */

const courseId = process.env.COURSE_ID || null;
const courseNameFilter = process.env.COURSE_NAME || null;

function classifyFolder(imagePath) {
  const p = (imagePath || "").split("?")[0];
  if (!p) return "empty";
  if (p.includes("/curriculum/images/")) return "curriculum/images";
  if (p.includes("/courses/") && p.includes("/submodules/") && p.includes("/slides/"))
    return "courses/submodules/slides";
  if (p.includes("/courses/") && p.includes("/lessons/") && p.includes("/slides/"))
    return "courses/lessons/slides";
  if (p.includes("/courses/") && p.includes("/social/")) return "courses/social";
  if (p.includes("/files/") || p.startsWith("uploads/")) return "local";
  if (p.includes("amazonaws.com") || p.includes(".s3.")) return "s3-other";
  return "external";
}

const match = {};
if (courseId) {
  match.courseId = courseId;
} else if (courseNameFilter) {
  const course = db.courses.findOne(
    { name: { $regex: courseNameFilter, $options: "i" } },
    { _id: 1, name: 1 }
  );
  if (!course) {
    print("No course matched name=" + courseNameFilter);
    quit(1);
  }
  match.courseId = String(course._id);
  print("Course: " + course.name + " (" + match.courseId + ")\n");
}

const rows = db.course_curricula
  .aggregate([
    ...(Object.keys(match).length ? [{ $match: match }] : []),
    { $unwind: { path: "$submodules", includeArrayIndex: "submoduleIdx" } },
    {
      $project: {
        courseId: 1,
        moduleName: 1,
        label: "$submodules.label",
        submoduleIdx: 1,
        imagePath: "$submodules.imagePath",
      },
    },
    { $match: { imagePath: { $exists: true, $nin: [null, ""] } } },
  ])
  .toArray();

const summary = {};
rows.forEach((r) => {
  const folder = classifyFolder(r.imagePath);
  summary[folder] = (summary[folder] || 0) + 1;
});

print("=== Summary (" + rows.length + " submodules with images) ===");
Object.entries(summary)
  .sort((a, b) => b[1] - a[1])
  .forEach(([folder, count]) => print(folder + ": " + count));

print("\n=== Detail ===");
rows.forEach((r) => {
  print(
    "\n[" +
      classifyFolder(r.imagePath) +
      "] " +
      r.moduleName +
      " / " +
      r.label +
      " (idx " +
      r.submoduleIdx +
      ")"
  );
  print("  " + r.imagePath);
});
