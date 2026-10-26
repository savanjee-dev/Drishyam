package com.savan.drishyam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private lateinit var downloadProgress: WavyProgressView
    private lateinit var statusRing: StatusRingView
    private var apkUrl: String? = null
    private var verifiedApk: VerifiedReleaseApk? = null
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
            text = "Drishyam updates"
            textSize = 25f
            maxLines = 1
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_bold)
            setTextColor(onSurface)
        })
        heading.addView(TextView(this).apply {
            text = "Every release, a better perspective"
            textSize = 14f
            setTextColor(onSurfaceVariant)
        })
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val statusCard = MaterialCardView(this).apply {
            radius = 34.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = themeColor(com.google.android.material.R.attr.colorOutlineVariant, R.color.glassStroke)
            setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorPrimaryContainer, R.color.surface))
            useCompatPadding = false
            setContentPadding(24.dp(), 26.dp(), 24.dp(), 26.dp())
        }
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        statusRing = StatusRingView(this).apply { state = "up" }
        cardContent.addView(statusRing, LinearLayout.LayoutParams(156.dp(), 156.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = 14.dp()
        })
        cardContent.addView(TextView(this).apply {
            text = "You're up to date"
            textSize = 22f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_bold)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer, R.color.fg))
            gravity = Gravity.CENTER
        })
        cardContent.addView(TextView(this).apply {
            text = "Drishyam " + BuildConfig.VERSION_NAME + " is the latest verified release."
            textSize = 29f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer, R.color.fg))
            gravity = Gravity.CENTER
            setPadding(0, 6.dp(), 0, 18.dp())
        })
        versionText = TextView(this).apply {
            text = "Latest version: Not checked yet"
            textSize = 16f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdatesActivity, R.font.montserrat_semibold)
            setTextColor(onSurface)
        }
        versionText.text = "Installed version · v${BuildConfig.VERSION_NAME}"
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
            text = "Release notes"
            textSize = 22f
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
            text = "Check for updates to load the changelog for the latest published version."
            textSize = 14f
            setLineSpacing(6.dp().toFloat(), 1f)
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
        downloadProgress = WavyProgressView(this).apply {
            progress = 0
            visibility = View.GONE
        }
        root.addView(downloadProgress, LinearLayout.LayoutParams(-1, 30.dp()).apply {
            topMargin = 8.dp()
            bottomMargin = 4.dp()
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
        if (!getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("motion_reduced", false)) animatePageEntrance(scroll)
        if (motionEnabled(this) && !getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("motion_reduced", false)) {
            val expressiveCards = listOf(statusCard, notesCard)
            expressiveCards.forEachIndexed { index, card ->
                card.alpha = 0f
                card.translationY = 18.dp().toFloat()
                card.scaleX = 0.985f
                card.scaleY = 0.985f
                card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(90L + index * 85L).setDuration(280L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }
    }

    private fun checkForUpdates() {
        if (checkInProgress) return
        checkInProgress = true
        checkButton.isEnabled = false
        checkButton.text = "Checking…"
        statusRing.state = "checking"
        status.text = "Connecting to GitHub Releases…"
        apkUrl = null
        verifiedApk = null
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
                    statusRing.state = "error"
                    status.text = failure ?: "Update check failed. Please try again."
                    Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val tag = result.optString("tag_name", "").trim()
                val version = tag.removePrefix("v").trim()
                if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)) {
                    statusRing.state = "error"
                    status.text = "GitHub returned an invalid release version. Please try again later."
                    notes.text = "Release notes are unavailable for this release."
                    return@runOnUiThread
                }

                releasePage = result.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                    ?: "https://github.com/savanjee-dev/Drishyam/releases/latest"
                latestVersion = version
                versionText.text = "Latest version: Drishyam v$version"
                val comparison = compareVersions(version, BuildConfig.VERSION_NAME)
                statusRing.state = if (comparison > 0) "available" else "up"
                status.text = when {
                    comparison > 0 -> "Checked just now · A newer version is available."
                    comparison == 0 -> "Checked just now · You're up to date. You're using the latest Drishyam version."
                    else -> "Checked just now · The latest published release is older than this installation. No update is available."
                }

                val rawNotes = result.opt("body")
                val releaseNotes = (rawNotes as? String)?.trim().orEmpty()
                val cachedNotes = ReleaseNotesCache.cachedNotes(this, version)
                notes.text = cachedNotes
                    ?: releaseNotes.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                    ?: "Loading release notes for v$version…"
                if (cachedNotes == null) {
                    Thread {
                        val exactNotes = ReleaseNotesCache.fetchNotes(this, version)
                            ?: ReleaseChangelog.fetch(version)
                        runOnUiThread {
                            if (isFinishing || isDestroyed || latestVersion != version) return@runOnUiThread
                            if (!exactNotes.isNullOrBlank()) {
                                ReleaseNotesCache.saveNotes(this, version, exactNotes)
                                notes.text = exactNotes
                            } else if (notes.text.isNullOrBlank() || notes.text.startsWith("Loading release notes")) {
                                notes.text = "The changelog for v$version is unavailable. Open the official release to review its details."
                            }
                        }
                    }.start()
                }

                val verifiedAsset = VerifiedReleaseApk.fromRelease(result, version)
                verifiedApk = verifiedAsset
                apkUrl = verifiedAsset?.url
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
        val release = verifiedApk ?: run {
            Toast.makeText(this, "No verified APK download is available yet. Check for updates again.", Toast.LENGTH_LONG).show()
            return
        }
        downloadButton.isEnabled = false
        downloadButton.text = "Verifying download…"
        VerifiedUpdateInstaller.downloadAndInstall(this, release) { message ->
            if (isFinishing || isDestroyed) return@downloadAndInstall
            status.text = message
            val percent = Regex("(\\d{1,3})%").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (percent != null) {
                downloadProgress.visibility = View.VISIBLE
                downloadProgress.progress = percent.coerceIn(0, 100)
                statusRing.state = "downloading"
            } else if (message.contains("Downloading", ignoreCase = true)) {
                downloadProgress.visibility = View.VISIBLE
                statusRing.state = "downloading"
            } else {
                downloadProgress.visibility = View.GONE
                downloadProgress.progress = 0
                if (message.contains("verified", ignoreCase = true)) statusRing.state = "ready"
            }
            val downloading = message.contains("Downloading update", ignoreCase = true)
            downloadButton.isEnabled = !downloading
            downloadButton.text = if (downloading) {
                if (percent != null) "Downloading… $percent%" else "Downloading…"
            } else "Download v${release.version}"
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

private class StatusRingView(context: android.content.Context) : View(context) {
    var state: String = "up"
        set(value) { field = value; invalidate() }
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val cx = width / 2f; val cy = height / 2f
        val radius = minOf(width, height) * .32f
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 9f*d
        paint.strokeCap = android.graphics.Paint.Cap.ROUND
        paint.color = 0x3889945A
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = if (state == "error") 0xFFB3261E.toInt() else 0xFF89945A.toInt()
        if (state == "checking" || state == "downloading") {
            canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, -90f, 235f, false, paint)
        } else if (state == "available") {
            canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, -90f, 125f, false, paint)
            val p = android.graphics.Path().apply { moveTo(cx,cy-18*d); lineTo(cx,cy+15*d); moveTo(cx-12*d,cy+4*d); lineTo(cx,cy+16*d); lineTo(cx+12*d,cy+4*d) }
            canvas.drawPath(p,paint)
        } else {
            canvas.drawCircle(cx,cy,radius,paint)
            val p = android.graphics.Path().apply { moveTo(cx-19*d,cy); lineTo(cx-5*d,cy+14*d); lineTo(cx+22*d,cy-17*d) }
            canvas.drawPath(p,paint)
        }
    }
}
private class WavyProgressView(context: android.content.Context) : View(context) {
    var progress: Int = 0
        set(value) { field = value.coerceIn(0,100); invalidate() }
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val cy = height / 2f
        if (w <= 0f) return
        val d = resources.displayMetrics.density
        val amplitude = 4f * d
        val path = android.graphics.Path()
        val end = w * progress / 100f
        for (x in 0..w.toInt() step 3) {
            val y = cy + kotlin.math.sin(x / (22f * d)) * amplitude
            if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
        }
        paint.color = 0x3889945A
        canvas.drawPath(path, paint)
        if (progress > 0) {
            val filled = android.graphics.Path()
            for (x in 0..end.toInt() step 3) {
                val y = cy + kotlin.math.sin(x / (22f * d)) * amplitude
                if (x == 0) filled.moveTo(x.toFloat(), y) else filled.lineTo(x.toFloat(), y)
            }
            paint.color = 0xFF89945A.toInt()
            canvas.drawPath(filled, paint)
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawCircle(end, cy + kotlin.math.sin(end / (22f * d)) * amplitude, 5f*d, paint)
            paint.style = android.graphics.Paint.Style.STROKE
        }
    }
}
