# Media Studio architecture (phase 1)

## Isolation
All files in this folder are independent of the Android app and Viyzo Worker web project. Work is being developed on the `media-studio-isolated` branch. Do not merge into the default branch until reviewed.

## Current backend
- Node.js 20+ built-in HTTP server; no third-party packages.
- Local JSON persistence for development only.
- Owner-only API protected by a bearer secret (minimum 32 characters).
- Request body limit, constant-time token comparison, no-store responses and atomic local file replacement.
- Domain validation for country code, language, duration and presenter choice.
- Ordered pipeline stage progression and explicit owner approval gate.
- No upload or publish endpoint exists yet.

## API
- `GET /health` — basic health check (no sensitive information).
- `GET /api/jobs` — owner-authenticated job list.
- `POST /api/jobs` — create a draft. Body: `prompt, countryCode, language, durationSeconds, presenter`.
- `POST /api/jobs/:id/stage` — complete the next stage in order. Body: `{ "stage": "research" }`.
- `POST /api/jobs/:id/approve` — owner approval only after quality_check.

Send `Authorization: Bearer <OWNER_ACCESS_TOKEN>` on all API routes except health.

## Local run
1. Install Node.js 20 or newer.
2. Copy `.env.example` to `.env`, choose a long random owner token, and export the values in your shell. The server does not load .env files automatically.
3. Run `npm test` and `npm start` from this folder.
4. Bind to localhost for local testing. Do not expose this server to the public internet.

## Production blockers (must be solved before launch)
- Replace JSON storage with a separate Media Studio database and migrations.
- Add proper owner identity through an authentication provider; never rely on a shared static token for a public deployment.
- Add CSRF/origin policy if cookie sessions are used, rate limits, audit log, backup/restore, observability and malware/content scanning.
- Add secure private object storage, resumable uploads, signed URLs and server-side media probing.
- Integrate real research, LLM, voice, video rendering, editing and thumbnail providers with per-provider budgets and secret management.
- Validate current-affairs claims against trustworthy sources and preserve source records.
- Add platform-specific OAuth and permissions. Publishing must remain a separate explicit owner action.
- Never use the Viyzo Worker Supabase project or its credentials for this product.

This is a development scaffold, not a production-ready or deployed service.
