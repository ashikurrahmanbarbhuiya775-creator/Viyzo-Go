import http from "node:http";
import { createWriteStream, createReadStream } from "node:fs";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { mkdir, readFile, rename, writeFile, unlink, stat } from "node:fs/promises";
import { pipeline } from "node:stream/promises";
import { Transform } from "node:stream";
import { dirname, resolve } from "node:path";
import { createVideoJob, approveJob, completeStage } from "./src/domain/video-job.mjs";
import { getTextEngineStatus, generateScriptDraft, generateMetadataDraft } from "./src/providers/openai-compatible.mjs";
import { extensionForMime, inspectVideo, autoEditVideo, getMediaEngineStatus, MEDIA_LIMITS } from "./src/providers/ffmpeg-pipeline.mjs";

const HOST = process.env.HOST || "127.0.0.1";
const PORT = Number(process.env.PORT || 8787);
const OWNER_TOKEN = process.env.OWNER_ACCESS_TOKEN || "";
const DATA_FILE = resolve(process.env.DATA_FILE || "./data/jobs.json");
const MEDIA_DIR = resolve(dirname(DATA_FILE), "media");
const MAX_BODY = 32 * 1024;

if (OWNER_TOKEN.length < 32) {
  console.error("Refusing to start: set OWNER_ACCESS_TOKEN to a random secret of at least 32 characters.");
  process.exit(1);
}

function authorized(req) {
  const header = req.headers.authorization || "";
  const candidate = header.startsWith("Bearer ") ? header.slice(7) : "";
  const a = Buffer.from(candidate);
  const b = Buffer.from(OWNER_TOKEN);
  return a.length === b.length && timingSafeEqual(a, b);
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
    if (!authorized(req)) return send(res, 401, { error: "unauthorized" });

    if (req.method === "GET" && url.pathname === "/api/engines") {
      return send(res, 200, {
        engines: [getTextEngineStatus(), await getMediaEngineStatus()],
        otherEngines: ["research", "voice", "video_render", "auto_edit", "metadata", "thumbnail", "publishing"]
          .map(id => ({ id, status: "not_configured" }))
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
      await mkdir(MEDIA_DIR, { recursive: true });
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

    const match = url.pathname.match(/^\/api\/jobs\/([0-9a-f-]{36})\/(stage|approve)$/i);
    if (match && req.method === "POST") {
      const [, id, action] = match;
      const store = await readStore();
      const index = store.jobs.findIndex(job => job.id === id);
      if (index < 0) return send(res, 404, { error: "job_not_found" });
      const body = await readBody(req);
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
