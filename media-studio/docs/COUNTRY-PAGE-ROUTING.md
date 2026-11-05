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
