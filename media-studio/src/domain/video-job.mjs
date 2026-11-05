export const PIPELINE_STAGES = Object.freeze([
  "research", "script", "voice", "render", "auto_edit", "metadata",
  "thumbnail", "quality_check", "owner_review"
]);

const COUNTRIES = /^[A-Z]{2}$/;
const LANGUAGES = /^[a-z]{2,3}(?:-[A-Z]{2})?$/;

export function validateJobInput(input = {}) {
  const errors = [];
  const prompt = typeof input.prompt === "string" ? input.prompt.trim() : "";
  const countryCode = typeof input.countryCode === "string" ? input.countryCode.trim().toUpperCase() : "";
  const language = typeof input.language === "string" ? input.language.trim() : "";
  const durationSeconds = Number(input.durationSeconds ?? 300);
  const presenter = input.presenter ?? "fictional";

  if (prompt.length < 5 || prompt.length > 2000) errors.push("prompt must be 5–2000 characters");
  if (!COUNTRIES.test(countryCode)) errors.push("countryCode must be an ISO-style 2-letter code");
  if (!LANGUAGES.test(language)) errors.push("language must be a language tag such as en or bn-IN");
  if (!Number.isInteger(durationSeconds) || durationSeconds < 30 || durationSeconds > 900) errors.push("durationSeconds must be 30–900");
  if (!["fictional", "none"].includes(presenter)) errors.push("presenter must be fictional or none");

  return {
    ok: errors.length === 0,
    errors,
    value: { prompt, countryCode, language, durationSeconds, presenter }
  };
}

export function createVideoJob(input, now = new Date().toISOString(), id = crypto.randomUUID()) {
  const result = validateJobInput(input);
  if (!result.ok) return { ok: false, errors: result.errors };
  const job = {
    id,
    ...result.value,
    status: "draft",
    completedStages: [],
    ownerApproved: false,
    publishState: "locked",
    createdAt: now,
    updatedAt: now
  };
  return { ok: true, job };
}

export function completeStage(job, stage, now = new Date().toISOString()) {
  if (!PIPELINE_STAGES.includes(stage)) return { ok: false, error: "unknown_stage" };
  if (stage === "owner_review") return { ok: false, error: "owner_review_requires_approval" };
  const expected = PIPELINE_STAGES[job.completedStages.length];
  if (stage !== expected) return { ok: false, error: "stage_out_of_order", expected };
  const updated = {
    ...job,
    completedStages: [...job.completedStages, stage],
    status: stage === "quality_check" ? "awaiting_owner_review" : stage,
    updatedAt: now
  };
  return { ok: true, job: updated };
}

export function approveJob(job, ownerId, now = new Date().toISOString()) {
  if (!ownerId) return { ok: false, error: "owner_identity_required" };
  if (!job.completedStages.includes("quality_check")) return { ok: false, error: "quality_check_incomplete" };
  return {
    ok: true,
    job: {
      ...job,
      ownerApproved: true,
      status: "approved",
      publishState: "ready_for_explicit_publish_action",
      approvedBy: ownerId,
      approvedAt: now,
      updatedAt: now
    }
  };
}
