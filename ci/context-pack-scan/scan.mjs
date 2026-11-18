#!/usr/bin/env node
/**
 * P4 context-pack guardrail — hard-fail PR diffs that violate a Hub Decision pack.
 *
 * Env (CLI):
 *   HUB_BASE_URL, HUB_CI_TOKEN, DECISION_ID, SERVICE_ID, GIT_DIFF_FILE,
 *   BRANCH_NAME, PR_BODY, REQUIRE_MUST_TOUCH
 */

import { readFileSync } from "fs";

export function extractDecisionId({ explicit, branch, prBody, diff }) {
  if (explicit?.trim()) return explicit.trim();
  const fromBranch = branch?.match(/ctx\/(dec_[a-z0-9]+)/i);
  if (fromBranch) return fromBranch[1];
  const re = /\b(dec_[a-z0-9]+)\b/i;
  const fromBody = prBody?.match(re);
  if (fromBody) return fromBody[1];
  const fromDiff = diff?.match(re);
  if (fromDiff) return fromDiff[1];
  return null;
}

export function addedLines(diff) {
  return String(diff || "")
    .split("\n")
    .filter((l) => l.startsWith("+") && !l.startsWith("+++"))
    .map((l) => l.slice(1));
}

export function normalize(s) {
  return String(s || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function phraseHits(phrase, haystack) {
  const p = normalize(phrase);
  if (p.length < 8) return false;
  const tokens = p.split(/\s+/).filter((t) => t.length >= 4).slice(0, 8);
  if (!tokens.length) return false;
  const hit = tokens.filter((t) => haystack.includes(t)).length;
  return hit >= Math.min(3, tokens.length);
}

export async function runContextPackScan(opts) {
  const {
    baseUrl,
    token,
    serviceId,
    decisionId: explicitId,
    branch = "",
    prBody = "",
    diff = "",
    requireMustTouch = false,
    fetchImpl = fetch,
  } = opts;

  if (!baseUrl) throw new Error("HUB_BASE_URL is required");
  if (!token) throw new Error("HUB_CI_TOKEN is required");
  if (!serviceId || !["lms", "backend", "ai_tutor"].includes(serviceId)) {
    throw new Error("SERVICE_ID must be lms|backend|ai_tutor");
  }

  const decisionId = extractDecisionId({
    explicit: explicitId,
    branch,
    prBody,
    diff,
  });
  if (!decisionId) {
    throw new Error(
      "Missing Decision ID — set DECISION_ID, use branch ctx/dec_…/service, or cite dec_… in PR body/diff",
    );
  }

  const cited =
    prBody.includes(decisionId) ||
    branch.includes(decisionId) ||
    diff.includes(decisionId);
  if (!cited) {
    throw new Error(`PR/branch/diff must cite Decision ID ${decisionId}`);
  }

  const url = `${String(baseUrl).replace(/\/$/, "")}/api/context-packs/${decisionId}`;
  const res = await fetchImpl(url, {
    headers: {
      authorization: `Bearer ${token}`,
      accept: "application/json",
    },
  });
  const pack = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`Hub pack fetch ${res.status}: ${pack.error || res.statusText}`);
  }

  if (!Array.isArray(pack.services) || !pack.services.includes(serviceId)) {
    throw new Error(
      `Service ${serviceId} is not on Decision ${decisionId} pack (services=${(pack.services || []).join(",")})`,
    );
  }

  const added = addedLines(diff);
  const haystack = normalize(added.join("\n"));

  const mustNot = Array.isArray(pack.mustNot) ? pack.mustNot : [];
  for (const rule of mustNot) {
    const phrase = rule.replace(/^Watch\s*\/\s*avoid:\s*/i, "");
    if (phraseHits(phrase, haystack)) {
      throw new Error(`mustNot violated for ${decisionId}: ${rule}`);
    }
  }

  if (requireMustTouch) {
    const must = Array.isArray(pack.must) ? pack.must : [];
    const any = must.some((m) => phraseHits(m.replace(/^Implement:\s*/i, ""), haystack));
    if (must.length && !any) {
      throw new Error(`No must-item keywords found in added lines for ${decisionId}`);
    }
  }

  return {
    ok: true,
    decisionId,
    serviceId,
    addedLineCount: added.length,
  };
}

async function cliMain() {
  const diffFile = process.env.GIT_DIFF_FILE?.trim();
  let diff = "";
  if (diffFile) {
    diff = readFileSync(diffFile, "utf8");
  } else if (process.env.SCAN_READ_STDIN === "1") {
    diff = readFileSync(0, "utf8");
  }

  try {
    const result = await runContextPackScan({
      baseUrl: process.env.HUB_BASE_URL?.trim(),
      token: process.env.HUB_CI_TOKEN?.trim(),
      serviceId: process.env.SERVICE_ID?.trim(),
      decisionId: process.env.DECISION_ID,
      branch: process.env.BRANCH_NAME || "",
      prBody: process.env.PR_BODY || "",
      diff,
      requireMustTouch: process.env.REQUIRE_MUST_TOUCH === "true",
    });
    console.log(
      `context-pack-scan OK: Decision ${result.decisionId} · service ${result.serviceId} · ${result.addedLineCount} added line(s) checked`,
    );
  } catch (err) {
    console.error(`context-pack-scan FAIL: ${err instanceof Error ? err.message : String(err)}`);
    process.exit(1);
  }
}

const isDirect =
  process.argv[1] &&
  (process.argv[1].endsWith("scan.mjs") || process.argv[1].endsWith("scan.js"));

if (isDirect) {
  cliMain();
}
