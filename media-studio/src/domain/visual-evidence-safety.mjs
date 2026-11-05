const ALLOWED_KINDS = new Set(["image", "video", "map", "graphic"]);
const ALLOWED_PROVENANCE = new Set(["verified_source", "licensed_stock", "generated_ai", "original"]);
const HTTPS = value => {
  try { return new URL(value).protocol === "https:"; } catch { return false; }
};

export function validateVisualAssets(assets, countryCode) {
  const errors = [];
  const normalizedCountry = String(countryCode || "").toUpperCase();
  if (!Array.isArray(assets) || assets.length === 0) return { ok: false, errors: ["visual_assets_required"], assets: [] };
  if (assets.length > 100) return { ok: false, errors: ["too_many_visual_assets"], assets: [] };

  const checked = assets.map((asset, index) => {
    const prefix = "asset_" + index + "_";
    const issues = [];
    if (!asset || typeof asset !== "object") return { index, ok: false, errors: [prefix + "invalid_record"] };
    if (!ALLOWED_KINDS.has(asset.kind)) issues.push(prefix + "invalid_kind");
    if (!ALLOWED_PROVENANCE.has(asset.provenanceType)) issues.push(prefix + "invalid_provenance");
    if (String(asset.depictedCountryCode || "").toUpperCase() !== normalizedCountry) issues.push(prefix + "country_mismatch");

    if (asset.provenanceType === "verified_source" || asset.provenanceType === "licensed_stock") {
      if (!HTTPS(asset.sourceUrl)) issues.push(prefix + "https_source_required");
      if (typeof asset.sourceName !== "string" || !asset.sourceName.trim()) issues.push(prefix + "source_name_required");
      if (typeof asset.license !== "string" || !asset.license.trim()) issues.push(prefix + "license_or_usage_rights_required");
      if (typeof asset.attribution !== "string" || !asset.attribution.trim()) issues.push(prefix + "attribution_required");
      if (asset.locationVerified !== true) issues.push(prefix + "location_not_verified");
      if (asset.isActualFootage === true && asset.provenanceType !== "verified_source") issues.push(prefix + "stock_not_verified_as_actual_footage");
    }
    if (asset.provenanceType === "generated_ai") {
      if (asset.isActualFootage === true) issues.push(prefix + "generated_visual_cannot_be_actual_footage");
      if (asset.disclosure !== "AI-generated illustration") issues.push(prefix + "ai_disclosure_required");
    }
    if (asset.kind === "map" && (typeof asset.mapProvider !== "string" || !asset.mapProvider.trim())) issues.push(prefix + "map_provider_required");
    if (typeof asset.caption !== "string" || !asset.caption.trim()) issues.push(prefix + "visible_caption_required");
    return { index, ok: issues.length === 0, errors: issues };
  });
  for (const item of checked) errors.push(...item.errors);
  return { ok: errors.length === 0, errors, assets: checked, checkedAt: new Date().toISOString() };
}
