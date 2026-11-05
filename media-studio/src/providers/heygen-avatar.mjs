const API_BASE = "https://api.heygen.com";

export function getAvatarEngineStatus(env = process.env) {
  const configured = Boolean(env.HEYGEN_API_KEY && env.HEYGEN_AVATAR_ID);
  return {
    id: "ai_presenter",
    name: "HeyGen human-like AI presenter",
    configured,
    status: configured ? "configured_not_health_checked" : "not_configured",
    lipSync: true,
    naturalVoice: true,
    note: configured
      ? "Avatar and voice generation can be requested by the owner; provider usage may be billed."
      : "Set HEYGEN_API_KEY and HEYGEN_AVATAR_ID on the server. No provider calls are made while off."
  };
}

function narrationFromScript(script) {
  if (!script || !Array.isArray(script.scenes) || script.scenes.length === 0) return "";
  return script.scenes.map(scene => String(scene?.narration || "").trim()).filter(Boolean).join("\n\n").slice(0, 12000);
}

async function heygenRequest(path, env, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs || 45000);
  try {
    return await fetch(API_BASE + path, {
      method: options.method || "GET",
      headers: { "x-api-key": env.HEYGEN_API_KEY, "content-type": "application/json", accept: "application/json" },
      ...(options.body ? { body: JSON.stringify(options.body) } : {}),
      signal: controller.signal
    });
  } finally {
    clearTimeout(timeout);
  }
}

export async function generateAvatarVideo(job, env = process.env) {
  const status = getAvatarEngineStatus(env);
  if (!status.configured) return { ok: false, error: "avatar_engine_not_configured" };
  const narration = narrationFromScript(job?.scriptDraft);
  if (!narration) return { ok: false, error: "approved_script_required" };
  const input = {
    video_inputs: [{
      character: { type: "avatar", avatar_id: env.HEYGEN_AVATAR_ID, avatar_style: "normal" },
      voice: {
        type: "text",
        input_text: narration,
        ...(env.HEYGEN_VOICE_ID ? { voice_id: env.HEYGEN_VOICE_ID } : {})
      }
    }],
    dimension: { width: 1920, height: 1080 },
    title: String(job.scriptDraft.title || job.prompt || "Viyzo AI Presenter").slice(0, 100)
  };
  try {
    const response = await heygenRequest("/v2/video/generate", env, { method: "POST", body: input, timeoutMs: 60000 });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) return { ok: false, error: "avatar_provider_error", status: response.status };
    const videoId = payload?.data?.video_id || payload?.video_id;
    if (typeof videoId !== "string" || !videoId) return { ok: false, error: "avatar_invalid_provider_response" };
    return { ok: true, videoId, status: payload?.data?.status || "processing", provider: "heygen" };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "avatar_provider_timeout" : "avatar_provider_unavailable" };
  }
}

export async function getAvatarVideoStatus(videoId, env = process.env) {
  if (!getAvatarEngineStatus(env).configured) return { ok: false, error: "avatar_engine_not_configured" };
  if (typeof videoId !== "string" || !/^[a-zA-Z0-9_-]{8,128}$/.test(videoId)) return { ok: false, error: "invalid_avatar_video_id" };
  try {
    const response = await heygenRequest("/v1/video_status.get?video_id=" + encodeURIComponent(videoId), env, { timeoutMs: 20000 });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) return { ok: false, error: "avatar_status_error", status: response.status };
    const data = payload?.data || payload;
    return {
      ok: true,
      videoId,
      status: String(data?.status || "unknown"),
      videoUrl: typeof data?.video_url === "string" ? data.video_url : null,
      thumbnailUrl: typeof data?.thumbnail_url === "string" ? data.thumbnail_url : null
    };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "avatar_provider_timeout" : "avatar_provider_unavailable" };
  }
}

export function isTrustedAvatarVideoUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" &&
      !url.username && !url.password &&
      (!url.port || url.port === "443") &&
      (url.hostname === "heygen.com" || url.hostname.endsWith(".heygen.com"));
  } catch {
    return false;
  }
}
