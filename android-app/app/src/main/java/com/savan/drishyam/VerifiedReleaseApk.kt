package com.savan.drishyam

import org.json.JSONObject

/**
 * Strictly identifies the APK asset for a release and validates the SHA-256
 * digest format exposed by GitHub's release asset API.
 *
 * Callers must compare the computed SHA-256 of downloaded bytes against
 * expectedSha256 before offering installation.
 */
internal data class VerifiedReleaseApk(
    val version: String,
    val url: String,
    val expectedSha256: String,
    val fileName: String
) {
    companion object {
        private const val REPOSITORY = "savanjee-dev/Drishyam"
        private const val DOWNLOAD_PREFIX = "https://github.com/$REPOSITORY/releases/download/"

        fun fromRelease(release: JSONObject, version: String): VerifiedReleaseApk? {
            if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)) return null
            val tag = release.optString("tag_name", "")
            if (tag.removePrefix("v") != version) return null
            val assets = release.optJSONArray("assets") ?: return null
            val expectedName = "Drishyam-$version.apk"
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (!asset.optString("name").equals(expectedName, ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                val expectedPrefix = "$DOWNLOAD_PREFIX${tag}/"
                if (!url.startsWith(expectedPrefix) || url.any { it.isWhitespace() }) return null
                val digest = asset.optString("digest")
                if (!digest.startsWith("sha256:")) return null
                val hash = digest.removePrefix("sha256:").lowercase()
                if (!Regex("^[0-9a-f]{64}$").matches(hash)) return null
                return VerifiedReleaseApk(version, url, hash, expectedName)
            }
            return null
        }
    }
}
