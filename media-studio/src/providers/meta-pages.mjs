import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import { readFile, writeFile, mkdir, rename, chmod, stat } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const GRAPH_VERSION = process.env.META_GRAPH_API_VERSION || "v26.0";
const STORE_FILE = resolve(process.env.META_TOKEN_STORE_FILE || "./data/meta-connections.enc");
function encryptionKey() {
  const raw = process.env.META_TOKEN_ENCRYPTION_KEY || "";
  if (!/^[a-fA-F0-9]{64}$/.test(raw)) return null;
  return Buffer.from(raw, "hex");
}
export function getMetaStatus() {
  const required = ["META_APP_ID", "META_APP_SECRET", "META_OAUTH_REDIRECT_URI", "META_TOKEN_ENCRYPTION_KEY", "META_TOKEN_STORE_FILE"];
  const missing = required.filter(k => !process.env[k]);
  const configured = missing.length === 0 && !!encryptionKey();
  return {
    configured,
    missing: missing.filter(k => k !== "META_TOKEN_ENCRYPTION_KEY" || !encryptionKey()),
    graphVersion: GRAPH_VERSION,
    capabilities: { facebookLogin: true, managedPageDiscovery: true, countryPageMapping: true, videoPublishing: true, automaticPageCreation: configured && process.env.META_PAGE_CREATION_ENABLED === "true" },
    note: "Meta app credentials, encrypted durable token storage, Page permissions and Meta review are required. Page creation through API requires Meta allowlisting."
  };
}
export function createMetaLoginUrl(state) {
  if (!getMetaStatus().configured) throw new Error("meta_setup_incomplete");
  const u = new URL("https://www.facebook.com/" + GRAPH_VERSION + "/dialog/oauth");
  u.search = new URLSearchParams({
    client_id: process.env.META_APP_ID,
    redirect_uri: process.env.META_OAUTH_REDIRECT_URI,
    state,
    response_type: "code",
    scope: "pages_show_list,pages_read_engagement,pages_manage_posts,pages_manage_metadata"
  }).toString();
  return u.toString();
}
async function graph(path, params) {
  const u = new URL("https://graph.facebook.com/" + GRAPH_VERSION + path);
  for (const [k,v] of Object.entries(params || {})) if (v !== undefined && v !== null) u.searchParams.set(k, String(v));
  const response = await fetch(u, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(15000) });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || body.error) throw new Error("meta_api_" + (body.error?.code || response.status));
  return body;
}
async function readEncrypted() {
  try {
    const envelope = JSON.parse(await readFile(STORE_FILE, "utf8"));
    const key = encryptionKey();
    if (!key) throw new Error("meta_encryption_key_missing");
    const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(envelope.iv, "base64"));
    decipher.setAuthTag(Buffer.from(envelope.tag, "base64"));
    return JSON.parse(Buffer.concat([decipher.update(Buffer.from(envelope.data, "base64")), decipher.final()]).toString("utf8"));
  } catch (e) { if (e.code === "ENOENT") return { userToken: "", pages: [], mappings: {} }; throw e; }
}
async function writeEncrypted(data) {
  const key = encryptionKey();
  if (!key) throw new Error("meta_encryption_key_missing");
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const encrypted = Buffer.concat([cipher.update(JSON.stringify(data), "utf8"), cipher.final()]);
  await mkdir(dirname(STORE_FILE), { recursive: true, mode: 0o700 });
  const tmp = STORE_FILE + ".tmp";
  await writeFile(tmp, JSON.stringify({ iv: iv.toString("base64"), tag: cipher.getAuthTag().toString("base64"), data: encrypted.toString("base64") }), { mode: 0o600 });
  await chmod(tmp, 0o600);
  await rename(tmp, STORE_FILE);
}
export async function finishMetaLogin(code) {
  if (!getMetaStatus().configured) throw new Error("meta_setup_incomplete");
  const short = await graph("/oauth/access_token", { client_id: process.env.META_APP_ID, client_secret: process.env.META_APP_SECRET, redirect_uri: process.env.META_OAUTH_REDIRECT_URI, code });
  const long = await graph("/oauth/access_token", { grant_type: "fb_exchange_token", client_id: process.env.META_APP_ID, client_secret: process.env.META_APP_SECRET, fb_exchange_token: short.access_token });
  let pageData = await graph("/me/accounts", { fields: "id,name,tasks,access_token", limit: 100, access_token: long.access_token });
  const allPages = [...(pageData.data || [])];
  for (let page = 0; page < 4 && pageData.paging?.next; page++) {
    const next = new URL(pageData.paging.next);
    if (next.hostname !== "graph.facebook.com" || next.protocol !== "https:") throw new Error("meta_pagination_url_invalid");
    const response = await fetch(next, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(15000) });
    pageData = await response.json().catch(() => ({}));
    if (!response.ok || pageData.error) throw new Error("meta_api_" + (pageData.error?.code || response.status));
    allPages.push(...(pageData.data || []));
  }
  const safePages = allPages.map(p => ({ id: String(p.id), name: String(p.name || ""), tasks: Array.isArray(p.tasks) ? p.tasks : [], accessToken: String(p.access_token || "") }));
  await writeEncrypted({ userToken: long.access_token, pages: safePages, mappings: {}, connectedAt: new Date().toISOString() });
  return safePages.map(({ id, name, tasks }) => ({ id, name, tasks }));
}
export async function listMetaPages() {
  const store = await readEncrypted();
  return { connected: !!store.userToken, pages: store.pages.map(({ id, name, tasks }) => ({ id, name, tasks })), mappings: store.mappings || {} };
}
export async function mapMetaPage(countryCode, pageId) {
  if (!/^[A-Z]{2}$/.test(countryCode)) throw new Error("invalid_country_code");
  const store = await readEncrypted();
  const page = store.pages.find(p => p.id === String(pageId));
  if (!page) throw new Error("page_not_authorized");
  if (!page.tasks.includes("CREATE_CONTENT")) throw new Error("page_missing_create_content_task");
  if (Object.entries(store.mappings || {}).some(([code, mappedId]) => code !== countryCode && mappedId === page.id)) throw new Error("page_already_assigned_to_another_country");
  store.mappings = { ...(store.mappings || {}), [countryCode]: page.id };
  await writeEncrypted(store);
  return { countryCode, page: { id: page.id, name: page.name } };
}
export async function createMetaCountryPage({ countryCode, countryName }) {
  if (process.env.META_PAGE_CREATION_ENABLED !== "true") return { ok: false, error: "meta_page_creation_not_enabled" };
  if (!getMetaStatus().configured) return { ok: false, error: "meta_setup_incomplete" };
  if (!/^[A-Z]{2}$/.test(countryCode) || typeof countryName !== "string" || !countryName.trim()) return { ok: false, error: "invalid_country" };
  const store = await readEncrypted();
  if (!store.userToken) return { ok: false, error: "meta_not_connected" };
  if (store.mappings?.[countryCode]) return { ok: false, error: "country_page_already_mapped" };
  const name = ("Viyzo " + countryName.trim()).slice(0, 75);
  const about = ("Official Viyzo video page for audiences in " + countryName.trim() + ".").slice(0, 255);
  const form = new URLSearchParams({ name, about, category_enum: "VIDEO_CREATOR", access_token: store.userToken });
  try {
    const response = await fetch("https://graph.facebook.com/" + GRAPH_VERSION + "/me/accounts", {
      method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body: form, signal: AbortSignal.timeout(20000)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || result.error || !result.id) return { ok: false, error: "meta_page_creation_failed", providerCode: result.error?.code || response.status };
    let created = { id: String(result.id), name, tasks: Array.isArray(result.tasks) ? result.tasks : [], accessToken: String(result.access_token || "") };
    if (!created.accessToken || !created.tasks.includes("CREATE_CONTENT")) {
      const refreshed = await graph("/me/accounts", { fields: "id,name,tasks,access_token", limit: 100, access_token: store.userToken });
      const match = (refreshed.data || []).find(p => String(p.id) === created.id);
      if (match) created = { id: String(match.id), name: String(match.name || name), tasks: Array.isArray(match.tasks) ? match.tasks : [], accessToken: String(match.access_token || "") };
    }
    if (!created.accessToken || !created.tasks.includes("CREATE_CONTENT")) return { ok: false, error: "created_page_token_or_task_unavailable" };
    store.pages = [...(store.pages || []).filter(p => p.id !== created.id), created];
    store.mappings = { ...(store.mappings || {}), [countryCode]: created.id };
    await writeEncrypted(store);
    return { ok: true, countryCode, page: { id: created.id, name: created.name } };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "meta_page_creation_timeout" : "meta_page_creation_unavailable" };
  }
}
export async function publishMetaVideo({ countryCode, videoPath, title, description }) {
  const store = await readEncrypted();
  if (!store.userToken) return { ok: false, error: "meta_not_connected" };
  const pageId = store.mappings?.[countryCode];
  const page = store.pages.find(p => p.id === pageId);
  if (!page) return { ok: false, error: "country_page_not_mapped" };
  if (!page.tasks.includes("CREATE_CONTENT") || !page.accessToken) return { ok: false, error: "page_missing_create_content_task" };
  const info = await stat(videoPath).catch(() => null);
  if (!info?.isFile()) return { ok: false, error: "edited_video_file_missing" };
  if (info.size > 256 * 1024 * 1024) return { ok: false, error: "video_exceeds_direct_upload_limit" };
  const bytes = await readFile(videoPath);
  const form = new FormData();
  form.set("access_token", page.accessToken);
  form.set("title", String(title || "Viyzo video").slice(0, 255));
  form.set("description", String(description || "").slice(0, 5000));
  form.set("source", new Blob([bytes], { type: "video/mp4" }), "viyzo-video.mp4");
  try {
    const response = await fetch("https://graph-video.facebook.com/" + GRAPH_VERSION + "/" + encodeURIComponent(page.id) + "/videos", {
      method: "POST", body: form, signal: AbortSignal.timeout(120000)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || result.error || !result.id) return { ok: false, error: "meta_publish_failed", providerCode: result.error?.code || response.status };
    return { ok: true, pageId: page.id, pageName: page.name, videoId: String(result.id), postId: result.post_id ? String(result.post_id) : null };
  } catch (error) {
    return { ok: false, error: error.name === "AbortError" ? "meta_publish_timeout" : "meta_publish_unavailable" };
  }
}
