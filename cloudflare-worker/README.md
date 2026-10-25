# Drishyam Cloudflare Worker proxy (draft)

This source is added for review only. It has **not** been deployed, and no Cloudflare secret has been set.

## Routes
- `GET /health`
- `GET /search/photos?query=...&orientation=portrait&per_page=20&page=1` — returns Unsplash's native search response for compatibility with Android's current parser.
- `GET /photos/{photo_id}/download` — triggers Unsplash download tracking through a fixed route, never an arbitrary URL.

## Required before enabling
1. Deploy this Worker in your Cloudflare account.
2. Add `UNSPLASH_ACCESS_KEY` using the Worker **Secret** setting. Never put it in source or Android BuildConfig.
3. Replace `REPLACE-WITH-YOUR-WORKER.workers.dev` in `Data.kt` with the actual Worker origin.
4. Test health, searches, pagination, attribution, download tracking, errors, and rate limits.
5. Inspect the built APK to confirm no Unsplash key is embedded.

The endpoint is public; CORS is not authentication. Add Cloudflare rate limiting/WAF before public launch. Do not deploy until the secret is configured and the endpoint is tested.
