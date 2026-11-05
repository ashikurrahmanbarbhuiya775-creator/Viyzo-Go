import http from "node:http";
import { randomUUID, timingSafeEqual } from "node:crypto";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { createVideoJob, approveJob, completeStage } from "./src/domain/video-job.mjs";
import { getTextEngineStatus, generateScriptDraft } from "./src/providers/openai-compatible.mjs";

const HOST = process.env.HOST || "127.0.0.1";
const PORT = Number(process.env.PORT || 8787);
const OWNER_TOKEN = process.env.OWNER_ACCESS_TOKEN || "";
const DATA_FILE = resolve(process.env.DATA_FILE || "./data/jobs.json");
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
        engines: [getTextEngineStatus()],
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
