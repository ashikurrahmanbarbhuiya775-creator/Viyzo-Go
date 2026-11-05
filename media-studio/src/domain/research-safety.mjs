const MAX_AGE_DAYS = 180;

function validHttpsUrl(value) {
  try { return new URL(value).protocol === "https:"; } catch { return false; }
}

export function validateResearchReport(report, countryCode, now = new Date()) {
  const errors = [];
  if (!report || typeof report !== "object") return { ok: false, errors: ["report_missing"] };
  if (String(report.countryCode || "").toUpperCase() !== String(countryCode || "").toUpperCase()) errors.push("country_mismatch");

  const researchedAt = Date.parse(report.researchedAt || "");
  if (!Number.isFinite(researchedAt)) errors.push("research_timestamp_missing");
  else if (researchedAt > now.getTime() + 5 * 60_000) errors.push("research_timestamp_in_future");
  else if (now.getTime() - researchedAt > 24 * 60 * 60_000) errors.push("research_report_stale");

  const sources = Array.isArray(report.sources) ? report.sources : [];
  const validSources = sources.filter(source => source && validHttpsUrl(source.url));
  const domains = new Set(validSources.map(source => new URL(source.url).hostname.toLowerCase().replace(/^www\\./, "")));
  if (validSources.length < 2 || domains.size < 2) errors.push("two_distinct_https_sources_required");

  const developments = Array.isArray(report.developments) ? report.developments : [];
  if (developments.length === 0) errors.push("no_researched_developments");
  const sourceUrls = new Set(validSources.map(source => source.url));
  for (const item of developments) {
    if (!item || typeof item.claim !== "string" || !item.claim.trim()) {
      errors.push("development_claim_missing");
      continue;
    }
    if (!item.sourceUrl || !sourceUrls.has(item.sourceUrl)) errors.push("development_source_not_cited");
    const publishedAt = Date.parse(item.publishedAt || "");
    if (!Number.isFinite(publishedAt)) errors.push("development_date_missing");
    else if (publishedAt > now.getTime() + 24 * 60 * 60_000) errors.push("development_date_in_future");
    else if (now.getTime() - publishedAt > MAX_AGE_DAYS * 24 * 60 * 60_000) errors.push("development_too_old_for_current_affairs");
  }
  return { ok: errors.length === 0, errors, sourceCount: validSources.length, sourceDomainCount: domains.size, developmentCount: developments.length };
}
