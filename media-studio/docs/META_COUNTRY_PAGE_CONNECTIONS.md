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

## Server-side configuration still required

- `META_APP_ID`
- `META_APP_SECRET`
- `META_OAUTH_REDIRECT_URI`
- A persistent encrypted token store and encryption key managed outside source control.
- OAuth state/CSRF validation, short-lived authorization-code exchange, token expiry/revocation handling, and a disconnect/revoke action.
- Country-to-Page mapping with owner confirmation and audit history.

Do not put Page access tokens in a single global variable: each country may map to a different Page and token. Do not enable publishing until OAuth, token encryption, Page selection, App Review requirements, persistent storage, and owner approval have been implemented and tested.

## Official starting points

- Meta developer portal: https://developers.meta.com/
- Meta Facebook Login: https://developers.facebook.com/docs/facebook-login/
- Meta Pages API: https://developers.facebook.com/docs/pages-api/
- Meta Graph API Page videos: https://developers.facebook.com/docs/graph-api/reference/page/videos/

## Current implementation status

The Media Hub UI now displays a flag beside each country and explains the per-country Facebook Page connection flow. This document is the integration specification. OAuth, secure token persistence, Page discovery/selection, and live publishing are not yet implemented or verified.
