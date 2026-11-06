import test from "node:test";
import assert from "node:assert/strict";
import { formatThumbnailHeadline } from "../src/providers/ffmpeg-pipeline.mjs";

test("thumbnail headline is sanitized, bounded and wrapped for 16:9 overlay", () => {
  const headline = formatThumbnailHeadline("  A professional story about India\nwith a safe headline  ");
  assert.equal(headline, "A professional story about\nIndia with a safe headline");
  assert.ok(headline.length <= 120);
  assert.equal(formatThumbnailHeadline(""), "VIYZO VIDEO");
});

test("thumbnail headline strips control characters and caps long input", () => {
  const headline = formatThumbnailHeadline("Topic\u0000 with\u001f unsafe controls " + "long ".repeat(40));
  assert.ok(!headline.includes("\u0000"));
  assert.ok(!headline.includes("\u001f"));
  assert.ok(headline.length <= 120);
});
