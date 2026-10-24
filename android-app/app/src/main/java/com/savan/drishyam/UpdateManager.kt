package com.savan.drishyam

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.net.Uri
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Checks the public GitHub Releases page and offers a user-approved APK download. */
object UpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/savanjee-dev/Drishyam/releases/latest"
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var checking = false
    private const val PREFS_UPDATES = "drishyam_update_state"
    private const val KEY_LAST_SUCCESSFUL_CHECK = "last_successful_check_ms"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private val dismissedThisSession = mutableSetOf<String>()

    fun check(context: Activity, popupSource: View, manual: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_UPDATES, Context.MODE_PRIVATE)
        if (!manual && System.currentTimeMillis() - prefs.getLong(KEY_LAST_SUCCESSFUL_CHECK, 0L) < CHECK_INTERVAL_MS) return
        if (checking) return
        checking = true
        Thread {
            var release: JSONObject? = null
            try {
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 9000
                connection.readTimeout = 12000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Drishyam-Android")
                connection.setRequestProperty("Cache-Control", "no-cache")
                try {
                    if (connection.responseCode != 200) throw Exception("GitHub returned HTTP ${connection.responseCode}")
                    release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally { connection.disconnect() }
            } catch (_: Exception) { }
            val result = release
            main.post {
                checking = false
                if (context.isFinishing || context.isDestroyed) return@post
                if (result == null) {
                    if (manual) Toast.makeText(context, "Update check failed. Check your internet and GitHub Releases.", Toast.LENGTH_LONG).show()
                    return@post
                }
                val tag = result.optString("tag_name", "").trim()
                val version = tag.removePrefix("v").trim()
                if (version.isBlank() || Regex("\\d+").find(version) == null) {
                    if (manual) Toast.makeText(context, "GitHub did not return a valid release version.", Toast.LENGTH_LONG).show()
                    return@post
                }
                // A valid release response is the successful check; failed network/API calls never reset the interval.
                prefs.edit().putLong(KEY_LAST_SUCCESSFUL_CHECK, System.currentTimeMillis()).apply()
                synchronized(dismissedThisSession) {
                    if (!manual && dismissedThisSession.contains(version)) return@post
                }
                val current = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }
                val versionComparison = compareVersions(version, current)
                if (versionComparison <= 0) {
                    if (manual) {
                        val message = if (versionComparison == 0) "You're using the latest Drishyam version." else "No newer Drishyam release is available."
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    return@post
                }
                val verifiedAsset = VerifiedReleaseApk.fromRelease(result, version)
                val apkUrl = verifiedAsset?.url
                val apkName = verifiedAsset?.fileName ?: "Drishyam-$version.apk"
                if (apkUrl == null) {
                    if (manual) Toast.makeText(context, "Release found, but no APK asset is attached yet.", Toast.LENGTH_LONG).show()
                    return@post
                }
                val safeSource = context.findViewById<View>(android.R.id.content)
                try { showReleaseDialog(context, safeSource, tag, version, result.optString("body", "No changelog was provided."), result.optString("html_url"), verifiedAsset!!, manual) }
                catch (_: Exception) { if (manual) Toast.makeText(context, "Update found, but the dialog could not open. Open the release page instead.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun compareVersions(a: String, b: String): Int {
        val aa = Regex("\\d+").findAll(a).mapNotNull { it.value.toIntOrNull() }.toList()
        val bb = Regex("\\d+").findAll(b).mapNotNull { it.value.toIntOrNull() }.toList()
        for (i in 0 until maxOf(aa.size, bb.size)) {
            val x = aa.getOrElse(i) { 0 }; val y = bb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun showReleaseDialog(a: Activity, source: View, tag: String, version: String, notes: String, pageUrl: String?, releaseApk: VerifiedReleaseApk, manual: Boolean) {
        val apkUrl = releaseApk.url\n        showGlassPopup(a, source) { box, close ->
            box.setPadding(24.dp(), 24.dp(), 24.dp(), 20.dp())
            box.addView(TextView(a).apply {
                text = if (manual) "Drishyam updates" else "Drishyam $version is here"
                textSize = 25f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(a, R.font.montserrat_bold)
                setTextColor(ContextCompat.getColor(a, R.color.fg))
            })
            box.addView(TextView(a).apply {
                text = "What's new"
                textSize = 15f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(a, R.font.poppins_medium)
                setTextColor(ContextCompat.getColor(a, R.color.accent))
                setPadding(0, 14.dp(), 0, 5.dp())
            })
            val formatted = notes.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
                .map { line -> if (line.startsWith("-") || line.startsWith("•") || line.startsWith("*")) "•  ${line.drop(1).trim()}" else "•  $line" }
                .joinToString("\n")
            box.addView(TextView(a).apply {
                text = formatted.ifBlank { "• Bug fixes and visual improvements." }
                textSize = 14f
                setLineSpacing(4.dp().toFloat(), 1f)
                setTextColor(ContextCompat.getColor(a, R.color.fg))
                maxHeight = 260.dp()
                isVerticalScrollBarEnabled = true
                movementMethod = android.text.method.ScrollingMovementMethod.getInstance()
                setPadding(0, 0, 0, 8.dp())
            })
            box.addView(TextView(a).apply {
                text = "Download the APK, then approve installation in Android. Your app data is retained when the signing key matches."
                textSize = 12f
                setLineSpacing(2.dp().toFloat(), 1f)
                setTextColor(ContextCompat.getColor(a, R.color.mute))
                setPadding(0, 8.dp(), 0, 0)
            })
            val download = TextView(a).apply {
                text = "Download update"
                gravity = Gravity.CENTER
                textSize = 15f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(a, R.font.poppins_medium)
                setTextColor(ContextCompat.getColor(a, android.R.color.white))
                background = ContextCompat.getDrawable(a, R.drawable.accent_pill)
                pressFx()
                setOnClickListener {
                    download.isEnabled = false
                    download.text = "Verifying download…"
                    VerifiedUpdateInstaller.downloadAndInstall(a, releaseApk) { message ->
                        download.text = message
                        download.isEnabled = true
                    }
                }
            }
            box.addView(download, LinearLayout.LayoutParams(-1, 52.dp()).apply { topMargin = 18.dp() })

            // Fallback link follows the release currently returned by GitHub, never an outdated hardcoded tag.
            val releasePage = pageUrl?.takeIf { it.startsWith("https://github.com/savanjee-dev/Drishyam/releases/") }
                ?: "https://github.com/savanjee-dev/Drishyam/releases/latest"
            val githubRelease = TextView(a).apply {
                text = "⌘  Download from GitHub Releases\n       Open official $tag release page  ↗"
                gravity = Gravity.CENTER_VERTICAL
                textSize = 14f
                setLineSpacing(3.dp().toFloat(), 1f)
                typeface = androidx.core.content.res.ResourcesCompat.getFont(a, R.font.poppins_medium)
                setTextColor(ContextCompat.getColor(a, R.color.fg))
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                background = ContextCompat.getDrawable(a, R.drawable.glass_pill)
                isClickable = true
                isFocusable = true
                pressFx()
                contentDescription = "Download from GitHub Releases. Opens the official Drishyam $tag release page."
                setOnClickListener {
                    try {
                        a.startActivity(android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            Uri.parse(releasePage)
                        ))
                    } catch (_: Exception) {
                        Toast.makeText(a, "Couldn't open GitHub Releases. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            box.addView(githubRelease, LinearLayout.LayoutParams(-1, 68.dp()).apply { topMargin = 10.dp() })
            box.addView(TextView(a).apply {
                text = "Later"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(ContextCompat.getColor(a, R.color.mute))
                isClickable = true
                setPadding(0, 14.dp(), 0, 0)
                setOnClickListener {
                    synchronized(dismissedThisSession) { dismissedThisSession.add(version) }
                    close()
                }
            }, LinearLayout.LayoutParams(-1, 42.dp()))
        }
    }
}
