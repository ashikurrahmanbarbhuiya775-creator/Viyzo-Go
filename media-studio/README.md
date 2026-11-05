# Viyzo AI Media Studio

This is an isolated workspace under `media-studio/` on the `media-studio-isolated` branch. It does not modify the Android app source, Gradle configuration, Viyzo Worker web project, or its backend.

## What is included
- Private-workspace dashboard demo (`index.html`)
- Country/language/duration prompt form and in-browser project list
- Post-production workflow preview: research, script, voice, render, auto-edit, metadata, thumbnail, quality check, owner review
- Supervisor panel showing all provider engines as disconnected
- Node.js API with salted-scrypt owner password login, expiring HttpOnly sessions, local JSON persistence and same-origin dashboard hosting
- Private local media vault with restrictive directory/file permissions (not cloud storage)
- Explicit owner-triggered script and metadata/thumbnail-brief generation via an optional OpenAI-compatible text provider\n- Owner-authenticated MP4/WebM/MOV upload, FFprobe validation, FFmpeg 1080p auto-edit and JPEG thumbnail extraction
- Video-job validation, ordered stage transitions and explicit owner-approval gate
- Tests and architecture/security notes

## Frontend demo
Run the backend as described below, then open `http://127.0.0.1:8787/`. The dashboard is served by the same-origin local API and can create/list jobs. You can also open `index.html` as a visual-only demo. Use the Library actions to explicitly request script or metadata drafts; each request may incur provider charges once credentials are configured.

## Backend local setup
Requires Node.js 20+ and FFmpeg/FFprobe installed on the server.

1. From this folder, copy `.env.example` to `.env`.
2. Generate a password hash by piping your long owner password into scripts/hash-owner-password.mjs.
3. Put the returned value in OWNER_PASSWORD_HASH; set OWNER_ACCESS_TOKEN only if local bearer access is also needed.
4. Export the variables in your shell (the server intentionally does not load .env automatically).
5. Run npm test, then npm start.

The API binds to localhost by default. Sign in from the Supervisor panel using the owner password. The session is HttpOnly, SameSite=Strict and expires after 12 hours. Create a job and explicitly click Generate script to request a provider call. See `docs/ARCHITECTURE.md` for routes and `docs/SECURITY.md` for limitations. GitHub Actions runs Node checks and API tests on this isolated branch.

## Important limitations
An optional OpenAI-compatible text adapter for script and metadata drafts is implemented but remains OFF until server-side `OPENAI_API_KEY` and `OPENAI_MODEL` are configured. Provider use may incur charges. No actual text provider has been connected or tested. Voice generation, AI presenter/video generation, cloud storage, production database, OAuth integration, or social publishing is connected. Uploaded and processed media remains on local disk with restrictive file permissions; this is not cloud backup or disk encryption. FFmpeg performs deterministic local formatting/normalization and frame extraction; it is not generative AI and does not remove silences or add AI scenes. No video is generated or uploaded. No paid service or cloud resource is created. Publishing remains unavailable until proper provider integrations, authorization, and a separate explicit owner action are implemented.

## Isolation and change control
All work is on `media-studio-isolated`. Do not merge to `main` until the owner reviews the isolated folder and confirms. Never reuse the Viyzo Worker Supabase project or credentials for this product.
