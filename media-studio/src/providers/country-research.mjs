import { isPaidAiAllowed } from "../domain/resource-policy.mjs";
import { validateResearchReport } from "../domain/research-safety.mjs";

const API = "https://api.openai.com/v1/responses";

export function getCountryResearchStatus(env = process.env) {
  const configured = Boolean(env.OPENAI_API_KEY && env.OPENAI_MODEL);
  return {
    id: "live_country_research",
    name: "Live country and current-affairs research",
    configured,
    status: !configured ? "not_configured" : isPaidAiAllowed(env) ? "configured_not_health_checked" : "configured_but_cost_locked",
    paidCallsEnabled: isPaidAiAllowed(env),
    note: configured
      ? "Uses live web search for each requested country. Provider usage may incur charges."
      : "Configure the OpenAI API key and model before current-affairs research can run."
  };
}

export async function researchCountry(job, env = process.env, now = new Date()) {
  if (!getCountryResearchStatus(env).configured) return { ok: false, error: "research_engine_not_configured" };
  if (!isPaidAiAllowed(env)) return { ok: false, error: "paid_ai_cost_guard_blocked" };
  const countryNames = new Intl.DisplayNames(["en"], { type: "region" });
  const country = countryNames.of(job.countryCode) || job.countryCode;
  const generatedAt = now.toISOString();
  const prompt = [
    "Research this country's current situation for a factual video report.",
    "Country: " + country + " (" + job.countryCode + ").",
    "Video brief: " + job.prompt,
    "Research timestamp: " + generatedAt + ".",
    "Use live web search, prioritizing official government/statistical sources and reputable independent news organizations.",
    "Find recent developments relevant to the brief, and include publication dates and direct source URLs.",
    "Separate verified facts from claims, opinion, and uncertainty. Include relevant positive government initiatives only when independently supported; include material context and criticism where needed.",
    "Do not invent events, numbers, quotes, dates, or government achievements. If reliable current evidence is insufficient, say so and recommend not making the claim.",
    "Return valid JSON with keys: country, researchedAt, summary, developments (array of objects with claim, publishedAt, sourceName, sourceUrl, confidence, context), safePositivePoints (array), avoidClaims (array), unansweredQuestions (array)."
  ].join("\n");

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 60000);
  try {
    const response = await fetch(API, {
      method: "POST",
      headers: { authorization: "Bearer " + env.OPENAI_API_KEY, "content-type": "application/json" },
      body: JSON.stringify({
        model: env.OPENAI_MODEL,
        tools: [{ type: "web_search_preview" }],
        tool_choice: "auto",
        input: prompt
      }),
      signal: controller.signal
    });
    if (!response.ok) return { ok: false, error: "research_provider_error", status: response.status };
    const payload = await response.json();
    const raw = payload.output_text || (payload.output || [])
      .flatMap(item => item.content || [])
      .filter(item => item.type === "output_text")
      .map(item => item.text || "").join("\n");
    if (!raw) return { ok: false, error: "empty_research_response" };
    let report;
    try { report = JSON.parse(raw); } catch { return { ok: false, error: "invalid_research_json" }; }
    if (!report || !Array.isArray(report.developments) || !Array.isArray(report.safePositivePoints) || !Array.isArray(report.avoidClaims)) {
      return { ok: false, error: "invalid_research_schema" };
    }
    const annotations = (payload.output || []).flatMap(item => item.content || [])
      .flatMap(item => item.annotations || [])
      .filter(item => item.type === "url_citation" && typeof item.url === "string");
    const sources = [...new Map(annotations.map(item => [item.url, { title: item.title || item.url, url: item.url }])).values()];
    const normalizedReport = { ...report, countryCode: job.countryCode, researchedAt: generatedAt, sources, freshness: "live_search_at_generation_time" };
    const validation = validateResearchReport(normalizedReport, job.countryCode, now);
    if (!validation.ok) return { ok: false, error: "research_evidence_validation_failed", validation };
    return { ok: true, report: normalizedReport, validation, model: env.OPENAI_MODEL };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "research_timeout" : "research_provider_unavailable" };
  } finally {
    clearTimeout(timeout);
  }
}
