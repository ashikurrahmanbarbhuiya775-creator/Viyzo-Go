# Viyzo AI Media Studio

This is an isolated workspace under `media-studio/` on the `media-studio-isolated` branch. It does not modify the Android app source, Gradle configuration, Viyzo Worker web project, or its backend.

## What is included
- Private-workspace dashboard demo (`index.html`)
- Country/language/duration prompt form and in-browser project list
- Post-production workflow preview: research, script, voice, render, auto-edit, metadata, thumbnail, quality check, owner review
- Supervisor panel showing all provider engines as disconnected
- Node.js API scaffold with owner-token protection and local JSON persistence
- Video-job validation, ordered stage transitions and explicit owner-approval gate
- Tests and architecture/security notes

## Frontend demo
Open `index.html` in a modern browser or serve this folder with a static HTTP server. The dashboard is a visual demo; it does not yet call the API.

## Backend local setup
Requires Node.js 20+.

1. From this folder, copy `.env.example` to `.env`.
2. Set `OWNER_ACCESS_TOKEN` to a random secret of at least 32 characters.
3. Export the variables in your shell (the server intentionally does not load .env automatically).
4. Run `npm test`, then `npm start`.

The API binds to localhost by default. See `docs/ARCHITECTURE.md` for routes and `docs/SECURITY.md` for limitations.

## Important limitations
No real AI provider, video renderer, audio service, media editor, thumbnail generator, cloud storage, database service, OAuth integration, or social publishing is connected. No video is generated or uploaded. No paid service or cloud resource is created. Publishing remains unavailable until proper provider integrations, authorization, and a separate explicit owner action are implemented.

## Isolation and change control
All work is on `media-studio-isolated`. Do not merge to `main` until the owner reviews the isolated folder and confirms. Never reuse the Viyzo Worker Supabase project or credentials for this product.
