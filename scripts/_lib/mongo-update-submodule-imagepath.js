/**
 * Update one submodule imagePath by module _id + array index.
 * Env: MIG_MODULE_ID, MIG_IDX (0-based), MIG_NEW_PATH
 */
const moduleId = process.env.MIG_MODULE_ID;
const idx = Number(process.env.MIG_IDX);
const newPath = process.env.MIG_NEW_PATH;

if (!moduleId || Number.isNaN(idx) || !newPath) {
  print("MONGO_ERROR missing MIG_MODULE_ID, MIG_IDX, or MIG_NEW_PATH");
  quit(2);
}

const coll = db.getSiblingDB("skillamaDB").course_curricula;

function resolveFilter(id) {
  const tries = [id];
  if (/^[a-fA-F0-9]{24}$/.test(id)) {
    try {
      tries.push(ObjectId(id));
    } catch (e) {
      /* ignore */
    }
  }
  for (const _id of tries) {
    if (coll.findOne({ _id }, { _id: 1 })) {
      return { _id };
    }
  }
  return { _id: id };
}

const filter = resolveFilter(moduleId);
const field = "submodules." + idx + ".imagePath";
const setPayload = {};
setPayload[field] = newPath;

const res = coll.updateOne(filter, { $set: setPayload });

if (res.matchedCount !== 1) {
  print(
    "MONGO_UPDATE_WARN matched=" +
      res.matchedCount +
      " modified=" +
      res.modifiedCount +
      " filter=" +
      tojson(filter)
  );
  quit(2);
}

if (res.modifiedCount === 0) {
  const doc = coll.findOne(filter, { submodules: 1 });
  const current =
    doc && doc.submodules && doc.submodules[idx] ? doc.submodules[idx].imagePath : null;
  if (current === newPath) {
    print("MONGO_OK_ALREADY");
    quit(0);
  }
  print("MONGO_UPDATE_WARN matched=1 modified=0 current=" + current);
  quit(2);
}

print("MONGO_OK");
