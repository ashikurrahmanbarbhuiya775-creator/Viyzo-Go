# Security checklist

## Implemented in the local development scaffold
- API is bound to 127.0.0.1 by default.
- All job routes require a bearer token with a minimum length of 32 characters.
- Token comparison uses a constant-time comparison for equal-length values.
- Request bodies are limited to 32 KiB.
- JSON responses disable caching and set nosniff / restrictive CSP headers.
- Local job data is written with restrictive file permissions and atomic rename.
- No public signup, upload, OAuth or publish endpoint is implemented.

## Not production security
The static bearer token is only a local-development gate, not user authentication. Do not deploy this API publicly or expose the token in browser code. Production must use verified owner sessions, server-side authorization, rate limiting, CSRF/origin protection where applicable, audit trails, secret rotation, backups, malware scanning and private object storage.

Never commit .env, API keys, OAuth tokens, private media, or production database credentials.
