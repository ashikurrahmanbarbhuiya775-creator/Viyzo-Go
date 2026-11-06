import test from "node:test";
import assert from "node:assert/strict";
import { getMetaStatus, createMetaLoginUrl, createMetaCountryPage, mapMetaPage } from "../src/providers/meta-pages.mjs";

test("Meta integration remains safely disabled without server configuration", () => {
  for (const key of ["META_APP_ID","META_APP_SECRET","META_OAUTH_REDIRECT_URI","META_TOKEN_ENCRYPTION_KEY","META_TOKEN_STORE_FILE","META_PAGE_CREATION_ENABLED"]) delete process.env[key];
  const status = getMetaStatus();
  assert.equal(status.configured, false);
  assert.equal(status.capabilities.automaticPageCreation, false);
  assert.throws(() => createMetaLoginUrl("test-state"), /meta_setup_incomplete/);
});

test("automatic Facebook Page creation requires explicit Meta allowlisting switch", async () => {
  delete process.env.META_PAGE_CREATION_ENABLED;
  const result = await createMetaCountryPage({ countryCode: "IN", countryName: "India" });
  assert.deepEqual(result, { ok: false, error: "meta_page_creation_not_enabled" });
});

test("country mapping rejects non-ISO two-letter country codes", async () => {
  await assert.rejects(mapMetaPage("IND", "123"), /invalid_country_code/);
});
