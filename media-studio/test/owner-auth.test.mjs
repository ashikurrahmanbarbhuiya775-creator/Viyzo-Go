import test from "node:test";
import assert from "node:assert/strict";
import { hashOwnerPassword, createOwnerAuth, OWNER_SESSION_TTL_SECONDS } from "../src/security/owner-auth.mjs";

test("owner password hashes are salted and verify without storing plaintext", () => {
  const password = "a-long-unique-owner-password";
  const first = hashOwnerPassword(password);
  const second = hashOwnerPassword(password);
  assert.notEqual(first, second);
  assert.match(first, /^scrypt\$[a-f0-9]{32}\$[a-f0-9]{128}$/);
  const auth = createOwnerAuth(first);
  assert.equal(auth.configured, true);
  assert.equal(auth.verifyPassword(password), true);
  assert.equal(auth.verifyPassword("wrong-password"), false);
  assert.equal(auth.verifyPassword(""), false);
});

test("owner sessions are random, valid and revocable", () => {
  const auth = createOwnerAuth(hashOwnerPassword("another-long-owner-password"));
  const a = auth.createSession();
  const b = auth.createSession();
  assert.notEqual(a, b);
  assert.equal(auth.isSessionValid(a), true);
  auth.revokeSession(a);
  assert.equal(auth.isSessionValid(a), false);
  assert.equal(auth.isSessionValid(b), true);
  assert.equal(OWNER_SESSION_TTL_SECONDS, 43200);
});

test("invalid or missing password hash never enables login", () => {
  assert.equal(createOwnerAuth("").configured, false);
  assert.equal(createOwnerAuth("not-a-scrypt-hash").verifyPassword("anything"), false);
  assert.throws(() => hashOwnerPassword("short"));
});
