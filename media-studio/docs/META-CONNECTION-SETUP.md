# Meta / Facebook Pages connection — production checklist

The server includes a Meta OAuth, encrypted token-store, Page discovery/mapping and owner-approved video publishing adapter. It is NOT configured in the current Railway deployment: no Meta app credentials, encryption key, durable token volume or Page authorization are present. Do not enable publishing until all prerequisites exist.

## Required owner actions

1. Create or select a Meta for Developers app owned by the business that will operate Viyzo Media Hub.
2. Add the Facebook Login / Facebook Login for Business product and the Pages API use case.
3. Set the exact HTTPS OAuth redirect URI to `https://YOUR-HOST/auth/meta/callback` (replace with the deployed Media Hub host).
4. Add the app ID and app secret only to the deployment host's secret manager/environment. Never put them in Android resources, browser JavaScript, Git, screenshots, or chat.
5. Log in through the deployed Media Hub's official Meta OAuth button and grant only the requested Page permissions.
6. Select one Page per country from the Pages returned by Meta. A country code is not a Page ID; the owner must confirm the country-to-Page mapping.
7. Complete Meta App Review / Advanced Access where required before connecting Pages for people outside app roles.
8. Test with a non-public/draft workflow first. Enable publishing only after a human owner approves each finished video.

## Server-side requirements before OAuth can be enabled

- HTTPS only; exact redirect URI allow-list.
- Cryptographically random, single-use OAuth state, bound to the owner session, short expiry, and constant-time verification.
- Server-side authorization-code exchange; the app secret must never reach the browser or Android app.
- Request least-privilege scopes. Start with `pages_show_list`, `pages_read_engagement`, and `pages_manage_posts`; request Insights access only if needed and approved. Verify current Meta requirements in the app dashboard.
- Fetch Pages through the authorized user's `/me/accounts` edge and check the user's Page tasks include `CREATE_CONTENT` before allowing a publishing destination.
- Store Page IDs, country mappings, and access tokens only in encrypted server-side storage. Never return tokens to the browser, expose them in logs, or put them in the jobs JSON file.
- Add token validation/reconnection, revocation, audit logs, rate limits, and deletion on disconnect.
- Publishing must be a separate owner-approved action, with an idempotency key and stored Meta post/video ID. No automatic public publishing.
- Keep the feature disabled until all checks pass.

## Environment variables (names reserved; values intentionally absent)

```text
META_APP_ID=
META_APP_SECRET=
META_GRAPH_API_VERSION=
META_OAUTH_REDIRECT_URI=https://YOUR-HOST/auth/meta/callback
META_TOKEN_ENCRYPTION_KEY=
```

Do not deploy with blank placeholders. Use a secret manager and generate a dedicated encryption key. Never reuse the owner password or another service's key.

## One Page per country

Use a reviewed ISO country-code to real Meta Page ID mapping. Do not assume one Page exists for every country. The owner must choose among Pages returned by Meta and confirm each mapping. If a country has no mapped Page, publishing remains unavailable for that country.

## Current repository status

Meta OAuth, encrypted token persistence, Page discovery, country mapping and publishing code are implemented, but remain **not configured or live-tested** in the current deployment. Page Insights integration is not implemented. Page creation remains disabled unless Meta explicitly allowlists the app. This document is a setup/security checklist, not a claim that a connection is active.

## Official documentation

- https://developers.facebook.com/docs/facebook-login/
- https://developers.facebook.com/docs/pages-api/
- https://developers.facebook.com/docs/permissions/
