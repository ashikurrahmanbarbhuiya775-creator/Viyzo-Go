import test from "node:test";
import assert from "node:assert/strict";
import { getTextEngineStatus } from "../src/providers/openai-compatible.mjs";

test("text engine stays off without credentials", () => {
  const status = getTextEngineStatus({});
  assert.equal(status.configured, false);
  assert.equal(status.status, "not_configured");
});

test("text engine reports configured only when key and model exist", () => {
  const status = getTextEngineStatus({ OPENAI_API_KEY: "secret", OPENAI_MODEL: "example-model" });
  assert.equal(status.configured, true);
  assert.equal(status.model, "example-model");
});


test("configured text engine remains cost-locked until explicit owner opt-in", () => {
  const status = getTextEngineStatus({ OPENAI_API_KEY: "secret", OPENAI_MODEL: "example-model" });
  assert.equal(status.configured, true);
  assert.equal(status.paidCallsEnabled, false);
  assert.equal(status.status, "configured_but_cost_locked");
});
