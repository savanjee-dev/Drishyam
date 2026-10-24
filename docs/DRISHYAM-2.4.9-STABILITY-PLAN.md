# Drishyam 2.4.9 — Stability and Reliability Plan

Status: investigation in progress; not a release checklist sign-off.

## Scope lock
- Keep the current 2.4.8 visual design, navigation, typography, colours, and screen layout unchanged.
- Work only on the dedicated `stability/drishyam-2.4.9` branch until changes are reviewed.
- Do not publish an APK or create a release tag until the app changes and verification are complete.
- Preserve application ID `com.savan.drishyam`, release signing configuration, and user data.

## Audit areas
1. Photo feed and proxy: response validation, HTTP errors, pagination, duplicate suppression, retry behaviour, timeouts, and late responses after a category/sort change.
2. Image rendering: thumbnail-to-full transition, failed image placeholders, memory/network caching, and lifecycle-safe requests.
3. Saved items: favourites and bookmarks survive process restart; malformed stored entries do not crash the app; toggle state remains consistent across feed and viewer.
4. Downloads and wallpaper: prevent duplicate concurrent actions; handle network failure, decode failure, MediaStore write failure, and wallpaper system errors; clean up partial output.
5. Update flow: release metadata errors, invalid/missing APK URL, version comparison, and safe handoff to Android's package installer.
6. Proxy: only expected routes and HTTP methods; input bounds; upstream 429/5xx mapping; cache correctness; no access key in logs or client code.
7. Build/release: compile release APK, verify APK signature, confirm package/version metadata, ensure signing certificate matches the installed release lineage, and keep the release unpublished until verified.

## Acceptance checklist
- [ ] Release build completes successfully.
- [ ] APK signature verification succeeds.
- [ ] Application ID remains `com.savan.drishyam`.
- [ ] Version name/code are increased from 2.4.8 / 19.
- [ ] Signing certificate is confirmed compatible with the currently installed app before asking the user to install.
- [ ] Explore feed, categories, search, sort modes (including Top), refresh, and pagination are exercised.
- [ ] Failed/slow network and proxy rate-limit/server errors produce recoverable behaviour.
- [ ] Likes and bookmarks persist after app restart.
- [ ] Photo download, share, original-source link, fullscreen, and wallpaper actions are exercised.
- [ ] Update-check behaviour is exercised with both available and unavailable release metadata.
- [ ] No unintended UI/design changes are included.
- [ ] Changelog reflects only changes actually implemented and tested.

## Initial code-review observations
- The feed has per-source generation counters intended to ignore stale responses after resets; verify all asynchronous completion paths check the generation before mutating visible state.
- The API helper uses blocking `HttpURLConnection` with finite connect/read timeouts; review response-size handling, connection cleanup, and error-body handling.
- Favourites are held in a process-level cache after first read while bookmarks are read from preferences on demand; test persistence and state consistency through all entry points.
- Viewer download/wallpaper actions run work on background threads; verify activity lifecycle safety and cleanup of partial MediaStore entries on failures.
- The worker's health endpoint only proves the worker is responding; it does not prove the Unsplash credential is configured or that search/download routes are healthy.

These are review targets, not claims that a defect has already been reproduced. No app source or release version has been changed by this planning document.
