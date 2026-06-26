/**
 * Build a collision-safe migration plan: one unique canonical S3 key per submodule.
 *
 * Canonical key (matches FileStorageServiceImpl.uploadImageForSubmoduleById):
 *   courses/{courseId}/modules/{moduleId}/submodules/{idx}/slides/01.{ext}
 *   idx = 1-based, zero-padded submodule array index
 *
 * Usage:
 *   mongosh "mongodb://USER:PASS@HOST:27017/skillamaDB?authSource=admin" \
 *     --file scripts/migrate-curriculum-images-plan.js \
 *     > /tmp/curriculum-image-migration-plan.json
 *
 * Optional env (mongosh):
 *   COURSE_ID=6817bbe4cb6b8135daecc428   # limit to one course for pilot
 *
 * Then review plan + run execute script with --dry-run first.
 */

const BUCKET = "presentation-image-courses";
const BASE_URL = `https://${BUCKET}.s3.ap-south-1.amazonaws.com`;
const COURSE_FILTER = typeof process !== "undefined" ? process.env.COURSE_ID : null;

function stripQuery(url) {
  return (url || "").split("?")[0];
}

function isCanonical(imagePath) {
  return /\/courses\/[^/]+\/modules\/[^/]+\/submodules\/\d{2}\/slides\/\d{2}\.[^/]+$/i.test(
    stripQuery(imagePath)
  );
}

function extensionFromPath(path) {
  const clean = stripQuery(path);
  const dot = clean.lastIndexOf(".");
  if (dot === -1) return ".png";
  const ext = clean.substring(dot).toLowerCase();
  if ([".jpg", ".jpeg", ".png", ".gif", ".webp"].includes(ext)) return ext === ".jpeg" ? ".jpg" : ext;
  return ".png";
}

function extractS3Key(imagePath) {
  const clean = stripQuery(imagePath);
  if (!clean) return null;
  const marker = ".amazonaws.com/";
  const idx = clean.indexOf(marker);
  if (idx !== -1) return clean.substring(idx + marker.length);
  if (clean.startsWith(BUCKET + "/")) return clean.substring(BUCKET.length + 1);
  if (!clean.includes("://") && !clean.startsWith("/")) return clean;
  return null;
}

function buildCanonicalKey(courseId, moduleId, submoduleIdx, imagePath) {
  const idxNum = Number(submoduleIdx);
  const idxStr = String(idxNum + 1).padStart(2, "0");
  const ext = extensionFromPath(imagePath);
  return `courses/${courseId}/modules/${moduleId}/submodules/${idxStr}/slides/01${ext}`;
}

function buildCanonicalUrl(key, version) {
  return `${BASE_URL}/${key}?v=${version || Date.now()}`;
}

const matchStage = COURSE_FILTER ? { $match: { courseId: COURSE_FILTER } } : null;

const pipeline = [
  ...(matchStage ? [matchStage] : []),
  { $unwind: { path: "$submodules", includeArrayIndex: "submoduleIdx" } },
  {
    $match: {
      "submodules.imagePath": { $exists: true, $nin: [null, ""] },
    },
  },
  {
    $lookup: {
      from: "courses",
      let: { cid: "$courseId" },
      pipeline: [
        { $match: { $expr: { $eq: [{ $toString: "$_id" }, "$$cid"] } } },
        { $project: { name: 1 } },
      ],
      as: "course",
    },
  },
  {
    $project: {
      courseId: 1,
      courseName: { $arrayElemAt: ["$course.name", 0] },
      moduleId: { $toString: "$_id" },
      moduleName: 1,
      submoduleIdx: 1,
      label: "$submodules.label",
      imagePath: "$submodules.imagePath",
    },
  },
];

const rows = db.course_curricula.aggregate(pipeline).toArray();

const plan = [];
const newKeyOwners = {};
const legacyUrlGroups = {};
const legacyNormGroups = {};
let skippedCanonical = 0;
let skippedExternal = 0;
const errors = [];

rows.forEach((row) => {
  const { courseId, moduleId, imagePath } = row;
  const submoduleIdx = Number(row.submoduleIdx);
  const recordId = `${courseId}:${moduleId}:${submoduleIdx}`;

  if (isCanonical(imagePath)) {
    skippedCanonical += 1;
    plan.push({
      recordId,
      action: "skip-already-canonical",
      courseId,
      courseName: row.courseName,
      moduleId,
      moduleName: row.moduleName,
      submoduleIdx,
      label: row.label,
      imagePath,
    });
    return;
  }

  const sourceKey = extractS3Key(imagePath);
  if (!sourceKey) {
    skippedExternal += 1;
    plan.push({
      recordId,
      action: "skip-external",
      courseId,
      courseName: row.courseName,
      moduleId,
      moduleName: row.moduleName,
      submoduleIdx,
      label: row.label,
      imagePath,
      reason: "Not a presentation-image-courses S3 URL",
    });
    return;
  }

  const newKey = buildCanonicalKey(courseId, moduleId, submoduleIdx, imagePath);
  const version = Date.now();
  const newImagePath = buildCanonicalUrl(newKey, version);

  if (!newKeyOwners[newKey]) newKeyOwners[newKey] = [];
  newKeyOwners[newKey].push(recordId);

  const legacyNorm = stripQuery(imagePath).replace(BASE_URL + "/", "");
  if (!legacyNormGroups[legacyNorm]) legacyNormGroups[legacyNorm] = [];
  legacyNormGroups[legacyNorm].push(recordId);

  if (!legacyUrlGroups[imagePath]) legacyUrlGroups[imagePath] = [];
  legacyUrlGroups[imagePath].push(recordId);

  plan.push({
    recordId,
    action: "copy",
    courseId,
    courseName: row.courseName,
    moduleId,
    moduleName: row.moduleName,
    submoduleIdx,
    label: row.label,
    oldImagePath: imagePath,
    sourceKey,
    newKey,
    newImagePath,
    sharedLegacyUrl: legacyUrlGroups[imagePath].length > 1,
    fixesLegacyCollision:
      legacyNormGroups[legacyNorm].length > 1 ||
      /\/modules\/\d{2}\/lessons\/\d{2}\/slides\//.test(legacyNorm),
  });
});

// Abort planning if two submodules would target the same canonical key (must never happen).
Object.entries(newKeyOwners).forEach(([key, owners]) => {
  if (owners.length > 1) {
    errors.push({
      type: "DUPLICATE_CANONICAL_TARGET",
      newKey: key,
      recordIds: owners,
      message:
        "Two submodules map to the same canonical S3 key — fix moduleId/submoduleIdx data before migrating.",
    });
  }
});

const sharedLegacyUrls = Object.entries(legacyUrlGroups).filter(([, ids]) => ids.length > 1);
const sharedLegacyNorms = Object.entries(legacyNormGroups).filter(([, ids]) => ids.length > 1);

const summary = {
  generatedAt: new Date().toISOString(),
  bucket: BUCKET,
  courseFilter: COURSE_FILTER || null,
  totalSubmodulesWithImages: rows.length,
  toMigrate: plan.filter((p) => p.action === "copy").length,
  skipAlreadyCanonical: skippedCanonical,
  skipExternal: skippedExternal,
  sharedLegacyUrlGroups: sharedLegacyUrls.length,
  sharedLegacyNormGroups: sharedLegacyNorms.length,
  legacyCollisionsToSplit: plan.filter((p) => p.action === "copy" && p.fixesLegacyCollision).length,
  planningErrors: errors.length,
  safeToExecute: errors.length === 0,
};

const output = {
  summary,
  errors,
  sharedLegacyUrlSamples: sharedLegacyUrls.slice(0, 10).map(([url, ids]) => ({
    imagePath: url,
    submoduleCount: ids.length,
    note: "Each submodule will get its own canonical copy (collision fixed).",
  })),
  sharedLegacyNormSamples: sharedLegacyNorms.slice(0, 10).map(([path, ids]) => ({
    normalizedPath: path,
    submoduleCount: ids.length,
    note: "Old order-based or duplicate path — split into unique moduleId/submodule keys.",
  })),
  plan,
};

print(JSON.stringify(output, null, 2));

if (errors.length > 0) {
  print("\n// PLANNING FAILED — resolve DUPLICATE_CANONICAL_TARGET before execute.");
  quit(1);
}
