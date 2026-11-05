import { randomBytes, scryptSync, timingSafeEqual } from "node:crypto";

const KEYLEN = 64;
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;

export function hashOwnerPassword(password, salt = randomBytes(16).toString("hex")) {
  if (typeof password !== "string" || password.length < 12 || password.length > 1024) {
    throw new Error("owner_password_must_be_12_to_1024_characters");
  }
  const hash = scryptSync(password, salt, KEYLEN).toString("hex");
  return "scrypt$" + salt + "$" + hash;
}

export function createOwnerAuth(passwordHash) {
  const parts = String(passwordHash || "").split("$");
  const validConfig = parts.length === 3 && parts[0] === "scrypt" &&
    /^[a-f0-9]{32}$/i.test(parts[1]) && /^[a-f0-9]{128}$/i.test(parts[2]);
  const sessions = new Map();

  function verifyPassword(password) {
    if (!validConfig || typeof password !== "string" || password.length > 1024) return false;
    const candidate = scryptSync(password, parts[1], KEYLEN);
    const expected = Buffer.from(parts[2], "hex");
    return candidate.length === expected.length && timingSafeEqual(candidate, expected);
  }

  function createSession() {
    const token = randomBytes(32).toString("base64url");
    sessions.set(token, Date.now() + SESSION_TTL_MS);
    return token;
  }

  function isSessionValid(token) {
    if (typeof token !== "string" || token.length < 40 || token.length > 100) return false;
    const expiry = sessions.get(token);
    if (!expiry) return false;
    if (expiry <= Date.now()) {
      sessions.delete(token);
      return false;
    }
    return true;
  }

  function revokeSession(token) {
    if (token) sessions.delete(token);
  }

  function sweepExpired() {
    const now = Date.now();
    for (const [token, expiry] of sessions) if (expiry <= now) sessions.delete(token);
  }

  return Object.freeze({ configured: validConfig, verifyPassword, createSession, isSessionValid, revokeSession, sweepExpired });
}

export const OWNER_SESSION_TTL_SECONDS = SESSION_TTL_MS / 1000;
