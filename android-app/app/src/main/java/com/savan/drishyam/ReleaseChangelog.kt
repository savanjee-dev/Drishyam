package com.savan.drishyam

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches release notes for an exact published version. Never substitutes notes from another release.
 */
object ReleaseChangelog {
    private const val API = "https://api.github.com/repos/savanjee-dev/Drishyam/releases/tags/v"

    fun fetch(version: String): String? {
        if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)) return null
        val release = requestJson(API + version) ?: return null
        val tag = release.optString("tag_name", "").removePrefix("v")
        if (tag != version) return null

        val assets = release.optJSONArray("assets")
        val expected = "DRISHYAM-$version-CHANGELOG.txt"
        if (assets != null) for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").equals(expected, true)) continue
            val url = asset.optString("browser_download_url", "")
            if (!url.startsWith("https://github.com/savanjee-dev/Drishyam/releases/download/v$version/")) return null
            requestText(url)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // Only use the description belonging to this exact release as a fallback.
        val body = release.optString("body", "").trim()
        return body.takeIf { it.isNotBlank() && !it.equals("null", true) }
    }

    private fun requestJson(url: String): JSONObject? {
        val text = requestText(url) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun requestText(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 9000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Drishyam-Android")
            connection.setRequestProperty("Cache-Control", "no-cache, no-store")
            if (connection.responseCode != 200) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
