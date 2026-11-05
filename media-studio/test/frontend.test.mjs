import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

test("dashboard inline JavaScript parses and exposes engine actions", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const match = html.match(/<script>([\\s\\S]*?)<\\/script>/i);
  assert.ok(match, "dashboard script exists");
  const dir = await mkdtemp(join(tmpdir(), "viyzo-ui-check-"));
  const file = join(dir, "dashboard.mjs");
  try {
    await writeFile(file, match[1]);
    const result = spawnSync(process.execPath, ["--check", file], { encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr || result.stdout);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
  assert.match(html, /data-script=/);
  assert.match(html, /data-metadata=/);
  assert.match(html, /generate-metadata/);
});
