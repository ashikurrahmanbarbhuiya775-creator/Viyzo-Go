import test from "node:test";
import assert from "node:assert/strict";
import { validateVisualAssets } from "../src/domain/visual-evidence-safety.mjs";

const sourced = {
  kind: "image",
  provenanceType: "verified_source",
  depictedCountryCode: "JP",
  sourceUrl: "https://example.jp/news/photo",
  sourceName: "Example News",
  license: "Editorial use permitted",
  attribution: "Example News",
  locationVerified: true,
  isActualFootage: true,
  caption: "Tokyo, Japan — verified location"
};

test("visual evidence accepts country-matched, attributed HTTPS source assets", () => {
  const result = validateVisualAssets([sourced], "JP");
  assert.equal(result.ok, true);
});

test("visual evidence rejects a visual from the wrong country", () => {
  const result = validateVisualAssets([{ ...sourced, depictedCountryCode: "IN" }], "JP");
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("asset_0_country_mismatch"));
});

test("visual evidence requires usage rights, attribution and location verification", () => {
  const result = validateVisualAssets([{ ...sourced, license: "", attribution: "", locationVerified: false, sourceUrl: "http://example.jp/photo" }], "JP");
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("asset_0_https_source_required"));
  assert.ok(result.errors.includes("asset_0_license_or_usage_rights_required"));
  assert.ok(result.errors.includes("asset_0_attribution_required"));
  assert.ok(result.errors.includes("asset_0_location_not_verified"));
});

test("AI-generated scenes must be disclosed and cannot be represented as actual footage", () => {
  const generated = { kind: "video", provenanceType: "generated_ai", depictedCountryCode: "JP",
    disclosure: "AI-generated illustration", isActualFootage: false, caption: "Illustrative AI scene" };
  assert.equal(validateVisualAssets([generated], "JP").ok, true);
  const misleading = { ...generated, isActualFootage: true, disclosure: "" };
  const result = validateVisualAssets([misleading], "JP");
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("asset_0_generated_visual_cannot_be_actual_footage"));
  assert.ok(result.errors.includes("asset_0_ai_disclosure_required"));
});

test("map assets require a provider and visible caption", () => {
  const map = { kind: "map", provenanceType: "original", depictedCountryCode: "JP", caption: "Map of Japan" };
  const result = validateVisualAssets([map], "JP");
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes("asset_0_map_provider_required"));
});
