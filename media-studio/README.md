# Viyzo AI Media Studio

Isolated workspace on branch `media-studio-isolated`. It does not modify the Viyzo Worker Android app, its Supabase project, or its existing workflows.

## Implemented code

- Node.js owner dashboard/API with salted-scrypt password login, HttpOnly session cookies, same-origin checks and login rate limiting.
- Country/language/topic job creation and job-stage validation.
- Optional OpenAI-compatible script and video-metadata generation adapter, including title, description, tags, hashtags, thumbnail headline and a 16:9 thumbnail image prompt.
- Live country research adapter using OpenAI Responses web search, with cited-source validation before script generation.
- Optional HeyGen avatar/video generation adapter and status polling.
- Owner-authenticated video upload, FFprobe validation, FFmpeg 1080p formatting/audio normalization, extracted JPEG frame thumbnail and technical quality checks.
- Visual-asset provenance checks and explicit owner review/approval gates.
- Meta OAuth, encrypted server-side token storage, Facebook Page discovery, country-to-Page mapping, optional allowlisted Page creation, and separate owner-approved video publishing adapter.
- Android WebView wrapper for the hosted dashboard with native speech recognition bridge.
- Automated Node tests, container build checks and Android debug APK workflow.

## Current production deployment status

- Railway service is live at https://media-studio-production-33a2.up.railway.app/.
- Current configured environment contains the owner password hash only; OpenAI, HeyGen and Meta provider credentials are not configured.
- Paid AI calls are blocked by default. Do not enable them until the owner chooses provider accounts and a spending limit.
- The Railway service currently has no persistent volume attached. Job records, uploaded videos and encrypted Meta tokens are therefore not guaranteed to survive container replacement/redeployment. Do not use it for important production media until durable private storage is configured and restore-tested.
- Meta app credentials, encryption key, durable token storage, Page permissions/review and country Page mappings are missing. Facebook connection/publishing is not live.
- The current APK is a debug WebView shell loading the hosted dashboard; it is not a standalone offline video editor or a Play Store release.

## Important video-generation limits

- Script and metadata generation require a configured OpenAI-compatible provider and the cost guard to be explicitly enabled.
- Human-like avatar narration/video requires a configured HeyGen API key and avatar ID, plus explicit paid-call authorization.
- The current FFmpeg processor edits an uploaded source video, normalizes/encodes it and extracts a frame thumbnail. It does not independently invent scenes, generate footage, compose a finished video from a topic, create timed captions, remove silences, or design an AI-generated thumbnail image.
- The metadata adapter creates a thumbnail image prompt and headline; an image-generation provider and image composition/export step are not yet integrated.
- No real end-to-end country/topic-to-published-video test has passed. No public Facebook publishing should be attempted before owner review and a private test.

## Local setup

Requires Node.js 20+ and FFmpeg/FFprobe. Copy `.env.example`, generate an owner password hash with `scripts/hash-owner-password.mjs`, configure environment variables on the server (never commit secrets), then run `npm run check` and `npm start`.

## Isolation and change control

All Media Hub changes remain under `media-studio/` on `media-studio-isolated`. Do not merge into `main` or change the Viyzo Worker backend/workflows without separate explicit approval.
