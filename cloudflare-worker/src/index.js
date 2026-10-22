// Drishyam Cloudflare proxy deployment trigger.
const API = "https://api.unsplash.com";
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
export default {
  async fetch(request,env,ctx) {
    const url=new URL(request.url);
    if (request.method==="OPTIONS") return new Response(null,{status:204,headers:CORS});
    if (request.method!=="GET") return fail(405,"Method not allowed.");
    if (url.pathname==="/health") return json({service:"drishyam-photo-proxy",status:"ok"});
    if (url.pathname==="/search/photos") {
      const query=(url.searchParams.get("query")||"").trim();
      const page=positiveInt(url.searchParams.get("page"),1,10000);
      const per=positiveInt(url.searchParams.get("per_page"),20,30);
      if (!query || query.length>120) return fail(400,"A valid search query is required.");
      if (page===null || per===null) return fail(400,"Invalid pagination.");
      const p=new URLSearchParams({query,page:String(page),per_page:String(per),orientation:"portrait"});
      const color=url.searchParams.get("color");
      if (color && /^[a-z-]{1,20}$/i.test(color)) p.set("color",color);
      const content=url.searchParams.get("content_filter");
      if (content && ["low","high"].includes(content)) p.set("content_filter",content);
      return upstream("/search/photos",p,env,ctx,20);
    }
    const match=url.pathname.match(/^\/photos\/([A-Za-z0-9_-]{1,80})\/download$/);
    if (match) return upstream(`/photos/${encodeURIComponent(match[1])}/download`,null,env,ctx,0);
    return fail(404,"Route not found.");
  }
};
