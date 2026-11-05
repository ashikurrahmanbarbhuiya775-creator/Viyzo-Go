import http from "node:http";
import { createWriteStream, createReadStream } from "node:fs";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { createOwnerAuth, OWNER_SESSION_TTL_SECONDS } from "./src/security/owner-auth.mjs";
import { mkdir, readFile, rename, writeFile, unlink, stat, chmod } from "node:fs/promises";
import { pipeline } from "node:stream/promises";
import { Transform } from "node:stream";
import { dirname, resolve } from "node:path";
import { createVideoJob, approveJob, completeStage } from "./src/domain/video-job.mjs";
import { validateResearchReport } from "./src/domain/research-safety.mjs";
import { validateVisualAssets } from "./src/domain/visual-evidence-safety.mjs";
import { getTextEngineStatus, generateScriptDraft, generateMetadataDraft } from "./src/providers/openai-compatible.mjs";
import { getCountryResearchStatus, researchCountry } from "./src/providers/country-research.mjs";
import { extensionForMime, inspectVideo, autoEditVideo, qualityCheckVideo, getMediaEngineStatus, MEDIA_LIMITS } from "./src/providers/ffmpeg-pipeline.mjs";
import { getAvatarEngineStatus, generateAvatarVideo, getAvatarVideoStatus, isTrustedAvatarVideoUrl } from "./src/providers/heygen-avatar.mjs";

const HOST = process.env.HOST || "127.0.0.1";
const PORT = Number(process.env.PORT || 8787);
const OWNER_TOKEN = process.env.OWNER_ACCESS_TOKEN || "";
const OWNER_PASSWORD_HASH = process.env.OWNER_PASSWORD_HASH || "";
const ownerAuth = createOwnerAuth(OWNER_PASSWORD_HASH);
const SESSION_COOKIE = "viyzo_owner";
const loginFailures = new Map();
const LOGIN_WINDOW_MS = 15 * 60 * 1000;
const LOGIN_MAX_FAILURES = 8;
function loginBlocked(ip) {
  const record = loginFailures.get(ip);
  if (!record || record.resetAt <= Date.now()) { loginFailures.delete(ip); return false; }
  return record.count >= LOGIN_MAX_FAILURES;
}
function recordLoginFailure(ip) {
  const now = Date.now();
  const record = loginFailures.get(ip);
  if (!record || record.resetAt <= now) loginFailures.set(ip, { count: 1, resetAt: now + LOGIN_WINDOW_MS });
  else record.count += 1;
}
const DATA_FILE = resolve(process.env.DATA_FILE || "./data/jobs.json");
const MEDIA_DIR = resolve(dirname(DATA_FILE), "media");
const MAX_BODY = 32 * 1024;

if (OWNER_TOKEN.length < 32 && !ownerAuth.configured) {
  console.error("Refusing to start: configure OWNER_PASSWORD_HASH or a 32+ character OWNER_ACCESS_TOKEN.");
  process.exit(1);
}

function cookieValue(req, name) {
  const cookies = String(req.headers.cookie || "").split(";");
  for (const item of cookies) {
    const index = item.indexOf("=");
    if (index > 0 && item.slice(0, index).trim() === name) return item.slice(index + 1).trim();
  }
  return "";
}
function authMethod(req) {
  const header = req.headers.authorization || "";
  const candidate = header.startsWith("Bearer ") ? header.slice(7) : "";
  const a = Buffer.from(candidate);
  const b = Buffer.from(OWNER_TOKEN);
  if (OWNER_TOKEN.length >= 32 && a.length === b.length && timingSafeEqual(a, b)) return "bearer";
  return ownerAuth.isSessionValid(cookieValue(req, SESSION_COOKIE)) ? "session" : "";
}
function csrfValid(req, url, method) {
  if (method === "GET" || method === "HEAD" || method === "OPTIONS") return true;
  if (authMethod(req) !== "session") return true;
  const origin = req.headers.origin;
  return typeof origin === "string" && origin === url.origin;
}
function sessionCookie(token, maxAge = OWNER_SESSION_TTL_SECONDS) {
  return SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge + (process.env.NODE_ENV === "production" ? "; Secure" : "");
}
function sendHtml(res, html) {
  res.writeHead(200, {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-security-policy": "default-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'"
  });
  res.end(html);
}
function send(res, status, data) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-security-policy": "default-src 'none'; frame-ancestors 'none'"
  });
  res.end(JSON.stringify(data));
}
async function readStore() {
  try {
    const parsed = JSON.parse(await readFile(DATA_FILE, "utf8"));
    return Array.isArray(parsed.jobs) ? parsed : { jobs: [] };
  } catch (e) {
    if (e.code === "ENOENT") return { jobs: [] };
    throw e;
  }
}
async function saveStore(store) {
  await mkdir(dirname(DATA_FILE), { recursive: true });
  const temp = DATA_FILE + "." + randomUUID() + ".tmp";
  await writeFile(temp, JSON.stringify(store, null, 2), { mode: 0o600 });
  await rename(temp, DATA_FILE);
}
async function readBody(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw Object.assign(new Error("body_too_large"), { status: 413 });
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}"); }
  catch { throw Object.assign(new Error("invalid_json"), { status: 400 }); }
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, "http://" + (req.headers.host || "localhost"));
    if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/index.html")) {
      return sendHtml(res, await readFile(resolve("index.html"), "utf8"));
    }
    if (req.method === "GET" && url.pathname === "/health") {
      return send(res, 200, { ok: true, service: "viyzo-media-studio-api", mode: "local-demo" });
    }
    if (req.method === "POST" && url.pathname === "/auth/login") {
      if (req.headers.origin && req.headers.origin !== url.origin) return send(res, 403, { error: "origin_not_allowed" });
      if (!ownerAuth.configured) return send(res, 503, { error: "owner_password_login_not_configured", hint: "Create OWNER_PASSWORD_HASH using scripts/hash-owner-password.mjs." });
      const loginIp = req.socket.remoteAddress || "unknown";
      if (loginBlocked(loginIp)) return send(res, 429, { error: "login_rate_limited", retryAfterSeconds: 900 });
      const body = await readBody(req);
      if (!ownerAuth.verifyPassword(body.password)) { recordLoginFailure(loginIp); return send(res, 401, { error: "invalid_credentials" }); }
      loginFailures.delete(loginIp);
      const token = ownerAuth.createSession();
      res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "set-cookie": sessionCookie(token), "x-content-type-options": "nosniff" });
      return res.end(JSON.stringify({ ok: true, owner: "owner", expiresInSeconds: OWNER_SESSION_TTL_SECONDS }));
    }
    if (req.method === "GET" && url.pathname === "/api/session") {
      return authMethod(req) ? send(res, 200, { authenticated: true, owner: "owner" }) : send(res, 401, { authenticated: false });
    }
    if (!authMethod(req)) return send(res, 401, { error: "unauthorized" });
    if (!csrfValid(req, url, req.method)) return send(res, 403, { error: "origin_not_allowed" });
    if (req.method === "POST" && url.pathname === "/auth/logout") {
      ownerAuth.revokeSession(cookieValue(req, SESSION_COOKIE));
      res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "set-cookie": sessionCookie("", 0) });
      return res.end(JSON.stringify({ ok: true }));
    }

    if (req.method === "GET" && url.pathname === "/api/system-status") {
      let storageWritable = false;
      let storageBytes = null;
      const probe = resolve(dirname(DATA_FILE), ".viyzo-storage-probe-" + randomUUID());
      try {
        await mkdir(dirname(DATA_FILE), { recursive: true });
        await writeFile(probe, "ok", { flag: "wx", mode: 0o600 });
        await unlink(probe);
        storageWritable = true;
        try { storageBytes = (await stat(dirname(DATA_FILE))).size; } catch {}
      } catch {
        try { await unlink(probe); } catch {}
      }
      const engines = {
        script: getTextEngineStatus(),
        research: getCountryResearchStatus(),
        presenter: getAvatarEngineStatus(),
        video: await getMediaEngineStatus()
      };
      return send(res, 200, {
        ok: storageWritable,
        service: "viyzo-media-studio-api",
        storage: { writable: storageWritable, directory: "configured-data-directory", directoryStatBytes: storageBytes },
        engines,
        checks: {
          ownerAuthentication: ownerAuth.configured ? "configured" : "not_configured",
          backup: "operator_managed",
          publishing: "not_configured"
        }
      });
    }

    if (req.method === "GET" && url.pathname === "/api/engines") {
      return send(res, 200, {
        engines: [getTextEngineStatus(), getCountryResearchStatus(), await getMediaEngineStatus()],
        otherEngines: [getAvatarEngineStatus(), ...["voice", "video_render", "publishing"].map(id => ({ id, status: "not_configured" }))]
      });
    }
    if (req.method === "GET" && url.pathname === "/api/jobs") {
      const store = await readStore();
      return send(res, 200, { jobs: store.jobs });
    }
    if (req.method === "POST" && url.pathname === "/api/jobs") {
      const body = await readBody(req);
      const created = createVideoJob(body);
      if (!created.ok) return send(res, 422, { error: "validation_failed", details: created.errors });
      const store = await readStore();
      store.jobs.unshift(created.job);
      await saveStore(store);
      return send(res, 201, { job: created.job });
    }

    const visualAssetsMatch = url.pathname.match(/^\\/api\\/jobs\\/([0-9a-f-]{36})\\/visual-assets$/i);
    if (req.method === "POST" && visualAssetsMatch) {
      const [, id] = visualAssetsMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const job = store.jobs[index];
      if (job.ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const body = await readBody(req);
      const result = validateVisualAssets(body.assets, job.countryCode);
      if (!result.ok) return send(res, 422, { error: "visual_evidence_validation_failed", validation: result });
      job.visualAssets = body.assets;
      job.visualEvidenceReport = result;
      job.updatedAt = new Date().toISOString();
      await saveStore(store);
      return send(res, 200, { job, validation: result, message: "Visual asset provenance, country match, usage rights and disclosures passed structural checks. Human review is still required." });
    }

    const sourceSuffix = "/source";
    const sourcePrefix = "/api/jobs/";
    const sourceId = url.pathname.startsWith(sourcePrefix) && url.pathname.endsWith(sourceSuffix) ? url.pathname.slice(sourcePrefix.length, -sourceSuffix.length) : "";
    if (req.method === "PUT" && /^[0-9a-f-]{36}$/i.test(sourceId)) {
      const id = sourceId;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      if (store.jobs[index].ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const mime = String(req.headers["content-type"] || "").split(";")[0].trim().toLowerCase();
      const extension = extensionForMime(mime);
      if (!extension) return send(res, 415, { error: "unsupported_video_type", allowed: ["video/mp4", "video/webm", "video/quicktime"] });
      const declaredLength = Number(req.headers["content-length"] || 0);
      if (declaredLength > MEDIA_LIMITS.maxSourceBytes) return send(res, 413, { error: "video_too_large" });
      await mkdir(MEDIA_DIR, { recursive: true, mode: 0o700 });
      await chmod(MEDIA_DIR, 0o700);
      const key = id + "-source" + extension;
      const finalPath = resolve(MEDIA_DIR, key);
      const tempPath = finalPath + ".part";
      let received = 0;
      const limiter = new Transform({
        transform(chunk, encoding, callback) {
          received += chunk.length;
          if (received > MEDIA_LIMITS.maxSourceBytes) callback(Object.assign(new Error("video_too_large"), { status: 413 }));
          else callback(null, chunk);
        }
      });
      try {
        await pipeline(req, limiter, createWriteStream(tempPath, { flags: "wx", mode: 0o600 }));
        const probe = await inspectVideo(tempPath);
        const previousKey = store.jobs[index].sourceMedia?.storageKey;
        if (previousKey && previousKey !== key) await unlink(resolve(MEDIA_DIR, previousKey)).catch(() => {});
        await unlink(resolve(MEDIA_DIR, id + "-edited.mp4")).catch(() => {});
        await unlink(resolve(MEDIA_DIR, id + "-thumbnail.jpg")).catch(() => {});
        delete store.jobs[index].processedMedia;
        await rename(tempPath, finalPath);
        store.jobs[index].sourceMedia = { storageKey: key, mime, bytes: probe.bytes, durationSeconds: probe.durationSeconds, width: probe.width, height: probe.height, hasAudio: probe.hasAudio };
        store.jobs[index].updatedAt = new Date().toISOString();
        await saveStore(store);
        return send(res, 201, { job: store.jobs[index], message: "Source video stored and validated." });
      } catch (error) {
        await unlink(tempPath).catch(() => {});
        await unlink(finalPath).catch(() => {});
        return send(res, error.status || 422, { error: error.status ? error.message : "video_validation_failed" });
      }
    }


    const postProduceMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/post-produce$/i);
    if (postProduceMatch && req.method === "POST") {
      const [, id] = postProduceMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const job = store.jobs[index];
      if (job.ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const source = job.sourceMedia;
      const validSourceKey = [id + "-source.mp4", id + "-source.webm", id + "-source.mov"].includes(source?.storageKey);
      if (!validSourceKey) return send(res, 409, { error: "source_video_required" });
      try {
        const result = await autoEditVideo(resolve(MEDIA_DIR, source.storageKey), id);
        const qualityReport = await qualityCheckVideo(result.outputPath);
        job.processedMedia = {
          editedKey: id + "-edited.mp4",
          thumbnailKey: id + "-thumbnail.jpg",
          editedBytes: result.editedBytes,
          thumbnailBytes: result.thumbnailBytes,
          profile: result.profile,
          status: "ready_for_owner_review"
        };
        job.qualityReport = qualityReport;
        job.updatedAt = qualityReport.checkedAt;
        await saveStore(store);
        return send(res, qualityReport.passed ? 200 : 422, {
          job,
          qualityReport,
          message: qualityReport.passed
            ? "Auto-edit, thumbnail and technical quality check completed."
            : "The edited file was created but did not pass technical quality checks."
        });
      } catch {
        return send(res, 422, { error: "post_production_failed", hint: "Check FFmpeg installation and the source video." });
      }
    }

    const editSuffix = "/auto-edit";
    const editId = url.pathname.startsWith(sourcePrefix) && url.pathname.endsWith(editSuffix) ? url.pathname.slice(sourcePrefix.length, -editSuffix.length) : "";
    if (req.method === "POST" && /^[0-9a-f-]{36}$/i.test(editId)) {
      const id = editId;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      if (store.jobs[index].ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const source = store.jobs[index].sourceMedia;
      const validSourceKey = [id + "-source.mp4", id + "-source.webm", id + "-source.mov"].includes(source?.storageKey);
      if (!validSourceKey) return send(res, 409, { error: "source_video_required" });
      try {
        const result = await autoEditVideo(resolve(MEDIA_DIR, source.storageKey), id);
        store.jobs[index].processedMedia = {
          editedKey: id + "-edited.mp4",
          thumbnailKey: id + "-thumbnail.jpg",
          editedBytes: result.editedBytes,
          thumbnailBytes: result.thumbnailBytes,
          profile: result.profile,
          status: "ready_for_owner_review"
        };
        store.jobs[index].updatedAt = new Date().toISOString();
        await saveStore(store);
        return send(res, 200, { job: store.jobs[index] });
      } catch {
        return send(res, 422, { error: "auto_edit_failed", hint: "Check that FFmpeg is installed and the source video is valid." });
      }
    }

    const mediaSuffix = url.pathname.endsWith("/thumbnail") ? "thumbnail" : url.pathname.endsWith("/edited-video") ? "edited-video" : "";
    const mediaId = mediaSuffix && url.pathname.startsWith(sourcePrefix) ? url.pathname.slice(sourcePrefix.length, -(mediaSuffix.length + 1)) : "";
    if (req.method === "GET" && /^[0-9a-f-]{36}$/i.test(mediaId)) {
      const id = mediaId;
      const kind = mediaSuffix;
      const store = await readStore();
      const job = store.jobs.find(item => item.id === id);
      if (!job?.processedMedia) return send(res, 404, { error: "processed_media_not_found" });
      const key = kind === "thumbnail" ? job.processedMedia.thumbnailKey : job.processedMedia.editedKey;
      if (key !== id + (kind === "thumbnail" ? "-thumbnail.jpg" : "-edited.mp4")) return send(res, 404, { error: "media_not_found" });
      const filePath = resolve(MEDIA_DIR, key);
      try {
        const info = await stat(filePath);
        res.writeHead(200, { "content-type": kind === "thumbnail" ? "image/jpeg" : "video/mp4", "content-length": info.size, "cache-control": "private, no-store", "x-content-type-options": "nosniff" });
        return createReadStream(filePath).pipe(res);
      } catch { return send(res, 404, { error: "media_file_missing" }); }
    }

    const researchMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/research$/i);
    if (researchMatch && req.method === "POST") {
      const [, id] = researchMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const job = store.jobs[index];
      if (job.ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const result = await researchCountry(job);
      if (!result.ok) return send(res, result.error === "research_engine_not_configured" ? 503 : 502, { error: result.error, providerStatus: result.status || null });
      job.researchReport = result.report;
      job.researchModel = result.model;
      job.researchedAt = result.report.researchedAt;
      job.updatedAt = new Date().toISOString();
      await saveStore(store);
      return send(res, 200, { job, message: "Live country research completed with cited web sources. Review the facts before production." });
    }

    const scriptMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/generate-script$/i);
    if (scriptMatch && req.method === "POST") {
      const [, id] = scriptMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      if (store.jobs[index].ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const generated = await generateScriptDraft(store.jobs[index]);
      if (!generated.ok) {
        const status = generated.error === "text_engine_not_configured" ? 503 : 502;
        return send(res, status, { error: generated.error, providerStatus: generated.status });
      }
      store.jobs[index].scriptDraft = generated.script;
      store.jobs[index].scriptModel = generated.model;
      store.jobs[index].updatedAt = new Date().toISOString();
      await saveStore(store);
      return send(res, 200, { job: store.jobs[index] });
    }

    const metadataSuffix = "/generate-metadata";
    const metadataPrefix = "/api/jobs/";
    const metadataId = url.pathname.startsWith(metadataPrefix) && url.pathname.endsWith(metadataSuffix)
      ? url.pathname.slice(metadataPrefix.length, -metadataSuffix.length) : "";
    if (req.method === "POST" && /^[0-9a-f-]{36}$/i.test(metadataId)) {
      const id = metadataId;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      if (store.jobs[index].ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const generated = await generateMetadataDraft(store.jobs[index]);
      if (!generated.ok) {
        const status = generated.error === "text_engine_not_configured" ? 503 : 502;
        return send(res, status, { error: generated.error, providerStatus: generated.status });
      }
      store.jobs[index].metadataDraft = generated.metadata;
      store.jobs[index].metadataModel = generated.model;
      store.jobs[index].updatedAt = new Date().toISOString();
      await saveStore(store);
      return send(res, 200, { job: store.jobs[index] });
    }

    const packageMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/prepare-package$/i);
    if (packageMatch && req.method === "POST") {
      const [, id] = packageMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const job = store.jobs[index];
      if (job.ownerApproved) return send(res, 409, { error: "job_already_approved" });

      if (!job.researchReport || !validateResearchReport(job.researchReport, job.countryCode).ok) {
        const research = await researchCountry(job);
        if (!research.ok) return send(res, research.error === "research_engine_not_configured" ? 503 : 502, { error: research.error, stage: "research", providerStatus: research.status || null });
        job.researchReport = research.report;
        job.researchModel = research.model;
        job.researchedAt = research.report.researchedAt;
      }
      const evidenceCheck = validateResearchReport(job.researchReport, job.countryCode);
      if (!evidenceCheck.ok) return send(res, 422, { error: "research_evidence_validation_failed", validation: evidenceCheck });

      // Prepare the complete editorial package in one owner action. Provider calls
      // remain server-side and are never attempted unless credentials are configured.
      if (!job.scriptDraft) {
        const script = await generateScriptDraft(job);
        if (!script.ok) {
          const status = script.error === "text_engine_not_configured" ? 503 : 502;
          return send(res, status, { error: script.error, stage: "script", providerStatus: script.status });
        }
        job.scriptDraft = script.script;
        job.scriptModel = script.model;
      }
      if (!job.metadataDraft) {
        const metadata = await generateMetadataDraft(job);
        if (!metadata.ok) {
          job.updatedAt = new Date().toISOString();
          await saveStore(store);
          const status = metadata.error === "text_engine_not_configured" ? 503 : 502;
          return send(res, status, { error: metadata.error, stage: "metadata", scriptSaved: true, providerStatus: metadata.status });
        }
        job.metadataDraft = metadata.metadata;
        job.metadataModel = metadata.model;
      }
      job.packagePreparedAt = new Date().toISOString();
      job.updatedAt = job.packagePreparedAt;
      await saveStore(store);
      return send(res, 200, { job, message: "Script and publishing metadata drafts are ready for owner review. No video has been rendered yet." });
    }

    const presenterMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/generate-presenter$/i);
    if (presenterMatch && req.method === "POST") {
      const [, id] = presenterMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const job = store.jobs[index];
      if (job.ownerApproved) return send(res, 409, { error: "job_already_approved" });
      if (job.presenter !== "fictional") return send(res, 409, { error: "fictional_presenter_required" });
      if (job.presenterVideo?.videoId && ["processing", "pending", "waiting"].includes(job.presenterVideo.status)) return send(res, 409, { error: "presenter_video_already_processing" });
      const generated = await generateAvatarVideo(job);
      if (!generated.ok) {
        const status = generated.error === "avatar_engine_not_configured" ? 503 : generated.error === "approved_script_required" ? 409 : 502;
        return send(res, status, { error: generated.error, providerStatus: generated.status });
      }
      job.presenterVideo = { videoId: generated.videoId, status: generated.status, provider: generated.provider, requestedAt: new Date().toISOString() };
      job.updatedAt = new Date().toISOString();
      await saveStore(store);
      return send(res, 202, { job, message: "AI presenter video submitted to HeyGen. Provider rendering is asynchronous." });
    }

    const presenterStatusMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/presenter-status$/i);
    if (presenterStatusMatch && req.method === "GET") {
      const [, id] = presenterStatusMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const videoId = store.jobs[index].presenterVideo?.videoId;
      if (!videoId) return send(res, 409, { error: "presenter_video_not_requested" });
      const status = await getAvatarVideoStatus(videoId);
      if (!status.ok) return send(res, status.error === "avatar_engine_not_configured" ? 503 : 502, { error: status.error });
      store.jobs[index].presenterVideo = { ...store.jobs[index].presenterVideo, status: status.status, videoUrl: status.videoUrl, thumbnailUrl: status.thumbnailUrl, checkedAt: new Date().toISOString() };
      store.jobs[index].updatedAt = new Date().toISOString();
      await saveStore(store);
      return send(res, 200, { presenterVideo: store.jobs[index].presenterVideo });
    }



    const importPresenterMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/import-presenter-video$/i);
    if (importPresenterMatch && req.method === "POST") {
      const [, id] = importPresenterMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const job = store.jobs[index];
      if (job.ownerApproved) return send(res, 409, { error: "job_already_approved" });
      if (job.sourceMedia?.storageKey) return send(res, 409, { error: "source_video_already_exists", hint: "Remove or replace the existing source video first." });
      const presenter = job.presenterVideo;
      if (!presenter?.videoId || presenter.status !== "completed" || !presenter.videoUrl) {
        return send(res, 409, { error: "presenter_video_not_completed", hint: "Check presenter status and wait until rendering is completed." });
      }
      if (!isTrustedAvatarVideoUrl(presenter.videoUrl)) return send(res, 422, { error: "untrusted_presenter_video_url" });
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 90000);
      const key = id + "-source.mp4";
      const finalPath = resolve(MEDIA_DIR, key);
      const tempPath = finalPath + ".part";
      try {
        const response = await fetch(presenter.videoUrl, { redirect: "manual", signal: controller.signal, headers: { accept: "video/mp4" } });
        if (!response.ok || response.status >= 300 || !response.body) {
          return send(res, 502, { error: "presenter_video_download_failed", providerStatus: response.status });
        }
        const mime = String(response.headers.get("content-type") || "").split(";")[0].toLowerCase();
        if (mime !== "video/mp4" && mime !== "application/octet-stream") return send(res, 415, { error: "presenter_video_not_mp4" });
        const declaredLength = Number(response.headers.get("content-length") || 0);
        if (declaredLength > MEDIA_LIMITS.maxSourceBytes) return send(res, 413, { error: "video_too_large" });
        await mkdir(MEDIA_DIR, { recursive: true, mode: 0o700 });
        await chmod(MEDIA_DIR, 0o700);
        let received = 0;
        const limiter = new Transform({
          transform(chunk, encoding, callback) {
            received += chunk.length;
            if (received > MEDIA_LIMITS.maxSourceBytes) callback(Object.assign(new Error("video_too_large"), { status: 413 }));
            else callback(null, chunk);
          }
        });
        await pipeline(response.body, limiter, createWriteStream(tempPath, { flags: "wx", mode: 0o600 }));
        const probe = await inspectVideo(tempPath);
        const previousKey = job.sourceMedia?.storageKey;
        await rename(tempPath, finalPath);
        if (previousKey && previousKey !== key) await unlink(resolve(MEDIA_DIR, previousKey)).catch(() => {});
        await unlink(resolve(MEDIA_DIR, id + "-edited.mp4")).catch(() => {});
        await unlink(resolve(MEDIA_DIR, id + "-thumbnail.jpg")).catch(() => {});
        delete job.processedMedia;
        delete job.qualityReport;
        job.sourceMedia = { storageKey: key, mime: "video/mp4", bytes: probe.bytes, durationSeconds: probe.durationSeconds, width: probe.width, height: probe.height, hasAudio: probe.hasAudio, importedFrom: "heygen", importedAt: new Date().toISOString() };
        job.presenterVideo = { ...presenter, importedAt: job.sourceMedia.importedAt, status: "imported_to_private_library" };
        job.updatedAt = job.sourceMedia.importedAt;
        await saveStore(store);
        return send(res, 201, { job, message: "Completed AI presenter video imported and validated in the private media library." });
      } catch (error) {
        await unlink(tempPath).catch(() => {});
        await unlink(finalPath).catch(() => {});
        return send(res, error.status || 502, { error: error.status ? error.message : error.name === "AbortError" ? "presenter_download_timeout" : "presenter_import_validation_failed" });
      } finally {
        clearTimeout(timeout);
      }
    }

    const qualityMatch = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/quality-check$/i);
    if (qualityMatch && req.method === "POST") {
      const [, id] = qualityMatch;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      if (store.jobs[index].ownerApproved) return send(res, 409, { error: "job_already_approved" });
      const media = store.jobs[index].processedMedia;
      if (!media || media.status !== "ready_for_owner_review") return send(res, 409, { error: "edited_video_required" });
      try {
        const report = await qualityCheckVideo(resolve(MEDIA_DIR, media.editedKey));
        store.jobs[index].qualityReport = report;
        store.jobs[index].updatedAt = report.checkedAt;
        await saveStore(store);
        return send(res, report.passed ? 200 : 422, { job: store.jobs[index], qualityReport: report });
      } catch {
        return send(res, 422, { error: "quality_check_failed", hint: "The edited video could not be inspected." });
      }
    }

    const match = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/(stage|approve)$/i);
    if (match && req.method === "POST") {
      const [, id, action] = match;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const body = await readBody(req);
      if (action === "stage" && body.stage === "quality_check" && store.jobs[index].qualityReport?.passed !== true) return send(res, 409, { error: "passed_quality_report_required" });
      const result = action === "stage"
        ? completeStage(store.jobs[index], body.stage)
        : approveJob(store.jobs[index], "local-owner");
      if (!result.ok) return send(res, 409, { error: result.error, expected: result.expected });
      store.jobs[index] = result.job;
      await saveStore(store);
      return send(res, 200, { job: result.job });
    }

    // Deliberately no upload or publishing endpoint exists in this development scaffold.
    return send(res, 404, { error: "not_found" });
  } catch (error) {
    return send(res, error.status || 500, { error: error.status ? error.message : "internal_error" });
  }
});

server.listen(PORT, HOST, () => console.log("Viyzo Media Studio API listening on http://" + HOST + ":" + PORT));
