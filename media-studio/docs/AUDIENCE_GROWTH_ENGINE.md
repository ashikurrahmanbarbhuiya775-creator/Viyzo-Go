# Viyzo Media Hub — Audience Growth Engine

This module is a private owner-controlled workspace for growing an authentic audience for newly created social Pages. It is currently a dashboard scaffold only; it does not connect to Facebook, publish posts, or retrieve Page insights.

## Growth workflow
1. Research country/language-specific topics and retain source links for factual claims.
2. Generate a clear opening hook, title, description, captions and representative thumbnail.
3. Build a consistent posting calendar; treat timing as a recommendation until authorized Page audience data is available.
4. Publish only to a Page explicitly authorized by the owner, through Meta's official APIs.
5. Analyze available metrics such as reach, video views, watch time/retention, shares, comments and follower change.
6. Recommend new topics and formats based on actual results, not invented metrics.

## Trust and safety
- No fake followers, fake views, engagement bots, spam, stealth automation or attempts to evade Meta enforcement.
- Never promise virality or guaranteed follower counts.
- Paid promotion is off by default and requires a separate explicit budget approval.
- Owner review/approval remains mandatory before each publish action.

## Meta integration prerequisites
- A Meta developer app owned/controlled by the business.
- HTTPS deployment and exact OAuth redirect URI.
- Official Page authorization and least-privilege permissions, subject to Meta eligibility/review.
- Server-side encrypted token storage, token expiry/revocation handling, audit log and disconnect control.
- Graph API publishing and Page Insights implementation with permission/error handling and rate-limit/backoff tests.

## Current state
The dashboard page is present, but Meta OAuth, token vault, Page discovery, publishing, scheduling and insights are NOT implemented. Do not represent this scaffold as a live Facebook integration. The app currently runs in local-demo mode with local JSON storage; production deployment, persistent database, private object storage, MFA and monitoring remain blockers. Keep this project isolated from Viyzo Worker and its Supabase project.
