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
