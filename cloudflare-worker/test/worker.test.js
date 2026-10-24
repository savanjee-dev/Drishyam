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
