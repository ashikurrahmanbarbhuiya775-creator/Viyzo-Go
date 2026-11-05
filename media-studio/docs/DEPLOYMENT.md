# Viyzo AI Media Studio — private backend deployment

This deployment package runs the existing Node.js API and FFmpeg processor in one container. It is intentionally separate from Viyzo Worker and does not use the Worker Supabase project.

## Requirements

- A host that supports Docker Compose and a persistent disk/volume.
- Public HTTPS in front of the service before using it outside a trusted local network.
- A private owner password of at least 12 characters.
- Optional provider credentials (OpenAI-compatible text API and HeyGen). Both integrations remain OFF until their credentials are configured. Provider usage can be billed.

## First-time setup

1. Copy `.env.production.example` to `.env`.
2. Generate the owner password hash on the deployment host using a password you choose. Keep the password private; do not send it in chat or commit it.
   ```sh
   printf '%s' 'YOUR_LONG_PRIVATE_PASSWORD' | docker compose run --rm --no-deps -T media-studio node scripts/hash-owner-password.mjs
   ```
3. Replace `OWNER_PASSWORD_HASH` in `.env` with the full `scrypt$...` output. Leave `OWNER_ACCESS_TOKEN` blank unless you deliberately configure a separate 32+ character token.
4. Keep all AI provider values blank until you have reviewed provider pricing and intentionally chosen a budget.
5. Build and start:
   ```sh
   docker compose up -d --build
   docker compose ps
   docker compose logs --tail=100 media-studio
   ```
6. Verify `http://HOST:8787/health` from the host. Do not expose port 8787 directly to the public internet. Put a TLS reverse proxy (Caddy, Nginx, or the host's HTTPS proxy) in front and forward to port 8787.
7. Open the HTTPS URL, sign in with the owner password, and check the engine status page.

## Persistence and backup

- Job metadata and media are stored in the named Docker volume `media_data` mounted at `/data`.
- Back up that volume regularly. A container restart does not delete it; deleting the volume does.
- This single-container setup is for an early private owner instance, not a horizontally scaled multi-instance service.
- Do not deploy on ephemeral storage: uploaded videos and job records would be lost when the host replaces the container.

## Provider setup

### Script and metadata generation
Set `OPENAI_BASE_URL`, `OPENAI_API_KEY`, and `OPENAI_MODEL` in the host's secret/environment settings. Restart the service. The engine status changes to configured; the first real generation call can still fail due to provider account, model access, billing, or network settings.

### AI Presenter
Set `HEYGEN_API_KEY` and `HEYGEN_AVATAR_ID` (and optionally a compatible `HEYGEN_VOICE_ID`) in the host's secret/environment settings. Review HeyGen's current plan, API access, avatar rights, and usage price first. Restart the service. A configured status is not proof that provider generation will succeed; test one short render and review the charge before regular use.

## Important limits before public launch

- This is a private single-owner beta deployment. Add HTTPS, host firewall, encrypted backups, monitoring, log retention, and a tested restore procedure.
- The current in-memory session store logs users out after a process restart and does not support multiple replicas.
- No Meta/Facebook OAuth app, Page authorization, or publishing credentials are provisioned by this package.
- The app does not create provider accounts or API keys. Never commit `.env` or paste secret values into source code.
- Do not enable public registration. Keep the owner approval gate before publishing.
