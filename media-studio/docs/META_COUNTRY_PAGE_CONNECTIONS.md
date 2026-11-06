# Facebook Page connections by country

## Intended Viyzo workflow

1. Owner selects a country (the country selector displays its ISO 3166-1 alpha-2 flag).
2. Owner connects a Facebook account through Meta Facebook Login.
3. Viyzo requests the user's managed Pages and displays the Page name, Page ID, and granted Page tasks.
4. Owner explicitly selects and confirms the Page for that country. Never infer the destination from the user's personal profile or silently assign a Page.
5. Viyzo stores the Page ID and Page access token encrypted server-side, mapped to the country code. Never store tokens in browser localStorage, APK resources, logs, or Git.
6. Before publishing, Viyzo checks the final video, metadata, destination Page, token validity and permissions. The owner must approve the exact Page and video.
7. Publish through Meta's Pages API video endpoint and record the returned video ID and status. Retry only when the operation is known to be safe; do not create duplicate posts.

## Meta prerequisites

- Create a Meta app in Meta for Developers and add Facebook Login.
- Configure the exact HTTPS OAuth redirect URI on the app and in the Viyzo server.
- Keep the Meta App Secret on the server only.
- Request only required permissions. Typical Page discovery/publishing flow uses `pages_show_list`, `pages_read_engagement`, and `pages_manage_posts`; video publishing requires the appropriate Page task (such as `CREATE_CONTENT`) and current Meta video publishing access. Confirm current requirements in Meta's docs and request App Review / Advanced Access where required.
- Use a user access token to discover Pages the user manages (commonly `GET /me/accounts`), then retain the Page ID and Page access token returned for the explicitly selected Page.
- Page publishing is for Facebook Pages, not personal profiles. A person must have sufficient Page tasks/permissions.
- Do not assume every country can publish without regional, account, Page, or Meta policy restrictions. Surface Meta API errors to the owner.

## Page creation limitation

Meta documents Page creation through the User Accounts / Pages API, but creation is restricted to test users or apps that Meta has explicitly allowlisted. Viyzo therefore keeps automatic Page creation OFF by default. The Settings button only becomes active when the owner configures `META_PAGE_CREATION_ENABLED=true` after Meta confirms allowlisting and the required permission is granted. It creates one Page for the selected country at a time, then verifies the returned Page token and `CREATE_CONTENT` task before assigning it. If Meta rejects the request, Viyzo must not pretend a Page was created.

## Server-side configuration still required

- `META_APP_ID`
- `META_APP_SECRET`
- `META_OAUTH_REDIRECT_URI`
- `META_TOKEN_ENCRYPTION_KEY` (32-byte AES-256-GCM key, hex-encoded)
- `META_TOKEN_STORE_FILE` on durable private storage (not an ephemeral container filesystem)
- `META_PAGE_CREATION_ENABLED=true` only after Meta allowlists the app for Page creation.
- OAuth state validation, authorization-code exchange, token expiry/revocation handling, and a disconnect/revoke action.
- Meta App Review / Advanced Access where required.

Do not put Page access tokens in browser localStorage, APK resources, logs, or Git. Do not enable publishing until token persistence, Page selection, permissions, owner approval, and actual Meta API calls have been tested.

## Current implementation status

- Country flag selector and country-to-Page mapping controls: implemented in the isolated Media Hub code.
- Meta OAuth callback, encrypted token store adapter, Page discovery, and one-Page-per-country mapping: code added; not live-tested because Meta app credentials and durable storage are not configured.
- Single-country Page creation adapter and Settings button: code added but deliberately disabled unless Meta allowlists the app and the owner explicitly enables the server flag.
- Owner approval gate and separate Facebook video publish action: code added; not live-tested without Meta credentials, an authorized Page token, and a completed video file.
- AI video rendering still depends on configured, cost-approved providers. No real video is claimed as rendered or published yet.
