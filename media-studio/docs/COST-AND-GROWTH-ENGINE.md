# Cost-guarded production and audience growth

## Cost protection

- Remote AI calls are **blocked by default**, even if API keys exist.
- To deliberately enable paid provider calls, the owner must set `VIYZO_ALLOW_PAID_AI=true` on the server. Calls to OpenAI-compatible research/script/metadata and HeyGen are then allowed and may incur provider charges.
- This flag is an opt-in gate, **not** a spend cap or billing guarantee. Configure hard spending limits/alerts with each provider before enabling it. Do not enable it until the owner has reviewed provider pricing.
- Prefer local FFmpeg for trimming, encoding, thumbnails and other supported post-production; it uses the host's CPU/disk and is not automatically free.
- Reuse country research, scripts, subtitles and visual assets only while the evidence remains fresh, country-matched, licensed and accurate. Current-affairs research must be refreshed when stale; do not reuse stale news to save money.
- Keep video resolution/bitrate/duration appropriate to the platform and retain only approved deliverables. Add storage quotas and retention cleanup before public hosting.

## Organic audience growth

- Each country's Page gets its own editorial calendar, local language, local topics and local posting-time experiments.
- Learn from official Meta Page Insights only: reach, views, average watch time/retention, shares and follows.
- Change one variable at a time (opening hook, title, thumbnail or length) and compare real results.
- Encourage genuine discussion and useful follow-up series. No fake followers, automated likes/comments, engagement pods, spam or guaranteed-virality claims.
- Organic Facebook distribution cannot be guaranteed to remain inside one country. Country targeting for paid distribution depends on Meta's currently available ad products and requires a budget; it is not a free organic reach switch.

## Current implementation boundary

The server exposes a cost policy and deterministic growth recommendations. Meta OAuth/Insights and Page publishing are still not connected. Recommendations cannot use live analytics until the owner connects an authorized Meta Page and the official insights integration is implemented.
