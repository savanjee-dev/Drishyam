import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function context() {
  return { waitUntil() {} };
}

test("health endpoint is public and reports service identity", async () => {
  const response = await worker.fetch(
    new Request("https://worker.example/health"),
    {},
    context()
  );
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    service: "drishyam-photo-proxy",
    status: "ok"
  });
});

test("preflight requests return CORS headers without calling upstream", async () => {
  const response = await worker.fetch(
    new Request("https://worker.example/search/photos", { method: "OPTIONS" }),
    {},
    context()
  );
  assert.equal(response.status, 204);
  assert.equal(response.headers.get("access-control-allow-origin"), "*");
});

test("unsupported methods and paths are rejected", async () => {
  const post = await worker.fetch(
    new Request("https://worker.example/health", { method: "POST" }),
    {},
    context()
  );
  assert.equal(post.status, 405);

  const missing = await worker.fetch(
    new Request("https://worker.example/anything"),
    {},
    context()
  );
  assert.equal(missing.status, 404);
});

test("search validates query and pagination before accessing Unsplash", async () => {
  const empty = await worker.fetch(
    new Request("https://worker.example/search/photos?query=%20"),
    {},
    context()
  );
  assert.equal(empty.status, 400);

  const badPage = await worker.fetch(
    new Request("https://worker.example/search/photos?query=nature&page=0"),
    {},
    context()
  );
  assert.equal(badPage.status, 400);

  const tooMany = await worker.fetch(
    new Request("https://worker.example/search/photos?query=nature&per_page=31"),
    {},
    context()
  );
  assert.equal(tooMany.status, 400);
});

test("search reports missing server-side credentials without leaking details", async () => {
  const response = await worker.fetch(
    new Request("https://worker.example/search/photos?query=nature"),
    {},
    context()
  );
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    error: "Photo service is not configured."
  });
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("download tracking uses the configured key and fixed Unsplash endpoint", async (t) => {
  const originalFetch = globalThis.fetch;
  let capturedUrl;
  let capturedHeaders;
  globalThis.fetch = async (url, options) => {
    capturedUrl = String(url);
    capturedHeaders = options.headers;
    return new Response(JSON.stringify({ url: "https://images.unsplash.com/example" }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  };
  t.after(() => { globalThis.fetch = originalFetch; });

  const response = await worker.fetch(
    new Request("https://worker.example/photos/abc_123/download"),
    { UNSPLASH_ACCESS_KEY: "test-only-key" },
    context()
  );

  assert.equal(response.status, 200);
  assert.equal(capturedUrl, "https://api.unsplash.com/photos/abc_123/download");
  assert.equal(capturedHeaders.Authorization, "Client-ID test-only-key");
  assert.equal(capturedHeaders["Accept-Version"], "v1");
});

test("upstream rate limits are returned as a safe retryable error", async (t) => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response("private upstream detail", { status: 429 });
  t.after(() => { globalThis.fetch = originalFetch; });

  const response = await worker.fetch(
    new Request("https://worker.example/photos/abc_123/download"),
    { UNSPLASH_ACCESS_KEY: "test-only-key" },
    context()
  );

  assert.equal(response.status, 429);
  assert.deepEqual(await response.json(), {
    error: "Photo service rate limit reached. Try again shortly.",
    upstream_status: 429
  });
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("network failures are converted to a safe gateway error", async (t) => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error("private network diagnostic"); };
  t.after(() => { globalThis.fetch = originalFetch; });

  const response = await worker.fetch(
    new Request("https://worker.example/photos/abc_123/download"),
    { UNSPLASH_ACCESS_KEY: "test-only-key" },
    context()
  );

  assert.equal(response.status, 502);
  assert.deepEqual(await response.json(), {
    error: "Photo service is temporarily unavailable."
  });
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("successful photo search forwards validated parameters and returns CORS JSON", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;
  let capturedUrl;
  let capturedHeaders;
  globalThis.caches = { default: {
    async match() { return undefined; },
    async put() {}
  }};
  globalThis.fetch = async (url, options) => {
    capturedUrl = String(url);
    capturedHeaders = options.headers;
    return new Response(JSON.stringify({ results: [{ id: "photo-1" }], total: 1 }), {
      status: 200,
      headers: { "content-type": "application/json" }
    });
  };
  t.after(() => {
    globalThis.fetch = originalFetch;
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  });

  const response = await worker.fetch(
    new Request("https://worker.example/search/photos?query=forest&page=2&per_page=12&color=green&content_filter=high"),
    { UNSPLASH_ACCESS_KEY: "test-only-key" },
    context()
  );

  assert.equal(response.status, 200);
  assert.equal(response.headers.get("access-control-allow-origin"), "*");
  assert.match(response.headers.get("content-type"), /application\/json/);
  assert.equal(capturedUrl, "https://api.unsplash.com/search/photos?query=forest&page=2&per_page=12&orientation=portrait&order_by=relevant&color=green&content_filter=high");
  assert.equal(capturedHeaders.Authorization, "Client-ID test-only-key");
  assert.deepEqual(await response.json(), { results: [{ id: "photo-1" }], total: 1 });
});
test("Pixabay search requires server-side key and R2 storage", async () => {
  const response = await worker.fetch(
    new Request("https://worker.example/pixabay/search?q=forest"),
    {},
    context()
  );
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    error: "Pixabay image service is not configured."
  });
});

test("Pixabay search uses server-side key, portrait-safe filters, and rewrites media URLs", async (t) => {
  const originalFetch = globalThis.fetch;
  const originalCaches = globalThis.caches;
  let capturedUrl;
  const objects = new Map();
  const bucket = {
    async put(key, value, options) { objects.set(key, { value, options }); },
    async get(key) {
      const entry = objects.get(key);
      if (!entry) return null;
      return {
        async text() { return String(entry.value); },
        body: new Response(String(entry.value)).body,
        httpEtag: '"test-etag"',
        writeHttpMetadata(headers) {
          if (entry.options?.httpMetadata?.contentType) headers.set("content-type", entry.options.httpMetadata.contentType);
        }
      };
    }
  };
  globalThis.caches = { default: {
    async match() { return undefined; },
    async put() {}
  }};
  globalThis.fetch = async (url) => {
    capturedUrl = String(url);
    return new Response(JSON.stringify({
      total: 1, totalHits: 1,
      hits: [{
        id: 123, pageURL: "https://pixabay.com/photos/forest-123/",
        previewURL: "https://cdn.pixabay.com/photo/test_150.jpg",
        webformatURL: "https://pixabay.com/get/test_640.jpg",
        largeImageURL: "https://pixabay.com/get/test_1280.jpg",
        imageWidth: 1200, imageHeight: 1800, user: "sample", tags: "forest"
      }]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };
  t.after(() => {
    globalThis.fetch = originalFetch;
    if (originalCaches === undefined) delete globalThis.caches;
    else globalThis.caches = originalCaches;
  });

  const response = await worker.fetch(
    new Request("https://worker.example/pixabay/search?q=forest&page=2&per_page=20&order=latest"),
    { PIXABAY_API_KEY: "test-secret", PIXABAY_IMAGES: bucket },
    context()
  );
  assert.equal(response.status, 200);
  const requestUrl = new URL(capturedUrl);
  assert.equal(requestUrl.origin, "https://pixabay.com");
  assert.equal(requestUrl.searchParams.get("key"), "test-secret");
  assert.equal(requestUrl.searchParams.get("orientation"), "vertical");
  assert.equal(requestUrl.searchParams.get("safesearch"), "true");
  assert.equal(requestUrl.searchParams.get("order"), "latest");
  const json = await response.json();
  assert.equal(json.hits[0].previewURL, "https://worker.example/pixabay/media/123?size=thumb");
  assert.equal(json.hits[0].largeImageURL, "https://worker.example/pixabay/media/123?size=full");
  assert.equal(json.hits[0].pageURL, "https://pixabay.com/photos/forest-123/");
  assert.ok(objects.has("pixabay-meta/123.json"));
});
