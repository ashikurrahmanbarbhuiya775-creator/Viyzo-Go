# Media Studio architecture (phase 1)

## Isolation
All files in this folder are independent of the Android app and Viyzo Worker web project. Work is being developed on the `media-studio-isolated` branch. Do not merge into the default branch until reviewed.

## Dashboard experience
- Grouped desktop navigation and a compact phone bottom bar with a More drawer for secondary tools.
- Video library project cards separate script/package, presenter and source/edit/quality actions into clear groups.
- Device-local settings for default language, duration, output resolution, subtitles, thumbnail briefs and fact-check reminders. Owner approval remains mandatory.

## Current backend
- Node.js 20+ built-in HTTP server; no third-party packages.
- Same-origin dashboard hosting at `/` to avoid cross-origin browser API calls.
- Local JSON persistence for development only.
- Owner password login uses a salted scrypt hash; successful login issues a 12-hour HttpOnly, SameSite=Strict session cookie.
- Cookie-authenticated mutations require an exact same-origin Origin header; local bearer token remains optional for development.
- Request body limit, constant-time token comparison, no-store responses and atomic local file replacement.
- Domain validation for country code, language, duration and presenter choice.
- Ordered pipeline stage progression and explicit owner approval gate.
- Owner-authenticated raw source upload (MP4/WebM/MOV), FFprobe validation and 1 GiB upload limit.
- Private local media vault with mode 0700 directories and mode 0600 media files on POSIX systems; not remote cloud storage.
- FFmpeg auto-edit to 1080p/30fps H.264/AAC with audio loudness normalization, plus 1280×720 JPEG thumbnail extraction.
- Technical quality gate checks the edited file is readable, HD (at least 1280×720), has an audio track, and is within the 15-minute duration limit. Owner approval requires a passed quality report.
- Optional HeyGen avatar provider adapter: fictional presenter, natural speech and lip-synced video generation from script; asynchronous status polling. OFF unless server-side API key and avatar ID are configured.
- Authenticated edited-video and thumbnail retrieval endpoints.
- No social upload or publish endpoint exists yet.

## API
- `GET /health` — basic health check (no sensitive information).
- `GET /api/engines` — owner-authenticated text, local FFmpeg and optional HeyGen avatar engine status.
- `POST /api/jobs/:id/generate-presenter` — submit the job script to HeyGen for a fictional talking presenter video (requires configured key/avatar and a script draft). Returns 202 with provider video ID.
- `GET /api/jobs/:id/presenter-status` — poll asynchronous HeyGen render status.
- `GET /api/jobs` — owner-authenticated job list.
- `POST /api/jobs` — create a draft. Body: `prompt, countryCode, language, durationSeconds, presenter`.
- `PUT /api/jobs/:id/source` — upload raw MP4/WebM/MOV bytes with the matching Content-Type; server probes the media before storing.
- `POST /api/jobs/:id/auto-edit` — run local FFmpeg edit and extract a thumbnail after source upload.
- `POST /api/jobs/:id/post-produce` — one-step FFmpeg edit, thumbnail extraction and technical quality report; output remains in owner review.
- `POST /api/jobs/:id/import-presenter-video` — after HeyGen reports `completed`, securely imports the MP4 into the private media directory, validates it with FFprobe and makes it the source for post-production. Only HTTPS URLs on HeyGen-owned hostnames are accepted; redirects are rejected, and file size is capped.
- `POST /api/jobs/:id/quality-check` — inspect the edited output; owner approval is blocked until the technical report passes.
- `GET /api/jobs/:id/thumbnail` and `/edited-video` — authenticated media retrieval.
- `POST /api/jobs/:id/generate-script` — explicitly request a script draft using the optional server-side OpenAI-compatible adapter. Returns 503 when not configured.
- `POST /api/jobs/:id/generate-metadata` — explicitly request title, description, tags and a thumbnail-image prompt from the same optional text engine.
- `POST /api/jobs/:id/stage` — complete the next stage in order. Body: `{ "stage": "research" }`.
- `POST /api/jobs/:id/approve` — owner approval only after quality_check.

Sign in with POST /auth/login using a password; the server sets the HttpOnly owner session cookie. GET /api/session checks it and POST /auth/logout revokes it. For local command-line testing only, Authorization: Bearer OWNER_ACCESS_TOKEN remains supported.

## Local run
1. Install Node.js 20 or newer and FFmpeg/FFprobe.
2. Generate a password hash by piping a long owner password into scripts/hash-owner-password.mjs, put it in OWNER_PASSWORD_HASH, and export it in your shell. Optionally set OWNER_ACCESS_TOKEN for local CLI calls. The server does not load .env files automatically.
3. Run `npm test` and `npm start` from this folder.
4. Bind to localhost for local testing. Do not expose this server to the public internet.

## Optional text engine adapter
The script adapter supports OpenAI-compatible Chat Completions endpoints. Configure `OPENAI_API_KEY`, `OPENAI_MODEL`, and optionally `OPENAI_BASE_URL` on the server only. This may incur provider charges; check pricing and configure spend limits before use. No provider is configured by default. The browser never receives the provider key. Script and metadata generation are separate manual actions and may incur provider usage charges. Generated script drafts are not fact-checked automatically and must be verified against independent sources.

## Optional AI presenter provider
Set `HEYGEN_API_KEY` and `HEYGEN_AVATAR_ID` on the server only; optionally set `HEYGEN_VOICE_ID` to pair a compatible voice. The owner must first generate and review a script, then explicitly request presenter generation. HeyGen returns an asynchronous render ID; this adapter polls its status and returns the provider video URL. Provider generation may incur charges; no call is made while credentials are absent. Confirm current plan pricing and limits with HeyGen before enabling. Use only fictional or properly consented avatars.

## Production blockers (must be solved before launch)
- Replace JSON storage with a separate Media Studio database and migrations.
- Upgrade the local owner-login foundation to persistent production identity, MFA, password recovery, session storage, login throttling and audit trails before public deployment.
- Add CSRF/origin policy if cookie sessions are used, rate limits, audit log, backup/restore, observability and malware/content scanning.
- Replace local-disk media storage with a separate private object-storage account, resumable uploads and short-lived signed URLs before remote deployment.
- Integrate real research, LLM, additional voice options, video rendering, editing and thumbnail providers with per-provider budgets and secret management.
- Add safe, authenticated download/import of completed provider renders into private storage, webhook signature validation and background queue retries.
- Validate current-affairs claims against trustworthy sources and preserve source records.
- Add platform-specific OAuth and permissions. Publishing must remain a separate explicit owner action.
- Never use the Viyzo Worker Supabase project or its credentials for this product.

This is a development scaffold, not a production-ready or deployed service.

## Owner system readiness diagnostics

Authenticated owners can call `GET /api/system-status` to inspect whether the configured data directory is writable and whether script, presenter, and video engines are configured. The response intentionally does not expose secret values or filesystem paths. Backup remains operator-managed; Facebook publishing is reported as not configured until the official Meta integration is completed.
