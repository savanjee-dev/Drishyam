# Drishyam Cloudflare Worker proxy

The Worker source and automated tests live in this directory. The live endpoint has been manually smoke-tested by the project owner: `/health` returned the expected JSON, photo search returned results, and page 2 was accepted. The deployed code revision and Cloudflare protection settings have not yet been independently verified.

## Routes
- `GET /health` — health/status response; does not validate Unsplash credentials.
- `GET /search/photos?query=...&page=1&per_page=20` — forwards validated search parameters to Unsplash and returns its native JSON response.
- `GET /photos/{photo_id}/download` — forwards a photo ID to Unsplash's download-tracking endpoint; it does not download the image itself.

## Live smoke-test status (2026-10-24)
- Health: owner-reported pass.
- Search: owner-reported pass with photo results.
- Pagination: owner-reported pass for page 2.
- Download tracking: **unresolved**. A request using a photo ID copied from a search result returned `{"error":"Photo not found.","upstream_status":404}`. Confirm that the deployed Worker matches this source, then investigate the exact ID and upstream response before marking this test passed.
- Cloudflare rate limiting/WAF: not yet verified.
- Android in-place upgrade and data-preservation test: not yet verified.

## Deployment and security checklist
1. Deploy this Worker in your Cloudflare account.
2. Configure `UNSPLASH_ACCESS_KEY` as a Worker secret. Never commit it or put it in Android BuildConfig.
3. Confirm Android points to the intended Worker origin.
4. Verify the deployed Worker source/revision matches this directory.
5. Test health, search, pagination, attribution, download tracking, and safe error handling.
6. Configure and verify appropriate Cloudflare rate limiting/WAF rules before public launch. CORS is not authentication; do not rely on CORS to protect a public endpoint.
7. Inspect the release APK to confirm no Unsplash access key is embedded.
8. Test the candidate APK as an in-place upgrade on a device, preserving existing app data, photos, settings, and Dark Mode.

Do not publish the next Android release until the unresolved download-tracking issue, rate-limit protection, and device upgrade checks have been resolved.
