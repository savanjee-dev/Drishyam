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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class UpdateActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var versions: TextView
    private lateinit var notes: TextView
    private lateinit var download: TextView
    private lateinit var retry: TextView
    private var apkUrl: String? = null
    private var latest: String? = null
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ContextCompat.getColor(this@UpdateActivity, R.color.bg)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(20.dp(), 14.dp(), 20.dp(), 10.dp()) }
        header.addView(TextView(this).apply { text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(ContextCompat.getColor(this@UpdateActivity, R.color.fg)); contentDescription = "Back"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(48.dp(), 52.dp()))
        header.addView(TextView(this).apply { text = "Drishyam updates"; textSize = 23f; typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdateActivity, R.font.montserrat_bold); setTextColor(ContextCompat.getColor(this@UpdateActivity, R.color.fg)) })
        root.addView(header)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20.dp(), 12.dp(), 20.dp(), 28.dp()) }
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp()); background = ContextCompat.getDrawable(this@UpdateActivity, R.drawable.glass_card) }
        card.addView(TextView(this).apply { text = "SOFTWARE UPDATE"; textSize = 12f; letterSpacing = 1.2f; setTextColor(ContextCompat.getColor(this@UpdateActivity, R.color.accent)) })
        versions = label("Installed version: " + BuildConfig.VERSION_NAME, 16f)
        card.addView(versions)
        status = label("Checking GitHub Releases…", 14f)
        card.addView(status)
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16.dp() })
        val notesCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp()); background = ContextCompat.getDrawable(this@UpdateActivity, R.drawable.glass_card) }
        notesCard.addView(label("What's new", 19f))
        notes = label("Fetching release notes…", 14f)
        notes.setLineSpacing(4.dp().toFloat(), 1f)
        notesCard.addView(notes)
        body.addView(notesCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18.dp() })
        download = button("Download update") { downloadApk() }
        download.visibility = View.GONE
        body.addView(download, LinearLayout.LayoutParams(-1, 52.dp()).apply { bottomMargin = 10.dp() })
        retry = button("Check again") { checkRelease() }
        body.addView(retry, LinearLayout.LayoutParams(-1, 52.dp()).apply { bottomMargin = 10.dp() })
        body.addView(button("Open GitHub Releases") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/savanjee-dev/Drishyam/releases/latest"))) }
            catch (_: Exception) { Toast.makeText(this, "Couldn't open GitHub Releases.", Toast.LENGTH_LONG).show() }
        }, LinearLayout.LayoutParams(-1, 48.dp()))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        checkRelease()
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(ContextCompat.getColor(this@UpdateActivity, R.color.fg)); setPadding(0, 10.dp(), 0, 0)
    }

    private fun button(value: String, action: () -> Unit) = TextView(this).apply {
        text = value; gravity = Gravity.CENTER; textSize = 15f
        typeface = androidx.core.content.res.ResourcesCompat.getFont(this@UpdateActivity, R.font.poppins_medium)
        setTextColor(ContextCompat.getColor(this@UpdateActivity, R.color.fg))
        background = ContextCompat.getDrawable(this@UpdateActivity, R.drawable.glass_pill)
        isClickable = true; isFocusable = true; pressFx(); setOnClickListener { action() }
    }

    private fun checkRelease() {
        val id = ++generation
        apkUrl = null
        download.visibility = View.GONE
        retry.isEnabled = false; retry.alpha = 0.6f
        status.text = "Checking GitHub Releases…"
        notes.text = "Fetching the latest release notes."
        Thread {
            var release: JSONObject? = null
            var error = "Network request failed"
            try {
                val c = URL("https://api.github.com/repos/savanjee-dev/Drishyam/releases/latest").openConnection() as HttpURLConnection
                c.connectTimeout = 9000; c.readTimeout = 12000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "Drishyam-Android")
                try {
                    if (c.responseCode != 200) throw Exception("GitHub returned HTTP " + c.responseCode)
                    release = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                } finally { c.disconnect() }
            } catch (e: Exception) { error = e.message ?: error }
            val data = release
            val err = error
            runOnUiThread {
                if (isFinishing || isDestroyed || id != generation) return@runOnUiThread
                retry.isEnabled = true; retry.alpha = 1f
                if (data == null) {
                    status.text = "Couldn't check for updates."
                    notes.text = "Check your internet connection and tap Check again. Details: " + err
                    Toast.makeText(this, "Update check failed. You can try again.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val tag = data.optString("tag_name", "").trim()
                val version = tag.removePrefix("v")
                if (version.isBlank() || Regex("\\d+").find(version) == null) {
                    status.text = "GitHub returned an invalid release version."
                    notes.text = "Try again later or open GitHub Releases."
                    return@runOnUiThread
                }
                latest = version
                versions.text = "Installed version: " + BuildConfig.VERSION_NAME + "\nLatest release: " + version
                val cmp = compareVersions(version, BuildConfig.VERSION_NAME)
                status.text = when { cmp > 0 -> "A newer version is available."; cmp == 0 -> "You're up to date."; else -> "You're running a version newer than the latest published release." }
                notes.text = data.optString("body", "").trim().ifBlank { "No release notes were provided." }
                val assets = data.optJSONArray("assets")
                if (cmp > 0 && assets != null) for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").lowercase(Locale.ROOT).endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url").takeIf { it.startsWith("https://") }
                        if (apkUrl != null) break
                    }
                }
                if (cmp > 0 && apkUrl != null) download.visibility = View.VISIBLE
                else if (cmp > 0) notes.append("\n\nRelease found, but no APK asset is attached.")
            }
        }.start()
    }

    private fun compareVersions(a: String, b: String): Int {
        val aa = Regex("\\d+").findAll(a).mapNotNull { it.value.toIntOrNull() }.toList()
        val bb = Regex("\\d+").findAll(b).mapNotNull { it.value.toIntOrNull() }.toList()
        for (i in 0 until maxOf(aa.size, bb.size)) { val x = aa.getOrElse(i) { 0 }; val y = bb.getOrElse(i) { 0 }; if (x != y) return x.compareTo(y) }
        return 0
    }

    private fun downloadApk() {
        val url = apkUrl ?: return Toast.makeText(this, "No update download is available.", Toast.LENGTH_LONG).show()
        val version = latest ?: return
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).setTitle("Drishyam " + version)
                .setDescription("Downloading Drishyam update").setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Drishyam-" + version + ".apk")
            manager.enqueue(request)
            Toast.makeText(this, "Download started. Open the completed download to install.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "Couldn't start download. Open GitHub Releases instead.", Toast.LENGTH_LONG).show() }
    }
}
