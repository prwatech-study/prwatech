/**
 * READ-ONLY diagnostic — no writes. Checks the exact submodules named in QA bug reports
 * (skipped Llama 3 / Diffusion, skipped self-attention / applications, missing SQL module
 * 6-7 images, missing Correlated Subqueries script/captions) for enabled/imagePath/scriptText
 * state, to confirm whether "content skipping" is a data issue (disabled/empty fields) rather
 * than a code defect.
 *
 * Usage:
 *   source scripts/_lib/read-skillama-mongo-uri.sh
 *   mongosh "$SKILLAMA_MONGO_URI" --quiet --file scripts/mongo-check-reported-content-gaps.js
 */

const db_ = db.getSiblingDB("skillamaDB");

function reportForCourseNameLike(pattern) {
  const courses = db_.courses.find({ name: { $regex: pattern, $options: "i" } }, { _id: 1, name: 1 }).toArray();
  if (!courses.length) {
    print(`  (no course matched /${pattern}/i)`);
    return;
  }
  courses.forEach((c) => {
    print(`Course: ${c.name}  (_id=${c._id})`);
    const curricula = db_.course_curricula.find({ courseId: String(c._id) }).toArray();
    if (!curricula.length) {
      print("  (no course_curricula docs found for this courseId)");
    }
    curricula.forEach((mod) => {
      const subs = mod.submodules || [];
      print(`  Module: ${mod.moduleName}  (${subs.length} submodules)`);
      subs.forEach((s, i) => {
        const enabled = s.enabled === undefined ? "(undefined→true)" : String(s.enabled);
        const hasImage = !!(s.imagePath && String(s.imagePath).trim());
        const hasScript = !!(s.scriptText && String(s.scriptText).trim());
        print(
          `    [${i}] "${s.label}" enabled=${enabled} imagePath=${hasImage ? "SET" : "EMPTY"} scriptText=${hasScript ? `SET(${String(s.scriptText).length} chars)` : "EMPTY"} isPracticalRequired=${s.isPracticalRequired}`
        );
      });
    });
  });
}

print("=== Generative AI course ===");
reportForCourseNameLike("generative");

print("\n=== SQL course ===");
reportForCourseNameLike("sql");
