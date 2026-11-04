// Drishyam Cloudflare proxy deployment trigger.
const API = "https://api.unsplash.com";
const PIXABAY_API = "https://pixabay.com/api/";
const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, OPTIONS",
  "access-control-allow-headers": "Content-Type",
  "access-control-max-age": "86400"
};
function json(data, status=200, extra={}) {
  return new Response(JSON.stringify(data), {status, headers:{...CORS,"content-type":"application/json; charset=utf-8",...extra}});
}
function fail(status, message) { return json({error:message},status,{"cache-control":"no-store"}); }
function positiveInt(v, fallback, max) {
  if (v == null || v === "") return fallback;
  if (!/^\d+$/.test(v)) return null;
  const n=Number(v);
  return Number.isSafeInteger(n) && n>=1 && n<=max ? n : null;
}
async function upstream(path, params, env, ctx, ttl=0) {
  if (!env.UNSPLASH_ACCESS_KEY) return fail(503,"Photo service is not configured.");
  const target=new URL(API+path);
  if (params) for (const [k,v] of params) target.searchParams.set(k,v);
  const key=new Request(target.toString(),{method:"GET"});
  if (ttl>0) {
    const cached=await caches.default.match(key);
    if (cached) {
      const headers=new Headers(cached.headers);
      Object.entries(CORS).forEach(([k,v])=>headers.set(k,v));
      return new Response(cached.body,{status:cached.status,headers});
    }
  }
  let res;
  try {
    res=await fetch(target.toString(),{headers:{"Authorization":`Client-ID ${env.UNSPLASH_ACCESS_KEY}`,"Accept-Version":"v1","Accept":"application/json"}});
  } catch { return fail(502,"Photo service is temporarily unavailable."); }
  if (!res.ok) {
    // Log only the upstream status; never log credentials or authorization headers.
    console.error("Drishyam Unsplash upstream failure", { status: res.status, path });
    const status=res.status===404?404:res.status===429?429:res.status>=500?502:res.status;
    const message=status===404?"Photo not found.":status===429?"Photo service rate limit reached. Try again shortly.":"Photo service request failed.";
    return json({error:message,upstream_status:res.status},status,{"cache-control":"no-store"});
  }
  const response=new Response(await res.text(),{status:200,headers:{...CORS,"content-type":"application/json; charset=utf-8","cache-control":ttl>0?`public, max-age=${ttl}`:"no-store"}});
  if (ttl>0) ctx.waitUntil(caches.default.put(key,response.clone()));
  return response;
}

function safePixabayQuery(v) {
  return typeof v === "string" ? v.trim().slice(0, 100) : "";
}
async function pixabaySearch(url, env, ctx) {
  if (!env.PIXABAY_API_KEY || !env.PIXABAY_IMAGES) return fail(503, "Pixabay image service is not configured.");
  const q = safePixabayQuery(url.searchParams.get("q"));
  const page = positiveInt(url.searchParams.get("page"), 1, 25);
  const per = positiveInt(url.searchParams.get("per_page"), 20, 30);
  const order = url.searchParams.get("order") || "popular";
  const orientation = url.searchParams.get("orientation") || "vertical";
  if (!q || page === null || per === null || !["popular", "latest"].includes(order) || !["vertical", "horizontal", "all"].includes(orientation)) {
    return fail(400, "Invalid Pixabay search parameters.");
  }
  const cacheUrl = new URL(url.toString());
  cacheUrl.searchParams.set("q", q);
  cacheUrl.searchParams.set("page", String(page));
  cacheUrl.searchParams.set("per_page", String(per));
  cacheUrl.searchParams.set("order", order);
  cacheUrl.searchParams.set("orientation", orientation);
  const cacheKey = new Request(cacheUrl.toString(), { method: "GET" });
  const cached = await caches.default.match(cacheKey);
  if (cached) {
    const headers = new Headers(cached.headers);
    Object.entries(CORS).forEach(([k, v]) => headers.set(k, v));
    return new Response(cached.body, { status: cached.status, headers });
  }

  const target = new URL(PIXABAY_API);
  target.searchParams.set("key", env.PIXABAY_API_KEY);
  target.searchParams.set("q", q);
  target.searchParams.set("page", String(page));
  target.searchParams.set("per_page", String(per));
  target.searchParams.set("order", order);
  target.searchParams.set("image_type", "photo");
  target.searchParams.set("orientation", orientation);
  target.searchParams.set("safesearch", "true");
  target.searchParams.set("lang", "en");

  let upstream;
  try {
    upstream = await fetch(target.toString(), { headers: { "Accept": "application/json" } });
  } catch {
    return fail(502, "Pixabay is temporarily unavailable.");
  }
  if (!upstream.ok) {
    const status = upstream.status === 429 ? 429 : upstream.status >= 500 ? 502 : upstream.status;
    console.error("Drishyam Pixabay upstream failure", { status: upstream.status });
    return json({ error: status === 429 ? "Pixabay rate limit reached. Try again later." : "Pixabay request failed." }, status, { "cache-control": "no-store" });
  }

  let body;
  try { body = await upstream.json(); }
  catch { return fail(502, "Pixabay returned an invalid response."); }

  const hits = Array.isArray(body.hits) ? body.hits : [];
  // Keep source URLs server-side in R2 metadata. The app receives only Worker-hosted
  // URLs because Pixabay prohibits permanent hotlinking of its CDN images.
  for (const hit of hits) {
    const id = Number(hit.id);
    if (!Number.isSafeInteger(id) || id <= 0) continue;
    const metadata = {
      previewURL: hit.previewURL || "",
      webformatURL: hit.webformatURL || "",
      largeImageURL: hit.largeImageURL || hit.webformatURL || "",
      pageURL: hit.pageURL || "",
      user: hit.user || "Pixabay contributor"
    };
    await env.PIXABAY_IMAGES.put("pixabay-meta/" + id + ".json", JSON.stringify(metadata), {
      httpMetadata: { contentType: "application/json", cacheControl: "public, max-age=31536000" }
    });
    hit.previewURL = new URL("/pixabay/media/" + id + "?size=thumb", url.origin).toString();
    hit.webformatURL = new URL("/pixabay/media/" + id + "?size=full", url.origin).toString();
    hit.largeImageURL = hit.webformatURL;
  }

  const response = json(body, 200, { "cache-control": "public, max-age=86400" });
  ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
  return response;
}
async function pixabayMedia(request, env) {
  if (!env.PIXABAY_IMAGES) return fail(503, "Pixabay image storage is not configured.");
  const url = new URL(request.url);
  const match = url.pathname.match(/^\/pixabay\/media\/(\d{1,15})$/);
  if (!match) return fail(404, "Image not found.");
  const id = match[1];
  const size = url.searchParams.get("size") === "full" ? "full" : "thumb";
  const imageKey = "pixabay-image/" + id + "/" + size;
  const cached = await env.PIXABAY_IMAGES.get(imageKey);
  if (cached) {
    const headers = new Headers(CORS);
    cached.writeHttpMetadata(headers);
    headers.set("cache-control", "public, max-age=31536000, immutable");
    headers.set("etag", cached.httpEtag);
    return new Response(cached.body, { status: 200, headers });
  }

  const metadataObject = await env.PIXABAY_IMAGES.get("pixabay-meta/" + id + ".json");
  if (!metadataObject) return fail(404, "Pixabay image metadata unavailable. Search again.");
  let metadata;
  try { metadata = JSON.parse(await metadataObject.text()); }
  catch { return fail(502, "Pixabay image metadata is invalid."); }
  const source = size === "full" ? (metadata.largeImageURL || metadata.webformatURL) : metadata.webformatURL;
  if (!source || !source.startsWith("https://pixabay.com/get/")) return fail(404, "Pixabay image URL is unavailable.");

  let upstream;
  try { upstream = await fetch(source); }
  catch { return fail(502, "Pixabay image download failed."); }
  if (!upstream.ok || !upstream.body) return fail(502, "Pixabay image download failed.");
  const contentType = upstream.headers.get("content-type") || "image/jpeg";
  await env.PIXABAY_IMAGES.put(imageKey, upstream.body, {
    httpMetadata: { contentType, cacheControl: "public, max-age=31536000, immutable" }
  });
  const stored = await env.PIXABAY_IMAGES.get(imageKey);
  if (!stored) return fail(502, "Pixabay image storage failed.");
  const headers = new Headers(CORS);
  stored.writeHttpMetadata(headers);
  headers.set("cache-control", "public, max-age=31536000, immutable");
  headers.set("etag", stored.httpEtag);
  return new Response(stored.body, { status: 200, headers });
}

export default {
  async fetch(request,env,ctx) {
    const url=new URL(request.url);
    if (request.method==="OPTIONS") return new Response(null,{status:204,headers:CORS});
    if (request.method!=="GET") return fail(405,"Method not allowed.");
    if (url.pathname==="/health") return json({service:"drishyam-photo-proxy",status:"ok"});
    if (url.pathname==="/pixabay/search") return pixabaySearch(url,env,ctx);
    if (url.pathname.startsWith("/pixabay/media/")) return pixabayMedia(request,env);
    if (url.pathname==="/photos") {
      const page=positiveInt(url.searchParams.get("page"),1,10000);
      const per=positiveInt(url.searchParams.get("per_page"),20,30);
      const order=url.searchParams.get("order_by")||"popular";
      if (page===null || per===null || !["popular","latest"].includes(order)) return fail(400,"Invalid photo feed parameters.");
      const params=new URLSearchParams({page:String(page),per_page:String(per),order_by:order});
      return upstream("/photos",params,env,ctx,url.searchParams.get("refresh")==="1"?0:20);
    }
    if (url.pathname==="/search/photos") {
      const query=(url.searchParams.get("query")||"").trim();
      const page=positiveInt(url.searchParams.get("page"),1,10000);
      const per=positiveInt(url.searchParams.get("per_page"),20,30);
      if (!query || query.length>120) return fail(400,"A valid search query is required.");
      if (page===null || per===null) return fail(400,"Invalid pagination.");
      const order=url.searchParams.get("order_by")||"relevant";
      if (!["relevant","latest"].includes(order)) return fail(400,"Invalid sort order.");
      const p=new URLSearchParams({query,page:String(page),per_page:String(per),orientation:"portrait",order_by:order});
      const color=url.searchParams.get("color");
      if (color && /^[a-z-]{1,20}$/i.test(color)) p.set("color",color);
      const content=url.searchParams.get("content_filter");
      if (content && ["low","high"].includes(content)) p.set("content_filter",content);
      return upstream("/search/photos",p,env,ctx,url.searchParams.get("refresh")==="1"?0:20);
    }
    const match=url.pathname.match(/^\/photos\/([A-Za-z0-9_-]{1,80})\/download$/);
    if (match) return upstream(`/photos/${encodeURIComponent(match[1])}/download`,null,env,ctx,0);
    return fail(404,"Route not found.");
  }
};
