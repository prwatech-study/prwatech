/**
 * Run against skillamaDB:
 *   mongosh "mongodb://USER:PASS@HOST:27017/skillamaDB?authSource=admin" \
 *     --file scripts/mongo-find-duplicate-curriculum-images.js
 *
 * Defaults: Advanced Python on AWS2 bucket (presentation-image-courses)
 * Override: COURSE_ID=... COURSE_NAME=... mongosh ... --file ...
 */

const courseId =
  process.env.COURSE_ID ||
  "69f98d0219c6fc7027d82f68";
const courseNameFilter = process.env.COURSE_NAME || "Advanced Python";
const aws2Bucket = "presentation-image-courses";
const aws1Bucket = "presentation-image-course"; // legacy singular

// 1) Resolve course
let course = null;
try {
  course = db.courses.findOne({ _id: ObjectId(courseId) }, { _id: 1, name: 1 });
} catch (e) {
  course = db.courses.findOne({ _id: courseId }, { _id: 1, name: 1 });
}
if (!course) {
  course = db.courses.findOne(
    { name: { $regex: courseNameFilter, $options: "i" } },
    { _id: 1, name: 1 }
  );
}
if (!course) {
  print("No course matched id=" + courseId + " or name=" + courseNameFilter);
  quit(1);
}
const resolvedCourseId = String(course._id);
print("Course: " + course.name + " (" + resolvedCourseId + ")");
print("Target AWS2 bucket: " + aws2Bucket + "\n");

// 2) Flatten all submodule imagePath values for this course
const rows = db.course_curricula.aggregate([
  { $match: { courseId: resolvedCourseId } },
  { $unwind: { path: "$submodules", includeArrayIndex: "submoduleIdx" } },
  {
    $project: {
      moduleId: "$_id",
      moduleName: 1,
      moduleOrder: "$order",
      submoduleIdx: 1,
      label: "$submodules.label",
      submoduleOrder: "$submodules.order",
      imagePath: "$submodules.imagePath",
      bucket: {
        $switch: {
          branches: [
            {
              case: {
                $regexMatch: {
                  input: { $ifNull: ["$submodules.imagePath", ""] },
                  regex: aws1Bucket.replace(".", "\\.") + "\\.s3",
                },
              },
              then: "AWS1-legacy",
            },
            {
              case: {
                $regexMatch: {
                  input: { $ifNull: ["$submodules.imagePath", ""] },
                  regex: aws2Bucket.replace(".", "\\.") + "\\.s3",
                },
              },
              then: "AWS2",
            },
          ],
          default: "other-or-relative",
        },
      },
      normalizedPath: {
        $let: {
          vars: { p: { $ifNull: ["$submodules.imagePath", ""] } },
          in: {
            $arrayElemAt: [{ $split: ["$$p", "?"] }, 0],
          },
        },
      },
    },
  },
  { $match: { imagePath: { $exists: true, $nin: [null, ""] } } },
]).toArray();

print("Submodules with images: " + rows.length);

print("\n=== Storage folder breakdown (imagePath in MongoDB) ===");
const folderCounts = {};
rows.forEach((r) => {
  const p = (r.imagePath || "").split("?")[0];
  let folder = "other-or-external";
  if (p.includes("/curriculum/images/")) folder = "curriculum/images (generic)";
  else if (p.includes("/courses/") && p.includes("/submodules/") && p.includes("/slides/"))
    folder = "courses/…/submodules/…/slides (structured)";
  else if (p.includes("/courses/") && p.includes("/lessons/") && p.includes("/slides/"))
    folder = "courses/…/lessons/…/slides (legacy order)";
  else if (p.includes("/courses/") && p.includes("/social/"))
    folder = "courses/…/social (thumbnail)";
  else if (p.includes("/files/") || p.startsWith("uploads/")) folder = "local server file";
  else if (p.includes("amazonaws.com") || p.includes(".s3.")) folder = "s3 other path";
  folderCounts[folder] = (folderCounts[folder] || 0) + 1;
});
Object.entries(folderCounts)
  .sort((a, b) => b[1] - a[1])
  .forEach(([k, v]) => print(k + ": " + v));

print("\n=== Bucket URL breakdown (migration check) ===");
const bucketCounts = {};
rows.forEach((r) => {
  bucketCounts[r.bucket] = (bucketCounts[r.bucket] || 0) + 1;
});
Object.entries(bucketCounts).forEach(([k, v]) => print(k + ": " + v));

// 3) Exact duplicate imagePath (same full URL stored on multiple submodules)
print("=== A) Exact duplicate imagePath (count > 1) ===");
const byExact = {};
rows.forEach((r) => {
  const k = r.imagePath;
  if (!byExact[k]) byExact[k] = [];
  byExact[k].push(r);
});
Object.entries(byExact)
  .filter(([, list]) => list.length > 1)
  .sort((a, b) => b[1].length - a[1].length)
  .forEach(([path, list]) => {
    print("\n[" + list.length + "x] " + path);
    list.forEach((r) =>
      print(
        "  - " +
          r.moduleName +
          " / " +
          r.label +
          " (moduleOrder=" +
          r.moduleOrder +
          ", submoduleOrder=" +
          r.submoduleOrder +
          ", idx=" +
          r.submoduleIdx +
          ")"
      )
    );
  });

// 4) Same S3 object key after stripping ?v= (order-based path collisions)
print("\n\n=== B) Duplicate normalized S3 path (count > 1) ===");
const byNorm = {};
rows.forEach((r) => {
  const k = r.normalizedPath;
  if (!k) return;
  if (!byNorm[k]) byNorm[k] = [];
  byNorm[k].push(r);
});
Object.entries(byNorm)
  .filter(([, list]) => list.length > 1)
  .sort((a, b) => b[1].length - a[1].length)
  .forEach(([path, list]) => {
    print("\n[" + list.length + "x] " + path);
    list.forEach((r) =>
      print("  - " + r.moduleName + " / " + r.label)
    );
  });

// 5) Duplicate module order within course (causes modules/03/... key reuse)
print("\n\n=== C) Duplicate module order values ===");
db.course_curricula.aggregate([
  { $match: { courseId: resolvedCourseId } },
  {
    $group: {
      _id: "$order",
      count: { $sum: 1 },
      modules: { $push: { id: "$_id", name: "$moduleName" } },
    },
  },
  { $match: { count: { $gt: 1 } } },
  { $sort: { _id: 1 } },
]).forEach((g) => {
  print("\norder=" + g._id + " used " + g.count + " times:");
  g.modules.forEach((m) => print("  - " + m.name + " (" + m.id + ")"));
});

// 6) Submodule order collisions inside each module
print("\n\n=== D) Submodule order collisions within a module ===");
db.course_curricula.aggregate([
  { $match: { courseId: resolvedCourseId } },
  { $unwind: "$submodules" },
  {
    $group: {
      _id: { moduleId: "$_id", moduleName: "$moduleName", subOrder: "$submodules.order" },
      count: { $sum: 1 },
      labels: { $push: "$submodules.label" },
    },
  },
  { $match: { count: { $gt: 1 } } },
]).forEach((g) => {
  print(
    "\n" +
      g._id.moduleName +
      " — submodule order " +
      g._id.subOrder +
      " x" +
      g.count +
      ": " +
      g.labels.join(", ")
  );
});
