# Drishyam NexWall proxy

This Worker keeps the NexWall developer key out of the Android APK. It exposes only read-only category and wallpaper listing endpoints and forwards a restricted set of query parameters.

## Required GitHub Actions repository secrets

Add these under **Settings → Secrets and variables → Actions → New repository secret**:

- `CLOUDFLARE_API_TOKEN`: Cloudflare API token with permission to edit Workers scripts for the target account.
- `CLOUDFLARE_ACCOUNT_ID`: Cloudflare account ID.
- `NEXWALL_API_KEY`: a newly regenerated NexWall API key. Do not reuse the key previously pasted into chat.

Then run **Actions → Deploy Drishyam NexWall proxy → Run workflow**. The Worker name is `drishyam-nexwall-proxy`.

The Android client is configured for the existing account's workers.dev subdomain:
`https://drishyam-nexwall-proxy.savansingh8147.workers.dev`.
If Cloudflare shows a different workers.dev hostname after deployment, update `NEXWALL_PROXY_BASE_URL` in `Data.kt` before building the APK.

## Endpoints

- `GET /categories`
- `GET /wallpapers?page=1&per_page=24&type=image&search=nature&sort=newest`

The proxy clamps page size to 24, allows image-only results, forwards only documented query parameters, caches successful listing responses for five minutes, and never accepts arbitrary upstream URLs.
