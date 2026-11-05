import test from "node:test";
import assert from "node:assert/strict";
import { getAvatarEngineStatus, generateAvatarVideo, getAvatarVideoStatus, isTrustedAvatarVideoUrl } from "../src/providers/heygen-avatar.mjs";

const env = { HEYGEN_API_KEY: "test-secret", HEYGEN_AVATAR_ID: "fictional-avatar-id", HEYGEN_VOICE_ID: "natural-voice-id" };

test("HeyGen presenter engine stays off without credentials and makes no call", async () => {
  assert.equal(getAvatarEngineStatus({}).status, "not_configured");
  const result = await generateAvatarVideo({ scriptDraft: { scenes: [{ narration: "Hello" }] } }, {});
  assert.deepEqual(result, { ok: false, error: "avatar_engine_not_configured" });
});

test("HeyGen creates a fictional presenter video from approved script draft data", async () => {
  const originalFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (url, options) => {
    request = { url, options, body: JSON.parse(options.body) };
    return new Response(JSON.stringify({ data: { video_id: "video_12345678", status: "processing" } }), { status: 200 });
  };
  try {
    const result = await generateAvatarVideo({
      prompt: "An educational country explainer",
      scriptDraft: { title: "Japan Explained", scenes: [{ narration: "Welcome to Japan." }, { narration: "Its history is rich." }] }
    }, env);
    assert.equal(result.ok, true);
    assert.equal(result.videoId, "video_12345678");
    assert.equal(request.url, "https://api.heygen.com/v2/video/generate");
    assert.equal(request.options.headers["x-api-key"], env.HEYGEN_API_KEY);
    assert.equal(request.body.video_inputs[0].character.avatar_id, env.HEYGEN_AVATAR_ID);
    assert.equal(request.body.video_inputs[0].voice.voice_id, env.HEYGEN_VOICE_ID);
    assert.equal(request.body.video_inputs[0].voice.input_text, "Welcome to Japan.\n\nIts history is rich.");
  } finally { globalThis.fetch = originalFetch; }
});

test("HeyGen presenter status is polled using the provider video id", async () => {
  const originalFetch = globalThis.fetch;
  let requestedUrl = "";
  globalThis.fetch = async url => {
    requestedUrl = String(url);
    return new Response(JSON.stringify({ data: { status: "completed", video_url: "https://cdn.example/video.mp4" } }), { status: 200 });
  };
  try {
    const result = await getAvatarVideoStatus("video_12345678", env);
    assert.equal(result.ok, true);
    assert.equal(result.status, "completed");
    assert.equal(result.videoUrl, "https://cdn.example/video.mp4");
    assert.match(requestedUrl, /video_status\.get/);
  } finally { globalThis.fetch = originalFetch; }
});

test("HeyGen rejects invalid provider video ids", async () => {
  const result = await getAvatarVideoStatus("../unsafe", env);
  assert.deepEqual(result, { ok: false, error: "invalid_avatar_video_id" });
});

test("presenter video import accepts only trusted HTTPS HeyGen media URLs", () => {
  assert.equal(isTrustedAvatarVideoUrl("https://files.heygen.com/video.mp4"), true);
  assert.equal(isTrustedAvatarVideoUrl("https://heygen.com/video.mp4"), true);
  assert.equal(isTrustedAvatarVideoUrl("http://files.heygen.com/video.mp4"), false);
  assert.equal(isTrustedAvatarVideoUrl("https://evil.example/video.mp4"), false);
  assert.equal(isTrustedAvatarVideoUrl("https://files.heygen.com.evil.example/video.mp4"), false);
  assert.equal(isTrustedAvatarVideoUrl("https://user:pass@files.heygen.com/video.mp4"), false);
  assert.equal(isTrustedAvatarVideoUrl("https://files.heygen.com:8443/video.mp4"), false);
});
