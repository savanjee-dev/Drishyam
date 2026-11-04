# Drishyam Cloudflare Worker proxy

The Worker source and automated tests live in this directory. The project owner has manually smoke-tested the live endpoint: `/health` returned the expected JSON, photo search returned results, and page 2 was accepted. The deployed production revision and Cloudflare protection settings have not been independently verified.

## Routes
- `GET /health` — health/status response; does not validate Unsplash credentials.
- `GET /search/photos?query=...&page=1&per_page=20` — forwards validated search parameters to Unsplash and returns its native JSON response.
- `GET /photos/{photo_id}/download` — forwards a photo ID to Unsplash's download-tracking endpoint; it does not download the image itself.

## Live smoke-test status (2026-10-24)
- Health: owner-reported pass.
- Search: owner-reported pass with photo results.
- Pagination: owner-reported pass for page 2.
- Download tracking: **unresolved**. A request using a photo ID copied from a search result returned `{"error":"Photo not found.","upstream_status":404}`. Verify the deployed Worker revision and investigate the exact ID and upstream response before marking this passed.
- Cloudflare rate limiting/WAF: not yet verified.
- Android in-place upgrade/data preservation: **owner-confirmed pass**. The candidate was installed over the existing app; existing data remained intact, and photos, navigation, Dark Mode, and saved content worked.

## Deployment and security checklist
1. Deploy this Worker in the Cloudflare account.
2. Configure `UNSPLASH_ACCESS_KEY` as a Worker secret. Never commit it or put it in Android BuildConfig.
3. Confirm Android points to the intended Worker origin.
4. Verify the deployed Worker source/revision matches this directory.
5. Test health, search, pagination, attribution, download tracking, and safe error handling.
6. Configure and verify appropriate Cloudflare rate limiting/WAF rules before public launch. CORS is not authentication; do not rely on CORS to protect a public endpoint.
7. Inspect the release APK to confirm no Unsplash access key is embedded.
8. Keep the successful in-place upgrade/data-preservation result recorded; do not ask the owner to repeat it unless the candidate changes materially.

Do not publish the next Android release until the unresolved download-tracking issue and rate-limit protection have been addressed and live failure-handling checks completed.

## Pixabay provider

Pixabay is a secondary fallback after Unsplash and before Wikimedia Commons. Android
never contains the Pixabay API key. Configure the Cloudflare Worker secret:

```sh
npx wrangler secret put PIXABAY_API_KEY
```

Create the R2 bucket named `drishyam-pixabay-images` before deploying (the binding is
in `wrangler.toml`). The Worker stores Pixabay image metadata and downloads image
bytes into R2 on first display, then serves app-facing image URLs from R2. This avoids
permanent hotlinking of Pixabay CDN URLs. Pixabay search responses are cached for at
least 24 hours, as required by its API documentation.

Pixabay attribution is shown in photo credits and each photo links to its Pixabay page.
The app requests portrait photos, safe-search results, and supports Pixabay's
`popular` and `latest` ordering. Pixabay's API limit is 100 requests per 60 seconds;
the Worker cache and 20-photo page size help control request volume.

After setting the secret and bucket, deploy the Worker and verify:
- `/pixabay/search?q=forest&page=1&per_page=20&order=popular`
- returned `previewURL` and `largeImageURL` Worker paths return image bytes
- image objects persist in R2 and subsequent reads do not contact Pixabay
