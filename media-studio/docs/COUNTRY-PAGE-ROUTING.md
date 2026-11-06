# One Facebook Page per country

Viyzo Media Hub uses a simple country-based destination model: **one connected Facebook Page per country**. The owner does not choose or remember numbered Page slots.

- Each video job stores its destination country ISO code.
- The system resolves that country to the single Page connected for it in the owner's Meta account.
- If no Page is connected for that country, the job remains in the private library and reports `not_connected`.
- The country-to-Page mapping must be created from Pages returned by the official Meta OAuth/API flow. A country code is not itself a Facebook Page ID.
- Meta OAuth, Page discovery/mapping, secure token storage, and actual publishing are not configured yet.
- Automatic creation and upload into the Viyzo private media library is a separate pipeline stage from publishing to Facebook. The current scaffold does not yet provide an end-to-end automatic render-and-library-upload pipeline.
- Public publishing remains locked until technical quality checks pass, the owner approves the finished video, and the owner explicitly confirms publishing.

Content guidance: civic/public-information videos must be factual and attributed. Do not fabricate government achievements, imply government sponsorship, impersonate officials, or present opinion as fact. Positive framing is fine when substantiated and clearly distinguished from commentary.


## Country-focused audience delivery

Every job also records an audience target matching the selected production country: `audienceTarget.countryCode` and `policy: country_focused`. This is a routing intent for downstream Meta publishing/advertising integrations, not a claim that an organic Facebook post can be confined to that country. Organic distribution is controlled by Meta and may cross borders. If Meta exposes an eligible Page-level country restriction for the specific Page/post type, use that official feature only after owner configuration; otherwise use Meta's permitted location targeting for paid campaigns where available. Never spoof the device/network location, use VPN/proxies to misrepresent origin, or claim guaranteed country-only organic reach. Keep each country Page mapped to the matching country and show a clear warning if audience controls are unavailable.
