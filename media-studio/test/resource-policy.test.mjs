import test from "node:test";
import assert from "node:assert/strict";
import { isPaidAiAllowed, getResourcePolicy, buildGrowthRecommendations } from "../src/domain/resource-policy.mjs";

test("remote paid AI is blocked by default", () => {
  assert.equal(isPaidAiAllowed({ OPENAI_API_KEY: "key", OPENAI_MODEL: "model" }), false);
  assert.equal(getResourcePolicy({}).defaultMode, "cost_guarded");
});

test("remote paid AI requires an explicit owner environment opt-in", () => {
  assert.equal(isPaidAiAllowed({ VIYZO_ALLOW_PAID_AI: "true" }), true);
  assert.equal(getResourcePolicy({ VIYZO_ALLOW_PAID_AI: "true" }).paidAiEnabled, true);
});

test("growth recommendations do not invent metrics when Meta insights are missing", () => {
  const result = buildGrowthRecommendations({ countryCode: "JP" });
  assert.equal(result.measured.reach, null);
  assert.equal(result.source, "owner_supplied_or_official_meta_insights_only");
  assert.match(result.recommendations[0].action, /Connect official Meta Page Insights/);
});

test("growth recommendations flag weak retention using measured inputs", () => {
  const result = buildGrowthRecommendations({ countryCode: "JP", reach: 1000, views: 500, averageWatchSeconds: 8, videoSeconds: 60, shares: 2, follows: 1 });
  assert.equal(result.measured.retentionPercent, 13);
  assert.ok(result.recommendations.some(item => item.action.includes("first 3–5 second hook")));
});
