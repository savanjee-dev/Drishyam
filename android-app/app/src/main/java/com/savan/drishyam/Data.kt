// Trigger safe debug APK build for Cloudflare proxy integration.
package com.savan.drishyam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Config {
    const val NEXWALL_API_BASE_URL = "https://nexwall.kodnextech.com/api/developer/v1"
    const val NEXWALL_API_KEY = "207|zOUljlzUpzFU6uGK1l9lCcTIbiYvCsEN7xkOnSyJ899d1e12"
    // Pixabay credentials stay in the Cloudflare Worker, never in the Android app.
    const val PIXABAY_PROXY_BASE_URL = "https://drishyam-unsplash-proxy.savansingh8147.workers.dev"
    const val UA = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Drishyam/2.4.3"
}

data class Photo(
    val id: String, val thumb: String, val full: String, val title: String,
    val credit: String, val link: String, val dl: String, val color: Int,
    val width: Int = 0, val height: Int = 0, val published: String = "", val publishedAt: Long = 0L, val likes: Int = 0,
    val camera: String = "", val aperture: String = "", val focalLength: String = "",
    val shutterSpeed: String = "", val iso: String = "", val downloads: String = "",
    val license: String = "", val licenseUrl: String = "", val creatorUrl: String = "", val source: String = "",
    val latitude: Double? = null, val longitude: Double? = null
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("thumb", thumb).put("full", full)
        .put("title", title).put("credit", credit).put("link", link).put("dl", dl).put("color", color)
        .put("width", width).put("height", height).put("published", published).put("publishedAt", publishedAt).put("likes", likes).put("camera", camera)
        .put("aperture", aperture).put("focalLength", focalLength).put("shutterSpeed", shutterSpeed)
        .put("iso", iso).put("downloads", downloads).put("license", license)
        .put("licenseUrl", licenseUrl).put("creatorUrl", creatorUrl).put("source", source)
        .put("latitude", latitude).put("longitude", longitude)

    companion object {
        fun from(o: JSONObject) = Photo(
            o.getString("id"), o.getString("thumb"), o.getString("full"), o.optString("title"),
            o.optString("credit"), o.optString("link"), o.optString("dl"), o.optInt("color", 0xFF222222.toInt()),
            o.optInt("width"), o.optInt("height"), o.optString("published"), o.optLong("publishedAt"), o.optInt("likes"), o.optString("camera"),
            o.optString("aperture"), o.optString("focalLength"), o.optString("shutterSpeed"),
            o.optString("iso"), o.optString("downloads"), o.optString("license"),
            o.optString("licenseUrl"), o.optString("creatorUrl"), o.optString("source"),
            if (o.has("latitude") && !o.isNull("latitude")) o.optDouble("latitude") else null,
            if (o.has("longitude") && !o.isNull("longitude")) o.optDouble("longitude") else null
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
    fun commons(query: String, page: Int, quality: String = "High", sort: String = "new"): Pair<List<Photo>, Boolean> {
        // Commons' bitmap filter includes drawings and illustrations. Bias feed
        // discovery toward files explicitly described as photographs, and avoid
        // appending the overly restrictive "wallpaper" term to every category query.
        val cleanQuery = query.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        val search = when (query.lowercase().trim()) {
            "incredibleindia" -> "India landscape photograph"
            "amoledbackgrounds" -> "dark abstract photograph"
            "mobilewallpaper", "verticalwallpapers", "wallpapers" -> "mobile nature landscape photograph"
            else -> {
                val terms = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }.take(5)
                (terms + if (terms.none { it.equals("photograph", true) || it.equals("photography", true) }) listOf("photograph") else emptyList())
                    .joinToString(" ")
            }
        }
        val previewWidth = when (quality) {
            "Standard" -> 800
            "High" -> 1600
            // Raw opens the original in the viewer; feed tiles remain bounded at 1600px.
            "Raw" -> 1600
            else -> 1600
        }
        val previewParam = if (previewWidth > 0) "&iiurlwidth=$previewWidth" else ""
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=" +
            java.net.URLEncoder.encode("filetype:bitmap $search", "UTF-8") +
            "&gsrnamespace=6" + (when (sort) {
                "hot" -> "&gsrqiprofile=popular_inclinks_pv"
                "top" -> "&gsrqiprofile=popular_inclinks"
                else -> "&gsrsort=create_timestamp_desc"
            }) + "&gsrlimit=50&gsroffset=${(page - 1) * 50}" +
            "&prop=imageinfo&iiprop=url|timestamp|extmetadata$previewParam&format=json&origin=*"
        val root = JSONObject(get(url, mapOf("User-Agent" to "DrishyamAndroid/2.7.13 (https://github.com/savanjee-dev/Drishyam)")))
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
            fun metadata(name: String): String = meta?.optJSONObject(name)?.optString("value", "")
                ?.replace(Regex("<[^>]*>"), "")?.replace("&amp;", "&")?.trim().orEmpty()
            val artistHtml = meta?.optJSONObject("Artist")?.optString("value", "").orEmpty()
            val artist = metadata("Artist").take(100)
            val title = pageObj.optString("title", "Wallpaper").removePrefix("File:")
            val width = info.optInt("width", 0)
            val height = info.optInt("height", 0)
            val uploadedAt = info.optString("timestamp", "")
            val uploadedAtMillis = try { java.time.Instant.parse(uploadedAt).toEpochMilli() } catch (_: Exception) { 0L }
            val uploadedDate = if (uploadedAtMillis > 0L)
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(uploadedAtMillis))
                else ""
            val pageUrl = "https://commons.wikimedia.org/wiki/" + java.net.URLEncoder.encode(pageObj.optString("title"), "UTF-8")
            out.add(Photo(pageObj.optString("pageid", title), thumb, original, title,
                if (artist.isBlank()) "Wikimedia Commons" else artist,
                pageUrl, "", 0xFF222222.toInt(), width, height, uploadedDate, uploadedAtMillis,
                license = metadata("LicenseShortName"),
                licenseUrl = metadata("LicenseUrl"),
                creatorUrl = Regex("href=[\\\"']([^\\\"']+)").find(artistHtml)?.groupValues?.getOrNull(1) ?: "",
                source = "Wikimedia Commons",
                latitude = metadata("GPSLatitude").replace(",", ".").toDoubleOrNull(),
                longitude = metadata("GPSLongitude").replace(",", ".").toDoubleOrNull()))
        }
        return Pair(out, pages.length() == 0 || out.isEmpty())
    }

    /** Direct NexWall image search. The API key is embedded in the client at the user's request. */
    fun nexwall(query: String, page: Int, sort: String = "newest"): Pair<List<Photo>, Boolean> {
        require(query.length <= 100) { "Invalid NexWall search query." }
        require(page in 1..10000) { "Invalid page." }
        val params = buildString {
            append("?page=").append(page)
            append("&per_page=24&type=image")
            append("&sort=").append(java.net.URLEncoder.encode(sort, "UTF-8"))
            if (query.length >= 2) append("&search=").append(java.net.URLEncoder.encode(query, "UTF-8"))
        }
        val root = JSONObject(get(
            Config.NEXWALL_API_BASE_URL.trimEnd('/') + "/wallpapers" + params,
            mapOf(
                "User-Agent" to "DrishyamAndroid/3.0 (https://github.com/savanjee-dev/Drishyam)",
                "Authorization" to "Bearer " + Config.NEXWALL_API_KEY
            )
        ))
        val items = root.optJSONArray("data") ?: root.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
        val currentPage = root.optInt("current_page", page)
        val lastPage = root.optInt("last_page", currentPage)
        val out = ArrayList<Photo>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val full = item.optString("image_url", "")
            if (id.isBlank() || !full.startsWith("https://")) continue
            val thumb = item.optString("thumbnail_url", item.optString("thumbnail", full))
                .takeIf { it.startsWith("https://") } ?: full
            val category = item.optJSONObject("category")?.optString("name", "").orEmpty()
            val title = item.optString("title", item.optString("name", category.ifBlank { "NexWall wallpaper" })).take(180)
            val width = item.optInt("width", 0)
            val height = item.optInt("height", 0)
            val created = item.optString("created_at", item.optString("published_at", ""))
            val publishedAt = try { java.time.Instant.parse(created).toEpochMilli() } catch (_: Exception) { 0L }
            out.add(Photo(
                id = "nexwall:$id", thumb = thumb, full = full, title = title,
                credit = category.ifBlank { "NexWall" },
                link = "https://nexwall.kodnextech.com", dl = "", color = 0xFF222222.toInt(),
                width = width, height = height, published = created.take(10), publishedAt = publishedAt,
                source = "NexWall"
            ))
        }
        return Pair(out, currentPage >= lastPage || items.length() == 0)
    }


    /** Pixabay provider routed through the Cloudflare Worker and its R2 image store. */
    fun pixabay(query: String, page: Int, order: String = "popular"): Pair<List<Photo>, Boolean> {
        require(query.isNotBlank() && query.length <= 100) { "Invalid Pixabay search query." }
        require(page >= 1) { "Invalid Pixabay page." }
        if (page > 25) return Pair(emptyList(), true)
        val base = Config.PIXABAY_PROXY_BASE_URL.trimEnd('/')
        val url = "$base/pixabay/search?q=" + java.net.URLEncoder.encode(query, "UTF-8") +
            "&page=$page&per_page=20&order=" + (if (order == "latest") "latest" else "popular")
        val root = JSONObject(get(url, emptyMap()))
        val hits = root.optJSONArray("hits") ?: JSONArray()
        val totalHits = root.optInt("totalHits", 0).coerceAtMost(500)
        val out = ArrayList<Photo>()
        for (i in 0 until hits.length()) {
            val hit = hits.optJSONObject(i) ?: continue
            val id = hit.optLong("id", 0L)
            if (id <= 0L) continue
            val thumb = hit.optString("previewURL", "")
            val full = hit.optString("largeImageURL", hit.optString("webformatURL", ""))
            val pageUrl = hit.optString("pageURL", "")
            if (!thumb.startsWith("https://") || !full.startsWith("https://") ||
                !pageUrl.startsWith("https://pixabay.com/")) continue
            val user = hit.optString("user", "Pixabay contributor")
            val userId = hit.optLong("user_id", 0L)
            val creatorUrl = if (userId > 0L) {
                "https://pixabay.com/users/" + java.net.URLEncoder.encode(user, "UTF-8").replace("+", "%20") + "-$userId/"
            } else ""
            out.add(Photo(
                id = "pixabay:$id", thumb = thumb, full = full,
                title = hit.optString("tags", "Photo").take(160),
                credit = "Pixabay · $user", link = pageUrl, dl = "", color = 0xFF222222.toInt(),
                width = hit.optInt("imageWidth", hit.optInt("webformatWidth", 0)),
                height = hit.optInt("imageHeight", hit.optInt("webformatHeight", 0)),
                likes = hit.optInt("likes", 0), downloads = hit.optString("downloads", ""),
                license = "Pixabay Content License",
                licenseUrl = "https://pixabay.com/service/license-summary/",
                creatorUrl = creatorUrl, source = "Pixabay"
            ))
        }
        return Pair(out, hits.length() == 0 || page * 20 >= totalHits)
    }

}
