import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import { readFile, writeFile, mkdir, rename, chmod } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const GRAPH_VERSION = process.env.META_GRAPH_API_VERSION || "v26.0";
const STORE_FILE = resolve(process.env.META_TOKEN_STORE_FILE || "./data/meta-connections.enc");
function encryptionKey() {
  const raw = process.env.META_TOKEN_ENCRYPTION_KEY || "";
  if (!/^[a-fA-F0-9]{64}$/.test(raw)) return null;
  return Buffer.from(raw, "hex");
}
export function getMetaStatus() {
  const required = ["META_APP_ID", "META_APP_SECRET", "META_OAUTH_REDIRECT_URI", "META_TOKEN_ENCRYPTION_KEY"];
  const missing = required.filter(k => !process.env[k]);
  return {
    configured: missing.length === 0 && !!encryptionKey(),
    missing: missing.filter(k => k !== "META_TOKEN_ENCRYPTION_KEY" || !encryptionKey()),
    graphVersion: GRAPH_VERSION,
    capabilities: { facebookLogin: true, managedPageDiscovery: true, countryPageMapping: true, automaticPageCreation: false, videoPublishing: false },
    note: "Meta app credentials, encrypted persistent storage and Meta review are required. Meta does not provide a general API for automatically creating unlimited Pages for a personal profile."
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
    scope: "pages_show_list,pages_read_engagement,pages_manage_posts"
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
  const pages = await graph("/me/accounts", { fields: "id,name,tasks,access_token", limit: 100, access_token: long.access_token });
  const safePages = (pages.data || []).map(p => ({ id: String(p.id), name: String(p.name || ""), tasks: Array.isArray(p.tasks) ? p.tasks : [], accessToken: String(p.access_token || "") }));
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
  store.mappings = { ...(store.mappings || {}), [countryCode]: page.id };
  await writeEncrypted(store);
  return { countryCode, page: { id: page.id, name: page.name } };
}
