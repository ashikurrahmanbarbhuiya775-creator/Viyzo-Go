import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { hashOwnerPassword } from "../src/security/owner-auth.mjs";

test("local API protects owner routes and creates validated jobs", async () => {
  const probe = createServer();
  await new Promise(resolve => probe.listen(0, "127.0.0.1", resolve));
  const port = probe.address().port;
  await new Promise(resolve => probe.close(resolve));
  const dir = await mkdtemp(join(tmpdir(), "viyzo-media-test-"));
  const token = "test-owner-token-0123456789-abcdefghijkl";
  const passwordHash = hashOwnerPassword("test-owner-password-long-enough");
  const child = spawn(process.execPath, ["server.mjs"], {
    cwd: new URL("..", import.meta.url).pathname,
    env: { ...process.env, HOST: "127.0.0.1", PORT: String(port), OWNER_ACCESS_TOKEN: token, OWNER_PASSWORD_HASH: passwordHash, DATA_FILE: join(dir, "jobs.json") },
    stdio: "ignore"
  });
  const base = "http://127.0.0.1:" + port;
  try {
    let ready = false;
    for (let i = 0; i < 50; i++) {
      try { if ((await fetch(base + "/health")).ok) { ready = true; break; } } catch {}
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.equal(ready, true, "API should start");
    const page = await fetch(base + "/");
    assert.equal(page.status, 200);
    assert.match(await page.text(), /Viyzo AI Media Studio/);

    const badLogin = await fetch(base + "/auth/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ password: "wrong-password" }) });
    assert.equal(badLogin.status, 401);
    const login = await fetch(base + "/auth/login", { method: "POST", headers: { "content-type": "application/json", origin: base }, body: JSON.stringify({ password: "test-owner-password-long-enough" }) });
    assert.equal(login.status, 200);
    const cookie = login.headers.get("set-cookie").split(";")[0];
    assert.match(login.headers.get("set-cookie"), /HttpOnly/);
    assert.match(login.headers.get("set-cookie"), /SameSite=Strict/);
    const session = await fetch(base + "/api/session", { headers: { cookie } });
    assert.equal(session.status, 200);
    const csrf = await fetch(base + "/api/jobs", { method: "POST", headers: { cookie, origin: "https://evil.example", "content-type": "application/json" }, body: "{}" });
    assert.equal(csrf.status, 403);
    const unauthorized = await fetch(base + "/api/jobs");
    assert.equal(unauthorized.status, 401);
    const headers = { authorization: "Bearer " + token, "content-type": "application/json" };
    const invalid = await fetch(base + "/api/jobs", { method: "POST", headers, body: JSON.stringify({ prompt: "short", countryCode: "JPN", language: "en", durationSeconds: 5 }) });
    assert.equal(invalid.status, 422);
    const created = await fetch(base + "/api/jobs", { method: "POST", headers, body: JSON.stringify({ prompt: "Create an educational video about Japan", countryCode: "JP", language: "en", durationSeconds: 300, presenter: "fictional" }) });
    assert.equal(created.status, 201);
    const { job } = await created.json();
    assert.equal(job.status, "draft");
    const metaStatus = await fetch(base + "/api/meta/status", { headers });
    assert.equal(metaStatus.status, 200);
    const metaStatusData = await metaStatus.json();
    assert.equal(metaStatusData.configured, false, "Meta must remain blocked until server credentials and durable storage exist");
    const metaLoginMissing = await fetch(base + "/api/meta/login-url", { method: "POST", headers, body: "{}" });
    assert.equal(metaLoginMissing.status, 503, "Facebook login must not pretend to work without Meta app setup");
    const createPageDisabled = await fetch(base + "/api/meta/pages/create-country", { method: "POST", headers, body: JSON.stringify({ countryCode: "IN", countryName: "India" }) });
    assert.equal(createPageDisabled.status, 409, "Page creation must remain blocked until Meta allowlists the app");
    assert.equal((await createPageDisabled.json()).error, "meta_page_creation_not_enabled");
    const metaPages = await fetch(base + "/api/meta/pages", { headers });
    assert.equal(metaPages.status, 200);
    assert.equal((await metaPages.json()).connected, false);
    const mapInvalid = await fetch(base + "/api/meta/mapping", { method: "PUT", headers, body: JSON.stringify({ countryCode: "IND", pageId: "1" }) });
    assert.equal(mapInvalid.status, 422);
    const approveBeforeQuality = await fetch(base + "/api/jobs/" + job.id + "/approve-publish", { method: "POST", headers, body: "{}" });
    assert.equal(approveBeforeQuality.status, 409);
    assert.equal((await approveBeforeQuality.json()).error, "passed_quality_check_required");
    const publishBeforeApproval = await fetch(base + "/api/jobs/" + job.id + "/publish", { method: "POST", headers, body: "{}" });
    assert.equal(publishBeforeApproval.status, 409);
    assert.equal((await publishBeforeApproval.json()).error, "owner_approval_required");
    const engines = await fetch(base + "/api/engines", { headers });
    assert.equal(engines.status, 200);
    const engineData = await engines.json();
    assert.equal(engineData.engines[0].status, "not_configured");
    const presenterEngine = engineData.otherEngines.find(engine => engine.id === "ai_presenter");
    assert.equal(presenterEngine.status, "not_configured");
    assert.equal(presenterEngine.lipSync, true);
    const importBeforeRender = await fetch(base + "/api/jobs/" + job.id + "/import-presenter-video", { method: "POST", headers, body: "{}" });
    assert.equal(importBeforeRender.status, 409, "presenter import must wait for a completed render");
    assert.equal((await importBeforeRender.json()).error, "presenter_video_not_completed");
    const presenterOff = await fetch(base + "/api/jobs/" + job.id + "/generate-presenter", { method: "POST", headers, body: "{}" });
    assert.equal(presenterOff.status, 503, "presenter provider must stay off without API credentials");
    const packageDraft = await fetch(base + "/api/jobs/" + job.id + "/prepare-package", { method: "POST", headers, body: "{}" });
    assert.equal(packageDraft.status, 503, "full package must stay disabled without text provider credentials");
    assert.equal((await packageDraft.json()).error, "research_engine_not_configured", "research must run before package drafting");
    const script = await fetch(base + "/api/jobs/" + job.id + "/generate-script", { method: "POST", headers, body: "{}" });
    assert.equal(script.status, 503, "script generation must remain disabled without provider credentials");
    const logout = await fetch(base + "/auth/logout", { method: "POST", headers: { cookie, origin: base, "content-type": "application/json" }, body: "{}" });
    assert.equal(logout.status, 200);
    const expiredSession = await fetch(base + "/api/session", { headers: { cookie } });
    assert.equal(expiredSession.status, 401);
    for (let attempt = 0; attempt < 8; attempt++) {
      const failed = await fetch(base + "/auth/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ password: "incorrect-password" }) });
      assert.equal(failed.status, 401);
    }
    const throttled = await fetch(base + "/auth/login", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ password: "test-owner-password-long-enough" }) });
    assert.equal(throttled.status, 429);


  } finally {
    child.kill("SIGTERM");
    await rm(dir, { recursive: true, force: true });
  }
});


import { spawnSync } from "node:child_process";
import { writeFile } from "node:fs/promises";

const ffmpegAvailable = !spawnSync("ffmpeg", ["-version"], { encoding: "utf8" }).error;

test("FFmpeg upload, auto-edit and thumbnail endpoints work end-to-end", { skip: !ffmpegAvailable }, async () => {
  const probe = createServer();
  await new Promise(resolve => probe.listen(0, "127.0.0.1", resolve));
  const port = probe.address().port;
  await new Promise(resolve => probe.close(resolve));
  const dir = await mkdtemp(join(tmpdir(), "viyzo-ffmpeg-test-"));
  const token = "test-owner-token-0123456789-abcdefghijkl";
  const child = spawn(process.execPath, ["server.mjs"], {
    cwd: new URL("..", import.meta.url).pathname,
    env: { ...process.env, HOST: "127.0.0.1", PORT: String(port), OWNER_ACCESS_TOKEN: token, DATA_FILE: join(dir, "jobs.json") },
    stdio: "ignore"
  });
  const base = "http://127.0.0.1:" + port;
  const headers = { authorization: "Bearer " + token, "content-type": "application/json" };
  const source = join(dir, "source.mp4");
  try {
    const generated = spawnSync("ffmpeg", [
      "-y", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=15",
      "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=44100",
      "-t", "2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", source
    ], { encoding: "utf8", timeout: 60000 });
    assert.equal(generated.status, 0, generated.stderr);
    let ready = false;
    for (let i = 0; i < 50; i++) {
      try { if ((await fetch(base + "/health")).ok) { ready = true; break; } } catch {}
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.equal(ready, true);
    const created = await fetch(base + "/api/jobs", { method: "POST", headers, body: JSON.stringify({ prompt: "Create an educational video about Japan", countryCode: "JP", language: "en", durationSeconds: 300, presenter: "fictional" }) });
    assert.equal(created.status, 201);
    const { job } = await created.json();
    const sourceBytes = await (await import("node:fs/promises")).readFile(source);
    const uploaded = await fetch(base + "/api/jobs/" + job.id + "/source", { method: "PUT", headers: { authorization: "Bearer " + token, "content-type": "video/mp4" }, body: sourceBytes });
    assert.equal(uploaded.status, 201);
    const edited = await fetch(base + "/api/jobs/" + job.id + "/auto-edit", { method: "POST", headers, body: "{}" });
    assert.equal(edited.status, 200);
    const editedData = await edited.json();
    assert.equal(editedData.job.processedMedia.status, "ready_for_owner_review");
    assert.ok(["branded_title_overlay", "clean_video_frame"].includes(editedData.job.processedMedia.thumbnailStyle));
    assert.match(editedData.job.processedMedia.thumbnailHeadline.replace(/\s+/g, " "), /educational video about Japan/i);
    const quality = await fetch(base + "/api/jobs/" + job.id + "/quality-check", { method: "POST", headers, body: "{}" });
    assert.equal(quality.status, 200);
    const qualityData = await quality.json();
    assert.equal(qualityData.qualityReport.passed, true);
    assert.equal(qualityData.qualityReport.checks.hdResolution, true);
    assert.equal(qualityData.qualityReport.checks.audioTrack, true);
    const thumbnail = await fetch(base + "/api/jobs/" + job.id + "/thumbnail", { headers: { authorization: "Bearer " + token } });
    assert.equal(thumbnail.status, 200);
    assert.match(thumbnail.headers.get("content-type"), /image\/jpeg/);
    assert.ok(Number(thumbnail.headers.get("content-length")) > 100);
    const video = await fetch(base + "/api/jobs/" + job.id + "/edited-video", { headers: { authorization: "Bearer " + token } });
    assert.equal(video.status, 200);
    assert.match(video.headers.get("content-type"), /video\/mp4/);
    const secondCreated = await fetch(base + "/api/jobs", { method: "POST", headers, body: JSON.stringify({ prompt: "Create a short educational video about Japan", countryCode: "JP", language: "en", durationSeconds: 300, presenter: "fictional" }) });
    assert.equal(secondCreated.status, 201);
    const { job: secondJob } = await secondCreated.json();
    const secondUpload = await fetch(base + "/api/jobs/" + secondJob.id + "/source", { method: "PUT", headers: { authorization: "Bearer " + token, "content-type": "video/mp4" }, body: sourceBytes });
    assert.equal(secondUpload.status, 201);
    const postProduced = await fetch(base + "/api/jobs/" + secondJob.id + "/post-produce", { method: "POST", headers, body: "{}" });
    assert.equal(postProduced.status, 200);
    const postProductionData = await postProduced.json();
    assert.equal(postProductionData.qualityReport.passed, true);
    assert.equal(postProductionData.job.processedMedia.status, "ready_for_owner_review");
    assert.equal(postProductionData.job.qualityReport.passed, true);

  } finally {
    child.kill("SIGTERM");
    await rm(dir, { recursive: true, force: true });
  }
});

test("owner password login works without a static bearer token", async () => {
  const probe = createServer();
  await new Promise(resolve => probe.listen(0, "127.0.0.1", resolve));
  const port = probe.address().port;
  await new Promise(resolve => probe.close(resolve));
  const dir = await mkdtemp(join(tmpdir(), "viyzo-owner-only-test-"));
  const child = spawn(process.execPath, ["server.mjs"], {
    cwd: new URL("..", import.meta.url).pathname,
    env: { ...process.env, HOST: "127.0.0.1", PORT: String(port), OWNER_ACCESS_TOKEN: "", OWNER_PASSWORD_HASH: hashOwnerPassword("owner-password-without-bearer"), DATA_FILE: join(dir, "jobs.json") },
    stdio: "ignore"
  });
  const base = "http://127.0.0.1:" + port;
  try {
    let ready = false;
    for (let i = 0; i < 50; i++) {
      try { if ((await fetch(base + "/health")).ok) { ready = true; break; } } catch {}
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.equal(ready, true, "password-only owner configuration should start the API");
    const login = await fetch(base + "/auth/login", { method: "POST", headers: { "content-type": "application/json", origin: base }, body: JSON.stringify({ password: "owner-password-without-bearer" }) });
    assert.equal(login.status, 200);
    const cookie = login.headers.get("set-cookie").split(";")[0];
    const jobs = await fetch(base + "/api/jobs", { headers: { cookie } });
    assert.equal(jobs.status, 200);
  } finally {
    child.kill("SIGTERM");
    await rm(dir, { recursive: true, force: true });
  }
});
