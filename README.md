# article-recommender-android

Android client for the Discover-like article recommendation system.
Design: `~/workspace/discover-recommender/design.md` (§2 UI, §3 gestures,
§6 source pages, §7 history, §10 architecture).

The app is a thin client: it renders, records events, and caches. All
ingestion, embedding, clustering and ranking live in the Python backend.
Until the backend exists, the app runs against an in-process fake
backend with mock data; the backend base URL (a tailnet address in
production) can be set from the Sources tab (long-press) with no other
code change.

## Milestones

- **M1 (current, v0.1.0)**: project skeleton, API boundary
  (`BackendApi` / `FakeBackend` / `HttpBackend` aligned with the real
  backend's FastAPI contract), four tabs, YT Client-style rows,
  two-stage swipe both directions with stage labels/colours, haptics
  at the stage boundary and undo, long-press source sheet, source
  page, history as a correction screen (superseding events),
  on-device event queue with batch flush, offline cache of feed/inbox,
  in-app WebView reader measuring dwell/completion (logged, not model
  signals), and a settings screen (exploration share §5b, backend URL,
  version). Thumbnails come from the backend's og:image
  (`thumbnail_url`).
- M2: seen/impression dwell rules (§4), reader polish.
- M3: real backend integration on the tailnet host (known contract
  gaps are listed in HttpBackend.kt: no unblock endpoint, no
  /settings endpoint, no undo path).

## Build

Gradle builds run on GitHub Actions only (the Muse VM cannot run
Gradle). CI builds `discover.apk` (fixed name, pinned signing key from
repo secrets) and gates on an API 35 emulator launch test.
