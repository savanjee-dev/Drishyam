package com.savan.drishyam

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Persistent, offline-first cache for exact release notes and the chronological release list. */
internal object ReleaseNotesCache {
    private const val PREFS = "drishyam_release_notes_cache"
    private const val KEY_HISTORY = "release_history_json"
    private const val KEY_PREFIX = "release_notes_"
    private const val API = "https://api.github.com/repos/savanjee-dev/Drishyam/releases"

    data class Release(val version: String, val title: String, val date: String, val notes: String, val url: String)

    fun cachedHistory(context: Context): List<Release> =
        parseHistory(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HISTORY, null))

    fun cachedNotes(context: Context, version: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREFIX + version, null)
            ?.takeIf { it.isNotBlank() }

    fun saveNotes(context: Context, version: String, notes: String) {
        if (validVersion(version) && notes.isNotBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PREFIX + version, notes).apply()
        }
    }

    fun fetchHistory(context: Context): List<Release>? {
        val body = request("$API?per_page=30") ?: return null
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val releases = ArrayList<Release>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue
            val version = item.optString("tag_name").removePrefix("v")
            if (!validVersion(version)) continue
            val title = item.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "Drishyam $version"
            val date = item.optString("published_at").take(10)
            val notes = exactNotesFromRelease(item, version).orEmpty()
            if (notes.isNotBlank()) saveNotes(context, version, notes)
            releases.add(Release(version, title, date, notes.ifBlank { "Release notes are not available offline yet." },
                item.optString("html_url").takeIf { it.startsWith("https://github.com/savanjee-dev/Drishyam/releases/") }
                    ?: "https://github.com/savanjee-dev/Drishyam/releases"))
        }
        if (releases.isEmpty()) return null
        val json = JSONArray()
        releases.forEach { r -> json.put(JSONObject().put("version", r.version).put("title", r.title).put("date", r.date).put("notes", r.notes).put("url", r.url)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, json.toString()).apply()
        return releases
    }

    fun fetchNotes(context: Context, version: String): String? {
        cachedNotes(context, version)?.let { return it }
        if (!validVersion(version)) return null
        val body = request("$API/tags/v$version") ?: return null
        val release = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (release.optString("tag_name").removePrefix("v") != version) return null
        val notes = exactNotesFromRelease(release, version) ?: return null
        saveNotes(context, version, notes)
        return notes
    }

    private fun exactNotesFromRelease(release: JSONObject, version: String): String? {
        val assets = release.optJSONArray("assets")
        if (assets != null) for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").equals("DRISHYAM-$version-CHANGELOG.txt", true)) continue
            val url = asset.optString("browser_download_url", "")
            if (!url.startsWith("https://github.com/savanjee-dev/Drishyam/releases/download/v$version/")) continue
            request(url)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return release.optString("body", "").trim().takeIf { it.isNotBlank() && !it.equals("null", true) }
    }

    private fun parseHistory(raw: String?): List<Release> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val version = o.optString("version")
            if (!validVersion(version)) return@mapNotNull null
            Release(version, o.optString("title", "Drishyam $version"), o.optString("date"), o.optString("notes"),
                o.optString("url", "https://github.com/savanjee-dev/Drishyam/releases"))
        }
    }

    private fun validVersion(version: String) = Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)

    private fun request(url: String): String? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 9000
            c.readTimeout = 12000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Drishyam-Android")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) { null } finally { c?.disconnect() }
    }
}
