package com.savan.drishyam

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Strictly identifies the APK asset for a release and validates its published
 * SHA-256 metadata. Installation must not be offered until verifyFile succeeds.
 */
internal data class VerifiedReleaseApk(
    val version: String,
    val url: String,
    val expectedSha256: String,
    val fileName: String
) {
    fun verifyFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.US_ASCII),
            expectedSha256.toByteArray(Charsets.US_ASCII)
        )
    }

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
