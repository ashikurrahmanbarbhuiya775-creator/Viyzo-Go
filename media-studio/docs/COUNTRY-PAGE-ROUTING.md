# Country and numbered Page routing

Each video job stores a destination Page slot (1–50) together with the selected country ISO code. The slot is a routing label chosen by the owner, not a real Facebook Page connection or numeric Facebook Page ID.

Workflow:
1. The owner enters a country, language, prompt, and destination Page slot.
2. The job stores the country + Page slot as its intended destination.
3. The owner connects their own Facebook account and authorized Pages through the official Meta OAuth flow (not implemented yet).
4. The owner maps each country/slot pair to a real Page returned by Meta.
5. The system can publish only after the video passes checks, receives owner approval, and the owner explicitly confirms publishing.

Content guidance: prompts should result in factual, clearly attributed civic or public-information videos. Do not fabricate government achievements, imply government sponsorship, impersonate officials, or present opinion as fact. Positive framing is fine when substantiated and clearly distinguished from commentary.

Current limitation: this commit stores the routing intent only. Meta OAuth, Page discovery/mapping, token storage, and actual publishing are not configured. No video is sent to Facebook by this feature.
