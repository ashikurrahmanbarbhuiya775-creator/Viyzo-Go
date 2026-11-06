# Security checklist

## Implemented in the local development scaffold
- API binds to 127.0.0.1 by default.
- Owner password is stored as a salted scrypt hash; plaintext is never written to project files.
- Successful password login creates a random, expiring 12-hour HttpOnly, SameSite=Strict session cookie.
- Session tokens are held server-side in memory and revoked on logout or server restart.
- Cookie-authenticated state changes require an exact same-origin Origin header.
- Owner password login is rate-limited to 8 failed attempts per IP in a 15-minute window.
- Constant-time comparison is used for bearer-token compatibility and password-hash verification.
- Job routes require a valid owner session or an optional 32+ character local bearer token.
- Request bodies are limited to 32 KiB; JSON responses disable caching and set nosniff / restrictive CSP headers.
- Local media directory is mode 0700; uploaded source and generated media files are mode 0600 on POSIX systems.
- Uploads are type-allowlisted, size-limited and probed with FFprobe before being accepted.
- No public signup or external cloud bucket is configured. Meta OAuth, encrypted token storage, Page mapping and owner-approved publishing routes exist in code but remain unavailable until production Meta credentials, durable private storage, permissions and review are configured.

## Configure owner login
1. Generate a salted hash without committing the plaintext password by piping it to scripts/hash-owner-password.mjs.
2. Store the returned scrypt$... string in the server environment as OWNER_PASSWORD_HASH.
3. Remove OWNER_ACCESS_TOKEN unless local command-line bearer access is specifically required.
4. Restart the server and sign in through the dashboard. The login session expires after 12 hours.

Use a unique password of at least 12 characters. Protect the environment file and never send the password or hash in chat or commit it to Git.

## Not production security
This is a private single-owner beta login foundation, not a production identity platform. Sessions are memory-only and are lost on restart; there is no password reset, MFA, account recovery, persistent session store, distributed rate limiting, audit trail, malware scan, automatic backup or multi-device session manager. Do not expose this API to the public internet.

Private media is stored on the server's local disk, not cloud storage. Local file permissions do not encrypt the disk or provide remote backups. Before remote deployment, add a separate private object-storage account, encrypted transport and at-rest encryption, short-lived signed access, resumable uploads, quotas, malware/content scanning, persistent database-backed sessions, MFA, monitoring and recovery.

Never commit .env, API keys, OAuth tokens, private media, password hashes, or production database credentials.