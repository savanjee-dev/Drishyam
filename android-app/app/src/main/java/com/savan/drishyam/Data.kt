// Trigger safe debug APK build for Cloudflare proxy integration.
package com.savan.drishyam

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Config {
    // Set this to the deployed Cloudflare Worker URL before enabling the proxy integration.
    // Do not add an Unsplash key to the Android app.
    const val UNSPLASH_PROXY_BASE_URL = "https://drishyam-unsplash-proxy.savansingh8147.workers.dev"
    const val UA = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Drishyam/2.4.3"
}

data class Photo(
    val id: String, val thumb: String, val full: String, val title: String,
    val credit: String, val link: String, val dl: String, val color: Int,
    val width: Int = 0, val height: Int = 0, val published: String = "",
    val camera: String = "", val aperture: String = "", val focalLength: String = "",
    val shutterSpeed: String = "", val iso: String = "", val downloads: String = ""
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("thumb", thumb).put("full", full)
        .put("title", title).put("credit", credit).put("link", link).put("dl", dl).put("color", color)
        .put("width", width).put("height", height).put("published", published).put("camera", camera)
        .put("aperture", aperture).put("focalLength", focalLength).put("shutterSpeed", shutterSpeed)
        .put("iso", iso).put("downloads", downloads)

    companion object {
        fun from(o: JSONObject) = Photo(
            o.getString("id"), o.getString("thumb"), o.getString("full"), o.optString("title"),
            o.optString("credit"), o.optString("link"), o.optString("dl"), o.optInt("color", 0xFF222222.toInt()),
            o.optInt("width"), o.optInt("height"), o.optString("published"), o.optString("camera"),
            o.optString("aperture"), o.optString("focalLength"), o.optString("shutterSpeed"),
            o.optString("iso"), o.optString("downloads")
        )
    }
}

object Store {
    private var cache: MutableList<Photo>? = null
    private fun prefs(c: Context) = c.getSharedPreferences("drishyam", 0)
    fun favs(c: Context): MutableList<Photo> {
        cache?.let { return it }
        val list = ArrayList<Photo>()
        try {
            val a = JSONArray(prefs(c).getString("favs", "[]"))
            for (i in 0 until a.length()) list.add(Photo.from(a.getJSONObject(i)))
        } catch (e: Exception) { }
        cache = list
        return list
    }
    fun isFav(c: Context, id: String) = favs(c).any { it.id == id }
    fun bookmarks(c: Context): MutableList<Photo> {
        val list = ArrayList<Photo>()
        try {
            val a = JSONArray(prefs(c).getString("bookmarks", "[]"))
            for (i in 0 until a.length()) list.add(Photo.from(a.getJSONObject(i)))
        } catch (_: Exception) { }
        return list
    }
    fun isBookmarked(c: Context, id: String) = bookmarks(c).any { it.id == id }
    fun toggleBookmark(c: Context, p: Photo): Boolean {
        val list = bookmarks(c)
        val had = list.removeAll { it.id == p.id }
        if (!had) list.add(p)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("bookmarks", arr.toString()).apply()
        return !had
    }
    fun toggle(c: Context, p: Photo): Boolean {
        val l = favs(c)
        val had = l.removeAll { it.id == p.id }
        if (!had) l.add(p)
        val arr = JSONArray()
        l.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("favs", arr.toString()).apply()
        return !had
    }
}

object Api {
    private fun readLimited(stream: java.io.InputStream, maxBytes: Int): String {
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw java.io.IOException("Response body exceeded the size limit.")
                out.write(buffer, 0, count)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun get(url: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.requestMethod = "GET"
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Cache-Control", "no-cache")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }

            val code = c.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val detail = try {
                    c.errorStream?.let { readLimited(it, 2048) }?.take(240)
                } catch (_: Exception) { null }
                throw Exception("HTTP $code" + if (!detail.isNullOrBlank()) ": $detail" else "")
            }
            // Bound untrusted API responses so a bad upstream response cannot exhaust app memory.
            return readLimited(c.inputStream, 5 * 1024 * 1024)
        } finally {
            c.disconnect()
        }
    }

    fun ping(downloadLocation: String) {
        // Unsplash requires its download endpoint to be triggered when a user downloads a photo.
        // Extract only the photo ID; never proxy a caller-supplied arbitrary URL.
        try {
            val id = Regex("/photos/([A-Za-z0-9_-]+)/download").find(downloadLocation)?.groupValues?.get(1)
                ?: return
            get(
                Config.UNSPLASH_PROXY_BASE_URL.trimEnd('/') + "/photos/" + id + "/download",
                emptyMap()
            )
        } catch (_: Exception) { }
    }

    /** Returns photos and the "after" token ("" when there is no more). */
    fun reddit(sub: String, after: String, sort: String = "hot"): Pair<List<Photo>, String> {
        val safeSub = sub.replace(Regex("[^A-Za-z0-9_]"), "")
        require(safeSub.isNotBlank()) { "Invalid photo topic name." }
        val query = "?limit=30&raw_json=1" +
            (if (sort == "top") "&t=week" else "") +
            (if (after.isNotEmpty()) "&after=" + java.net.URLEncoder.encode(after, "UTF-8") else "") +
            "&_=" + System.currentTimeMillis()
        val headers = mapOf(
            "User-Agent" to "android:com.savan.drishyam:v2.4.2 (Android photo app)",
            "Accept" to "application/json",
            "Accept-Language" to "en-US,en;q=0.9"
        )
        var response: String? = null
        var lastError: Exception? = null
        // Reddit occasionally blocks one public host. Try standard and old hosts before failing.
        for (host in listOf("www.reddit.com", "old.reddit.com", "reddit.com")) {
            try {
                response = get("https://$host/r/$safeSub/$sort.json$query", headers)
                break
            } catch (e: Exception) { lastError = e }
        }
        val data = JSONObject(response ?: throw (lastError ?: Exception("Reddit feed unavailable."))).getJSONObject("data")
        var next = data.optString("after", "")
        if (next == "null") next = ""
        val out = ArrayList<Photo>()
        val kids = data.getJSONArray("children")
        for (i in 0 until kids.length()) {
            val d = kids.getJSONObject(i).optJSONObject("data") ?: continue
            if (d.optBoolean("over_18") || d.optBoolean("stickied")) continue
            val u = d.optString("url_overridden_by_dest", d.optString("url", ""))
            val isImg = d.optString("post_hint") == "image" || Regex("\\.(jpe?g|png|webp)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(u)
            if (!isImg || !u.startsWith("https://")) continue
            var thumb = u
            val res = d.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)?.optJSONArray("resolutions")
            if (res != null && res.length() > 0) {
                var pick = res.getJSONObject(res.length() - 1)
                for (k in 0 until res.length()) {
                    val r = res.getJSONObject(k)
                    if (r.optInt("width") >= 320) { pick = r; break }
                }
                thumb = pick.optString("url", u)
            }
            val sr = d.optString("photo topic")
            val source = d.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)?.optJSONObject("source")
            val width = source?.optInt("width", 0) ?: 0
            val height = source?.optInt("height", 0) ?: 0
            val epoch = d.optLong("created_utc", 0L)
            val date = if (epoch > 0L) java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(epoch * 1000L)) else ""
            val author = d.optString("author", "")
            out.add(Photo(d.getString("name"), thumb, u, d.optString("title"),
                if (author.isNotBlank()) author else "r/$sr",
                "https://redd.it/" + d.optString("id"), "", 0xFF222222.toInt(), width, height, date))
        }
        return Pair(out, next)
    }

    /** Public Wikimedia Commons search used for broad text searches and invalid custom photo topic names. */
    fun commons(query: String, page: Int): Pair<List<Photo>, Boolean> {
        val search = when (query.lowercase()) {
            "incredibleindia" -> "India landscape monument architecture"
            "amoledbackgrounds" -> "dark abstract wallpaper"
            "mobilewallpaper", "verticalwallpapers", "wallpapers" -> "mobile wallpaper nature landscape"
            else -> query.replace(Regex("[^a-zA-Z0-9 ]"), " ") + " wallpaper"
        }
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=" +
            java.net.URLEncoder.encode("filetype:bitmap $search", "UTF-8") +
            "&gsrnamespace=6&gsrlimit=20&gsroffset=${(page - 1) * 20}&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=640&format=json&origin=*"
        val root = JSONObject(get(url, mapOf("User-Agent" to "DrishyamAndroid/1.2 (wallpaper viewer; contact: support@example.com)")))
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
        val out = ArrayList<Photo>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val pageObj = pages.getJSONObject(keys.next())
            val info = pageObj.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val original = info.optString("url", "")
            val thumb = info.optString("thumburl", original)
            if (!original.startsWith("https://") || !thumb.startsWith("https://")) continue
            val meta = info.optJSONObject("extmetadata")
            val artist = meta?.optJSONObject("Artist")?.optString("value", "")
                ?.replace(Regex("<[^>]*>"), "")?.take(100).orEmpty()
            val title = pageObj.optString("title", "Wallpaper").removePrefix("File:")
            val width = info.optInt("width", 0)
            val height = info.optInt("height", 0)
            out.add(Photo(pageObj.optString("pageid", title), thumb, original, title,
                if (artist.isBlank()) "Wikimedia Commons" else artist,
                "https://commons.wikimedia.org/wiki/" + java.net.URLEncoder.encode(pageObj.optString("title"), "UTF-8"),
                "", 0xFF222222.toInt(), width, height))
        }
        return Pair(out, pages.length() == 0 || out.isEmpty())
    }

    /** Returns photos and whether this was the last page. */
    fun unsplash(query: String, page: Int): Pair<List<Photo>, Boolean> {
        require(query.isNotBlank() && query.length <= 120) { "Invalid search query." }
        require(page in 1..10000) { "Invalid page." }
        val url = Config.UNSPLASH_PROXY_BASE_URL.trimEnd('/') +
            "/search/photos?query=" + java.net.URLEncoder.encode(query, "UTF-8") +
            "&orientation=portrait&per_page=20&page=$page"
        val j = JSONObject(get(url, emptyMap()))
        val arr = j.getJSONArray("results")
        val out = ArrayList<Photo>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val urls = p.getJSONObject("urls")
            val raw = urls.getString("raw")
            val full = raw + (if (raw.contains("?")) "&" else "?") + "w=1440&q=85&fm=jpg&fit=max"
            val user = p.getJSONObject("user")
            val col = try { Color.parseColor(p.optString("color", "#222222")) } catch (_: Exception) { 0xFF222222.toInt() }
            out.add(Photo(p.getString("id"), urls.getString("small"), full,
                p.optString("alt_description", "Photo").let { if (it == "null") "Photo" else it },
                "📷 " + user.optString("name"),
                p.getJSONObject("links").optString("html") + "?utm_source=drishyam&utm_medium=referral",
                p.getJSONObject("links").optString("download_location"), col,
                p.optInt("width", 0), p.optInt("height", 0),
                p.optString("created_at", "").take(10)))
        }
        return Pair(out, page >= j.optInt("total_pages", 1) || arr.length() == 0)
    }
}
