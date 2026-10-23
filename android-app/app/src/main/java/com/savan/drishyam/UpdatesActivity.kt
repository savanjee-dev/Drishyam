package com.savan.drishyam

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Manual-only update checker backed by the latest published stable GitHub release. */
class UpdatesActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var versionText: TextView
    private lateinit var notes: TextView
    private lateinit var checkButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var releaseButton: MaterialButton
    private var apkUrl: String? = null
    private var releasePage: String? = null
    private var latestVersion: String? = null
    private var checkInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)

        val surface = themeColor(com.google.android.material.R.attr.colorSurface, R.color.bg)
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface, R.color.fg)
        val onSurfaceVariant = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.mute)
        val primary = themeColor(com.google.android.material.R.attr.colorPrimary, R.color.accent)
        window.statusBarColor = surface
        window.navigationBarColor = surface
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !isNightMode()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars =
            !isNightMode()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(20.dp(), 12.dp(), 20.dp(), 8.dp())
            addView(root)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 4.dp())
        }
        val back = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "‹"
            textSize = 30f
            minWidth = 48.dp()
            minimumWidth = 48.dp()
            insetTop = 0
            insetBottom = 0
            contentDescription = "Back"
            setPadding(0, 0, 0, 3.dp())
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(52.dp(), 52.dp()))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 0, 0, 0)
        }
        heading.addView(TextView(this).apply {
            text = "Software update"
            textSize = 25f
            maxLines = 1
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_bold)
            setTextColor(onSurface)
        })
        heading.addView(TextView(this).apply {
            text = "Keep your app up to date"
            textSize = 14f
            setTextColor(onSurfaceVariant)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val statusCard = MaterialCardView(this).apply {
            radius = 30.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = themeColor(com.google.android.material.R.attr.colorOutlineVariant, R.color.glassStroke)
            setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerLow, R.color.surface))
            useCompatPadding = false
            setContentPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
        }
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        cardContent.addView(TextView(this).apply {
            text = "INSTALLED VERSION"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(onSurfaceVariant)
        })
        cardContent.addView(TextView(this).apply {
            text = "Drishyam v${BuildConfig.VERSION_NAME}"
            textSize = 23f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(onSurface)
            setPadding(0, 4.dp(), 0, 16.dp())
        })
        versionText = TextView(this).apply {
            text = "Latest version: Not checked yet"
            textSize = 16f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(onSurface)
        }
        cardContent.addView(versionText)
        status = TextView(this).apply {
            text = "Tap Check for updates to check the latest published stable release."
            textSize = 14f
            setLineSpacing(3.dp().toFloat(), 1f)
            setTextColor(onSurfaceVariant)
            setPadding(0, 10.dp(), 0, 0)
        }
        cardContent.addView(status)
        statusCard.addView(cardContent)
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 18.dp()
        })

        root.addView(TextView(this).apply {
            text = "What's new"
            textSize = 20f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(onSurface)
            setPadding(2.dp(), 22.dp(), 0, 10.dp())
        })
        val notesCard = MaterialCardView(this).apply {
            radius = 24.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = themeColor(com.google.android.material.R.attr.colorOutlineVariant, R.color.glassStroke)
            setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer, R.color.glassFill))
            setContentPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        }
        notes = TextView(this).apply {
            text = "Release notes will appear here after you check for updates."
            textSize = 14f
            setLineSpacing(5.dp().toFloat(), 1f)
            setTextColor(onSurface)
        }
        notesCard.addView(notes)
        root.addView(notesCard)

        checkButton = MaterialButton(this).apply {
            text = "Check for updates"
            textSize = 15f
            isAllCaps = false
            cornerRadius = 22.dp()
            setOnClickListener { checkForUpdates() }
        }
        root.addView(checkButton, LinearLayout.LayoutParams(-1, 56.dp()).apply {
            topMargin = 20.dp()
        })
        downloadButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Download update"
            textSize = 15f
            isAllCaps = false
            cornerRadius = 22.dp()
            visibility = View.GONE
            setOnClickListener { downloadUpdate() }
        }
        root.addView(downloadButton, LinearLayout.LayoutParams(-1, 54.dp()).apply {
            topMargin = 10.dp()
        })
        releaseButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "View GitHub release"
            textSize = 14f
            isAllCaps = false
            cornerRadius = 20.dp()
            visibility = View.GONE
            setOnClickListener { openReleasePage() }
        }
        root.addView(releaseButton, LinearLayout.LayoutParams(-1, 48.dp()).apply {
            topMargin = 4.dp()
            bottomMargin = 12.dp()
        })

        setContentView(scroll)
    }

    private fun checkForUpdates() {
        if (checkInProgress) return
        checkInProgress = true
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
                val connection = URL("https://api.github.com/repos/savanjee-dev/Drishyam/releases/latest")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Drishyam-Android")
                connection.setRequestProperty("Cache-Control", "no-cache, no-store")
                try {
                    val code = connection.responseCode
                    if (code != 200) {
                        val detail = when (code) {
                            404 -> "No published stable release was found yet."
                            403, 429 -> "GitHub temporarily limited update checks. Please try again later."
                            else -> "GitHub returned HTTP $code. Please try again."
                        }
                        throw IllegalStateException(detail)
                    }
                    response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                error = when (e) {
                    is java.net.SocketTimeoutException -> "The request timed out. Check your connection and try again."
                    is IllegalStateException -> e.message
                    else -> "Couldn't reach GitHub Releases. Check your connection and try again."
                }
            }

            val result = response
            val failure = error
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                checkInProgress = false
                checkButton.isEnabled = true
                checkButton.text = "Check again"

                if (result == null) {
                    status.text = failure ?: "Update check failed. Please try again."
                    notes.text = "Release notes are unavailable because the release check did not complete."
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val tag = result.optString("tag_name", "").trim()
                val version = tag.removePrefix("v").trim()
                if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)) {
                    status.text = "GitHub returned an invalid release version. Please try again later."
                    notes.text = "Release notes are unavailable for this release."
                    return@runOnUiThread
                }

                releasePage = result.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                    ?: "https://github.com/savanjee-dev/Drishyam/releases/latest"
                latestVersion = version
                versionText.text = "Latest version: Drishyam v$version"
                val comparison = compareVersions(version, BuildConfig.VERSION_NAME)
                status.text = when {
                    comparison > 0 -> "A newer version is available."
                    comparison == 0 -> "You're using the latest Drishyam version."
                    else -> "The latest published release is older than this installation. No update is available."
                }

                val rawNotes = result.opt("body")
                val releaseNotes = (rawNotes as? String)?.trim().orEmpty()
                notes.text = "Loading notes for v$version…"
                Thread {
                    val exactNotes = ReleaseChangelog.fetch(version)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (latestVersion != version) return@runOnUiThread
                        notes.text = exactNotes
                            ?: releaseNotes.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                            ?: "The changelog for v$version is unavailable. Open the official release to review its details."
                    }
                }.start()

                val assets = result.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (name.equals("Drishyam-$version.apk", ignoreCase = true) &&
                            url.startsWith("https://github.com/savanjee-dev/Drishyam/releases/download/")) {
                            apkUrl = url
                            break
                        }
                    }
                }
                if (comparison > 0 && apkUrl != null) {
                    downloadButton.text = "Download v$version"
                    downloadButton.visibility = View.VISIBLE
                } else if (comparison > 0) {
                    status.text = "A newer release exists, but its matching APK isn't attached yet."
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
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Drishyam-${latestVersion ?: "update"}.apk"
                )
            manager.enqueue(request)
            Toast.makeText(this, "Download started. Open the completed download to install.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't start the download. Open the GitHub release page instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openReleasePage() {
        val url = releasePage ?: "https://github.com/savanjee-dev/Drishyam/releases/latest"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't open GitHub Releases.", Toast.LENGTH_LONG).show()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aa = Regex("\\d+").findAll(a).mapNotNull { it.value.toIntOrNull() }.toList()
        val bb = Regex("\\d+").findAll(b).mapNotNull { it.value.toIntOrNull() }.toList()
        for (i in 0 until maxOf(aa.size, bb.size)) {
            val x = aa.getOrElse(i) { 0 }
            val y = bb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun themeColor(attribute: Int, fallback: Int): Int =
        MaterialColors.getColor(this, attribute, ContextCompat.getColor(this, fallback))

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
