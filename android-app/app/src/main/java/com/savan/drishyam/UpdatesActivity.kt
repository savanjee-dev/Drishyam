package com.savan.drishyam

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Dedicated, retryable update screen. Manual checks always make a fresh API request. */
class UpdatesActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var versionText: TextView
    private lateinit var notes: TextView
    private lateinit var checkButton: Button
    private lateinit var downloadButton: Button
    private lateinit var releaseButton: Button
    private var apkUrl: String? = null
    private var releasePage: String? = null
    private var latestVersion: String? = null
    private var latestTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 18.dp(), 22.dp(), 24.dp())
            setBackgroundColor(ContextCompat.getColor(this@UpdatesActivity, R.color.bg))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 36f
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.fg))
            setPadding(0, 0, 18.dp(), 0)
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(back)
        header.addView(TextView(this).apply {
            text = "Drishyam updates"
            textSize = 25f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_bold)
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.fg))
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Keep your app up to date"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.mute))
            setPadding(0, 5.dp(), 0, 18.dp())
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            background = ContextCompat.getDrawable(this@UpdatesActivity, R.drawable.glass_row)
        }
        card.addView(TextView(this).apply {
            text = "INSTALLED VERSION"
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.mute))
        })
        card.addView(TextView(this).apply {
            text = "Drishyam v${BuildConfig.VERSION_NAME}"
            textSize = 20f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.fg))
            setPadding(0, 5.dp(), 0, 14.dp())
        })
        versionText = TextView(this).apply {
            text = "Latest version: Not checked yet"
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.fg))
        }
        card.addView(versionText)
        status = TextView(this).apply {
            text = "Tap Check for updates to connect to GitHub Releases."
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.mute))
            setPadding(0, 10.dp(), 0, 0)
        }
        card.addView(status)
        root.addView(card)
        root.addView(TextView(this).apply {
            text = "What's new"
            textSize = 18f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.fg))
            setPadding(0, 22.dp(), 0, 8.dp())
        })
        notes = TextView(this).apply {
            text = "The latest release notes will appear here after the check completes."
            textSize = 14f
            setLineSpacing(4.dp().toFloat(), 1f)
            setTextColor(ContextCompat.getColor(this@UpdatesActivity, R.color.fg))
            setPadding(0, 0, 0, 16.dp())
        }
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(notes) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        checkButton = makeButton("Check for updates") { checkForUpdates() }
        root.addView(checkButton, LinearLayout.LayoutParams(-1, 50.dp()).apply { topMargin = 14.dp() })
        downloadButton = makeButton("Download update") { downloadUpdate() }.apply { visibility = View.GONE }
        root.addView(downloadButton, LinearLayout.LayoutParams(-1, 50.dp()).apply { topMargin = 10.dp() })
        releaseButton = makeButton("Open GitHub release page") {
            openReleasePage()
        }.apply { visibility = View.GONE }
        root.addView(releaseButton, LinearLayout.LayoutParams(-1, 48.dp()).apply { topMargin = 8.dp() })
        setContentView(root)
        checkForUpdates()
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun checkForUpdates() {
        checkButton.isEnabled = false
        checkButton.text = "Checking…"
        status.text = "Connecting to GitHub Releases…"
        apkUrl = null
        downloadButton.visibility = View.GONE
        releaseButton.visibility = View.GONE
        Thread {
            var response: JSONObject? = null
            var error: String? = null
            try {
                val connection = URL("https://api.github.com/repos/savanjee-dev/Drishyam/releases/latest").openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Drishyam-Android")
                connection.setRequestProperty("Cache-Control", "no-cache")
                try {
                    val code = connection.responseCode
                    if (code != 200) throw IllegalStateException("GitHub returned HTTP $code")
                    response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                error = when (e) {
                    is java.net.SocketTimeoutException -> "The request timed out. Check your connection and try again."
                    else -> "Couldn't reach GitHub Releases. Check your internet connection and try again."
                }
            }
            val result = response
            val failure = error
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                checkButton.isEnabled = true
                checkButton.text = "Check again"
                if (result == null) {
                    status.text = failure ?: "Update check failed. Please try again."
                    notes.text = "Release notes are unavailable until the update check succeeds."
                    Toast.makeText(this, "Update check failed. You can try again.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val tag = result.optString("tag_name", "").trim()
                val version = tag.removePrefix("v").trim()
                if (version.isBlank() || Regex("\\d+").find(version) == null) {
                    status.text = "GitHub returned an invalid release version. Please try again."
                    return@runOnUiThread
                }
                latestTag = tag
                latestVersion = version
                releasePage = result.optString("html_url").takeIf { it.startsWith("https://") }
                    ?: "https://github.com/savanjee-dev/Drishyam/releases/latest"
                versionText.text = "Latest version: Drishyam v$version"
                val current = BuildConfig.VERSION_NAME
                val comparison = compareVersions(version, current)
                status.text = when {
                    comparison > 0 -> "A newer version is available."
                    comparison == 0 -> "You're using the latest Drishyam version."
                    else -> "The public release is older than the installed version. Check again later."
                }
                notes.text = result.optString("body", "").trim().ifBlank { "No release notes were provided." }
                val assets = result.optJSONArray("assets")
                if (assets != null) for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").lowercase(Locale.ROOT).endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                        if (apkUrl != null) break
                    }
                }
                if (comparison > 0 && apkUrl != null) {
                    downloadButton.visibility = View.VISIBLE
                    downloadButton.text = "Download v$version"
                } else if (comparison > 0) {
                    status.text = "A newer release exists, but its APK is not attached yet."
                }
                releaseButton.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun downloadUpdate() {
        val url = apkUrl ?: run {
            Toast.makeText(this, "No APK download is available yet.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Drishyam ${latestVersion ?: ""}")
                .setDescription("Downloading Drishyam update")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Drishyam-${latestVersion ?: "update"}.apk")
            manager.enqueue(request)
            Toast.makeText(this, "Download started. Open the completed download to install.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't start the download. Open the GitHub release page instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openReleasePage() {
        val url = releasePage ?: "https://github.com/savanjee-dev/Drishyam/releases/latest"
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Couldn't open GitHub Releases.", Toast.LENGTH_LONG).show() }
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
}
