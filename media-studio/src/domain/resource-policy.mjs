export function isPaidAiAllowed(env = process.env) {
  return String(env.VIYZO_ALLOW_PAID_AI || "").toLowerCase() === "true";
}

export function getResourcePolicy(env = process.env) {
  const enabled = isPaidAiAllowed(env);
  return {
    paidAiEnabled: enabled,
    defaultMode: "cost_guarded",
    remoteAiCalls: enabled ? "owner_enabled_may_incur_provider_charges" : "blocked_until_owner_opt_in",
    localVideoProcessing: "use_local_ffmpeg_when_available",
    reusePolicy: "reuse_existing_research_assets_and_metadata_when_still_fresh",
    note: enabled
      ? "Remote AI calls are enabled by the owner and may incur usage charges. Provider billing limits must also be configured with the provider."
      : "Remote AI calls are blocked by default. Local FFmpeg processing can still run when installed; no paid provider call is made."
  };
}

export function buildGrowthRecommendations(metrics = {}) {
  const number = key => Number.isFinite(Number(metrics[key])) && Number(metrics[key]) >= 0 ? Number(metrics[key]) : null;
  const reach = number("reach");
  const views = number("views");
  const avgWatchSeconds = number("averageWatchSeconds");
  const videoSeconds = number("videoSeconds");
  const shares = number("shares");
  const follows = number("follows");
  const retention = avgWatchSeconds !== null && videoSeconds > 0 ? Math.min(100, Math.round(avgWatchSeconds / videoSeconds * 100)) : null;
  const recommendations = [];
  if (reach === null || views === null) {
    recommendations.push({ priority: "high", action: "Connect official Meta Page Insights before judging reach; do not invent audience numbers." });
  }
  if (retention !== null && retention < 30) recommendations.push({ priority: "high", action: "Test a clearer first 3–5 second hook and remove slow opening material; compare retention on the next videos." });
  if (shares !== null && views > 0 && shares / views < 0.01) recommendations.push({ priority: "medium", action: "Test a more useful, locally relevant explainer that viewers may genuinely want to share." });
  if (follows !== null && views > 0 && follows / views < 0.005) recommendations.push({ priority: "medium", action: "Add a truthful follow invitation and a clearly named country-specific series so viewers know what to expect." });
  if (recommendations.length === 0) recommendations.push({ priority: "medium", action: "Run a small title or thumbnail experiment, changing one variable at a time and comparing verified Page Insights." });
  return {
    countryCode: typeof metrics.countryCode === "string" ? metrics.countryCode.toUpperCase() : null,
    measured: { reach, views, averageWatchSeconds: avgWatchSeconds, videoSeconds, retentionPercent: retention, shares, follows },
    recommendations,
    source: "owner_supplied_or_official_meta_insights_only",
    guarantee: "No reach or follower outcome is guaranteed."
  };
}
