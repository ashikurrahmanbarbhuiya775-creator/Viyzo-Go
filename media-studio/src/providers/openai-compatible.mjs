const DEFAULT_BASE_URL = "https://api.openai.com/v1";

export function getTextEngineStatus(env = process.env) {
  const configured = Boolean(env.OPENAI_API_KEY && env.OPENAI_MODEL);
  return {
    id: "text_generation",
    name: "OpenAI-compatible text generation",
    configured,
    status: configured ? "configured_not_health_checked" : "not_configured",
    model: configured ? env.OPENAI_MODEL : null,
    note: configured ? "Server-side credentials detected; provider call occurs only when owner requests script generation." : "Set OPENAI_API_KEY and OPENAI_MODEL on the server to enable."
  };
}

export async function generateScriptDraft(job, env = process.env) {
  const status = getTextEngineStatus(env);
  if (!status.configured) return { ok: false, error: "text_engine_not_configured" };

  const base = (env.OPENAI_BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, "");
  const endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45000);
  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "authorization": "Bearer " + env.OPENAI_API_KEY,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model: env.OPENAI_MODEL,
        temperature: 0.4,
        messages: [
          {
            role: "system",
            content: "You are a careful video script editor. Return a concise structured draft with title, hook, scene-by-scene narration, and a fact_check list. Never invent current events, statistics, quotes, or sources. Clearly label claims needing verification. Do not impersonate real people or imply government endorsement. Output valid JSON only with keys title, hook, scenes (array of objects with visual and narration), fact_check (array), and closing."
          },
          {
            role: "user",
            content: JSON.stringify({
              brief: job.prompt,
              country_code: job.countryCode,
              language: job.language,
              target_duration_seconds: job.durationSeconds,
              presenter: job.presenter,
              note: "This is a draft. Current-affairs facts must be independently researched and verified before production."
            })
          }
        ],
        response_format: { type: "json_object" }
      }),
      signal: controller.signal
    });
    if (!response.ok) {
      const body = (await response.text()).slice(0, 1000);
      return { ok: false, error: "provider_error", status: response.status, detail: body };
    }
    const payload = await response.json();
    const content = payload?.choices?.[0]?.message?.content;
    if (typeof content !== "string" || !content.trim()) return { ok: false, error: "empty_provider_response" };
    let script;
    try { script = JSON.parse(content); }
    catch { return { ok: false, error: "invalid_provider_json" }; }
    if (!script || typeof script.title !== "string" || !Array.isArray(script.scenes) || !Array.isArray(script.fact_check)) {
      return { ok: false, error: "invalid_script_schema" };
    }
    return { ok: true, script, model: env.OPENAI_MODEL };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "provider_timeout" : "provider_unavailable" };
  } finally {
    clearTimeout(timeout);
  }
}

export async function generateMetadataDraft(job, env = process.env) {
  const status = getTextEngineStatus(env);
  if (!status.configured) return { ok: false, error: "text_engine_not_configured" };
  const base = (env.OPENAI_BASE_URL || DEFAULT_BASE_URL).replace(/\\/+$/, "");
  const endpoint = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45000);
  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "authorization": "Bearer " + env.OPENAI_API_KEY, "content-type": "application/json" },
      body: JSON.stringify({
        model: env.OPENAI_MODEL,
        temperature: 0.35,
        messages: [
          { role: "system", content: "Create accurate, non-clickbait video publishing metadata. Do not invent factual claims. Return valid JSON only with keys title, description, tags (array of strings), thumbnailText, thumbnailImagePrompt. The image prompt must describe an original, non-infringing 16:9 thumbnail and must not depict real public figures or logos." },
          { role: "user", content: JSON.stringify({ brief: job.prompt, country_code: job.countryCode, language: job.language, duration_seconds: job.durationSeconds, script: job.scriptDraft || null }) }
        ],
        response_format: { type: "json_object" }
      }),
      signal: controller.signal
    });
    if (!response.ok) return { ok: false, error: "provider_error", status: response.status };
    const payload = await response.json();
    const raw = payload?.choices?.[0]?.message?.content;
    if (typeof raw !== "string" || !raw.trim()) return { ok: false, error: "empty_provider_response" };
    let metadata;
    try { metadata = JSON.parse(raw); } catch { return { ok: false, error: "invalid_provider_json" }; }
    if (typeof metadata.title !== "string" || typeof metadata.description !== "string" ||
        !Array.isArray(metadata.tags) || typeof metadata.thumbnailImagePrompt !== "string") {
      return { ok: false, error: "invalid_metadata_schema" };
    }
    return { ok: true, metadata, model: env.OPENAI_MODEL };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "provider_timeout" : "provider_unavailable" };
  } finally {
    clearTimeout(timeout);
  }
}
