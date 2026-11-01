const UPSTREAM = "https://nexwall.kodnextech.com/api/developer/v1";
const ALLOWED = new Set(["page", "per_page", "category_id", "type", "search", "sort"]);

function json(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, OPTIONS",
      "access-control-allow-headers": "Accept, Content-Type",
      ...extra,
    },
  });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: {
        "access-control-allow-origin": "*",
        "access-control-allow-methods": "GET, OPTIONS",
        "access-control-allow-headers": "Accept, Content-Type",
        "access-control-max-age": "86400",
      }});
    }
    if (request.method !== "GET") return json({ message: "Method not allowed" }, 405);
    if (!env.NEXWALL_API_KEY) return json({ message: "NexWall proxy is not configured" }, 503);

    let path;
    if (url.pathname === "/categories") {
      path = "/categories";
    } else if (url.pathname === "/wallpapers") {
      path = "/wallpapers";
    } else {
      return json({ message: "Not found" }, 404);
    }

    const params = new URLSearchParams();
    for (const [key, value] of url.searchParams) {
      if (!ALLOWED.has(key)) continue;
      if (key === "page") params.set(key, String(Math.max(1, Math.min(10000, Number(value) || 1))));
      else if (key === "per_page") params.set(key, String(Math.max(1, Math.min(24, Number(value) || 24))));
      else if (key === "type") params.set(key, "image");
      else if (key === "sort" && ["newest", "oldest", "popular", "random"].includes(value)) params.set(key, value);
      else if (key === "search" && value.length >= 2) params.set(key, value.slice(0, 100));
      else if (key === "category_id" && /^\d+$/.test(value)) params.set(key, value);
    }
    if (path === "/wallpapers") {
      params.set("type", "image");
      if (!params.has("per_page")) params.set("per_page", "24");
    }

    const upstreamUrl = UPSTREAM + path + (params.size ? "?" + params.toString() : "");
    const cacheKey = new Request(upstreamUrl, { method: "GET" });
    const cache = caches.default;
    const cached = await cache.match(cacheKey);
    if (cached) {
      const headers = new Headers(cached.headers);
      headers.set("access-control-allow-origin", "*");
      headers.set("x-drishyam-proxy-cache", "HIT");
      return new Response(cached.body, { status: cached.status, headers });
    }

    let upstream;
    try {
      upstream = await fetch(upstreamUrl, {
        headers: {
          "accept": "application/json",
          "authorization": "Bearer " + env.NEXWALL_API_KEY,
          "user-agent": "DrishyamAndroid/3.0 (+https://github.com/savanjee-dev/Drishyam)",
        },
      });
    } catch {
      return json({ message: "NexWall service is temporarily unreachable" }, 502);
    }

    const body = await upstream.text();
    const headers = new Headers({
      "content-type": upstream.headers.get("content-type") || "application/json",
      "access-control-allow-origin": "*",
      "cache-control": "no-store",
      "x-drishyam-proxy-cache": "MISS",
    });
    for (const name of ["retry-after", "x-ratelimit-limit", "x-ratelimit-remaining", "x-ratelimit-reset", "x-developer-api-plan"]) {
      const value = upstream.headers.get(name);
      if (value) headers.set(name, value);
    }

    const response = new Response(body, { status: upstream.status, headers });
    if (upstream.ok) {
      const cacheHeaders = new Headers(headers);
      cacheHeaders.set("cache-control", "public, max-age=300");
      ctx.waitUntil(cache.put(cacheKey, new Response(body, { status: upstream.status, headers: cacheHeaders })));
    }
    return response;
  },
};
