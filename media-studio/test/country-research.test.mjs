import test from "node:test";
import assert from "node:assert/strict";
import { getCountryResearchStatus, researchCountry } from "../src/providers/country-research.mjs";

test("live country research stays disabled without server credentials", async () => {
  const env = {};
  assert.equal(getCountryResearchStatus(env).configured, false);
  const result = await researchCountry({ countryCode: "JP", prompt: "Current government initiatives" }, env);
  assert.deepEqual(result, { ok: false, error: "research_engine_not_configured" });
});

test("research engine reports its configured but unverified state", () => {
  const status = getCountryResearchStatus({ OPENAI_API_KEY: "test-key", OPENAI_MODEL: "test-model" });
  assert.equal(status.configured, true);
  assert.equal(status.status, "configured_not_health_checked");
});

import { validateResearchReport } from "../src/domain/research-safety.mjs";

const freshReport = {
  countryCode: "JP",
  researchedAt: "2026-11-05T10:00:00.000Z",
  sources: [
    { title: "Official source", url: "https://www.example.gov.jp/report" },
    { title: "Independent source", url: "https://news.example.org/story" }
  ],
  developments: [
    { claim: "A documented policy update", sourceUrl: "https://www.example.gov.jp/report", publishedAt: "2026-11-04T08:00:00.000Z" }
  ],
  safePositivePoints: ["A sourced initiative"],
  avoidClaims: ["An unsupported claim"]
};

test("evidence gate accepts a fresh country-matched report with two independent HTTPS domains", () => {
  const result = validateResearchReport(freshReport, "JP", new Date("2026-11-05T11:00:00.000Z"));
  assert.equal(result.ok, true);
  assert.equal(result.sourceDomainCount, 2);
});

test("evidence gate rejects a report for the wrong country", () => {
  const result = validateResearchReport(freshReport, "IN", new Date("2026-11-05T11:00:00.000Z"));
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("country_mismatch"));
});

test("evidence gate rejects stale research and old current-affairs items", () => {
  const stale = { ...freshReport, researchedAt: "2026-11-01T00:00:00.000Z",
    developments: [{ ...freshReport.developments[0], publishedAt: "2025-01-01T00:00:00.000Z" }] };
  const result = validateResearchReport(stale, "JP", new Date("2026-11-05T11:00:00.000Z"));
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("research_report_stale"));
  assert.ok(result.errors.includes("development_too_old_for_current_affairs"));
});

test("evidence gate rejects untrusted URLs and uncited claims", () => {
  const bad = { ...freshReport, sources: [
    { url: "http://insecure.example.gov" }, { url: "https://insecure.example.gov/other" }
  ] };
  const result = validateResearchReport(bad, "JP", new Date("2026-11-05T11:00:00.000Z"));
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("two_distinct_https_sources_required"));
  assert.ok(result.errors.includes("development_source_not_cited"));
});
