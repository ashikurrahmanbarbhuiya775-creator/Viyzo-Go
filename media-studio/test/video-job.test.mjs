import test from "node:test";
import assert from "node:assert/strict";
import { createVideoJob, completeStage, approveJob } from "../src/domain/video-job.mjs";

const valid = { prompt: "Create a factual video about Japan", countryCode: "JP", language: "en", durationSeconds: 300, presenter: "fictional" };

test("creates a valid draft job", () => {
  const result = createVideoJob(valid, "2026-11-05T00:00:00.000Z", "job-1");
  assert.equal(result.ok, true);
  assert.equal(result.job.status, "draft");
  assert.equal(result.job.publishState, "locked");
});

test("rejects invalid country and duration", () => {
  const result = createVideoJob({ ...valid, countryCode: "Japan", durationSeconds: 10 });
  assert.equal(result.ok, false);
  assert.equal(result.errors.length, 2);
});

test("requires stages in order", () => {
  const job = createVideoJob(valid).job;
  const result = completeStage(job, "auto_edit");
  assert.equal(result.ok, false);
  assert.equal(result.expected, "research");
});

test("requires quality check before owner approval", () => {
  const job = createVideoJob(valid).job;
  const result = approveJob(job, "owner-1");
  assert.equal(result.ok, false);
  assert.equal(result.error, "quality_check_incomplete");
});

test("publishing remains gated after approval", () => {
  let job = createVideoJob(valid).job;
  for (const stage of ["research","script","voice","render","auto_edit","metadata","thumbnail","quality_check"]) {
    const result = completeStage(job, stage);
    assert.equal(result.ok, true);
    job = result.job;
  }
  const approved = approveJob(job, "owner-1");
  assert.equal(approved.ok, true);
  assert.equal(approved.job.ownerApproved, true);
  assert.equal(approved.job.publishState, "ready_for_explicit_publish_action");
});
