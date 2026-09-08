/*
 * Pre-flight check for skillama.mongodb.auto-index-creation=true
 *
 * Enabling auto index creation makes the app build every @Indexed index on startup.
 * Unique indexes are built against live data, so any pre-existing duplicate makes index
 * creation throw and the application fails to boot. Non-sparse unique indexes are the
 * bigger hazard: MongoDB treats every document missing the field as null, so two
 * documents without the field already collide.
 *
 * Run this against skillamaDB BEFORE deploying. Exit code 0 means safe to enable.
 *
 *   mongosh "mongodb://<user>:<pass>@<host>:27017/skillamaDB?authSource=admin" \
 *     --quiet --file scripts/check-mongo-unique-indexes.js
 */

const UNIQUE_INDEXES = [
  { collection: "course_knowledge_sync_state", field: "courseId", sparse: false },
  { collection: "exam_sessions", field: "examSessionId", sparse: false },
  { collection: "module_quiz_sessions", field: "quizSessionId", sparse: false },
  { collection: "organizations", field: "slug", sparse: false },
  { collection: "platform_features", field: "code", sparse: false },
  { collection: "practical_datasets", field: "datasetId", sparse: false },
  { collection: "subscription_plans", field: "code", sparse: false },
  { collection: "user_profiles", field: "sessionId", sparse: false },
  { collection: "users", field: "referralCode", sparse: true },
  { collection: "users", field: "googleSub", sparse: true },
  { collection: "users", field: "appleSub", sparse: true },
];

let blocking = 0;

function duplicateValues(collection, field) {
  return db
    .getCollection(collection)
    .aggregate(
      [
        { $group: { _id: `$${field}`, count: { $sum: 1 } } },
        { $match: { count: { $gt: 1 } } },
        { $sort: { count: -1 } },
        { $limit: 5 },
      ],
      { allowDiskUse: true }
    )
    .toArray();
}

print("");
print("Checking unique-index candidates in skillamaDB");
print("=".repeat(78));

for (const { collection, field, sparse } of UNIQUE_INDEXES) {
  const exists = db.getCollectionNames().includes(collection);
  if (!exists) {
    print(`SKIP   ${collection}.${field} — collection does not exist yet`);
    continue;
  }

  const total = db.getCollection(collection).countDocuments({});
  const dupes = duplicateValues(collection, field);

  // A sparse unique index ignores documents missing the field, so null groups are fine.
  const offenders = sparse ? dupes.filter((d) => d._id !== null) : dupes;

  if (offenders.length === 0) {
    print(`OK     ${collection}.${field} (${total} docs)`);
    continue;
  }

  blocking += 1;
  print("");
  print(`BLOCK  ${collection}.${field} — unique index would FAIL to build`);
  for (const d of offenders) {
    const label = d._id === null ? "<missing/null>" : JSON.stringify(d._id);
    print(`         ${d.count} documents share ${label}`);
  }
  if (!sparse && offenders.some((d) => d._id === null)) {
    print(
      "         NOTE: this index is not sparse, so documents missing the field all collide on null."
    );
  }
  print("");
}

print("=".repeat(78));
if (blocking === 0) {
  print("PASS — no duplicates found. Safe to set skillama.mongodb.auto-index-creation=true");
} else {
  print(`FAIL — ${blocking} field(s) would break startup. Resolve duplicates, or deploy with`);
  print("       skillama.mongodb.auto-index-creation=false and create indexes manually.");
}
print("");

quit(blocking === 0 ? 0 : 1);
