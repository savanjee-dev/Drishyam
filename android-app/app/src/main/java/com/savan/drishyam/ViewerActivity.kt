package com.savan.drishyam

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import org.json.JSONObject

class ViewerActivity : AppCompatActivity() {
    private lateinit var p: Photo
    private lateinit var like: ImageView
    private lateinit var bookmark: ImageView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        animatePageEntrance(findViewById(android.R.id.content))

        val payload = intent.getStringExtra("p")
        p = try {
            if (payload.isNullOrBlank()) throw IllegalArgumentException("Missing photo data")
            Photo.from(JSONObject(payload))
        } catch (_: Exception) {
            toast("Couldn’t open this photo. Please try another image.")
            finish()
            return
        }

        val viewerSource = findViewById<View>(R.id.viewerSource)
        // The photo is static, so the glass refreshes slowly and saves battery.
        bindGlass(findViewById(R.id.viewerOverlay), viewerSource, 250)

        val iv = findViewById<ImageView>(R.id.full)
        Glide.with(this).load(p.full)
            .thumbnail(Glide.with(this).load(p.thumb).disallowHardwareConfig())
            .disallowHardwareConfig().centerCrop().into(iv)
        iv.setOnClickListener { openFullscreen() }

        findViewById<View>(R.id.expand).apply { pressFx(); setOnClickListener { openFullscreen() } }
        findViewById<View>(R.id.back).apply { pressFx(); setOnClickListener { finish() } }
        findViewById<View>(R.id.shareTop).apply { pressFx(); setOnClickListener { sharePhoto() } }

        findViewById<TextView>(R.id.credit).text = p.credit.ifBlank { getString(R.string.unknown_photographer) }
        findViewById<TextView>(R.id.published).apply {
            text = p.published
            visibility = if (p.published.isBlank()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.titleDetail).text = p.title

        // One tidy line of details, showing only what the source actually provides.
        val parts = listOf(
            p.camera, p.aperture, p.focalLength, p.shutterSpeed,
            if (p.iso.isNotBlank()) "ISO ${p.iso}" else "",
            if (p.width > 0 && p.height > 0) "${p.width} × ${p.height}" else ""
        ).filter { it.isNotBlank() }
        findViewById<TextView>(R.id.meta).apply {
            text = parts.joinToString("  ·  ")
            visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        }

        findViewById<TextView>(R.id.sourceLink).setOnClickListener {
            val url = p.link.ifBlank { p.full }
            if (url.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        like = findViewById(R.id.like)
        bookmark = findViewById(R.id.bookmark)
        like.pressFx()
        bookmark.pressFx()
        like.setOnClickListener { Store.toggle(this, p); refreshLike() }
        bookmark.setOnClickListener { Store.toggleBookmark(this, p); refreshBookmark() }

        findViewById<View>(R.id.dl).apply { pressFx(); setOnClickListener { fetchBitmap { saveToGallery(it) } } }
        findViewById<View>(R.id.setwp).apply { pressFx(); setOnClickListener { chooseWallpaperTarget(viewerSource) } }

        refreshLike()
        refreshBookmark()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!PopupStack.dismiss()) super.onBackPressed()
    }

    private fun chooseWallpaperTarget(source: View) {
        choicePopup(
            this, source, getString(R.string.wallpaper_on),
            listOf(getString(R.string.home_screen), getString(R.string.lock_screen), getString(R.string.both_screens))
        ) { which ->
            val flag = when (which) {
                0 -> WallpaperManager.FLAG_SYSTEM
                1 -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            fetchBitmap { setWallpaper(it, flag) }
        }
    }

    private fun openFullscreen() {
        startActivity(Intent(this, FullscreenActivity::class.java).putExtra("p", p.toJson().toString()))
    }

    private fun sharePhoto() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, p.title + "\n" + (p.link.ifEmpty { p.full }) + "\n" + getString(R.string.share_via))
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    private fun refreshLike() {
        val liked = Store.isFav(this, p.id)
        like.setHeart(liked)
        like.contentDescription = getString(if (liked) R.string.unlike_desc else R.string.like_desc)
    }

    private fun refreshBookmark() {
        val saved = Store.isBookmarked(this, p.id)
        bookmark.imageTintList = ColorStateList.valueOf(if (saved) 0xFF9D7BFF.toInt() else 0xFFFFFFFF.toInt())
        bookmark.contentDescription = getString(if (saved) R.string.unbookmark_desc else R.string.bookmark_desc)
    }

    private fun fetchBitmap(done: (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        toast(getString(R.string.preparing))
        if (p.dl.isNotEmpty()) Thread { Api.ping(p.dl) }.start()
        Thread {
            try {
                val bmp = Glide.with(applicationContext).asBitmap().load(p.full).submit(1440, 2960).get()
                runOnUiThread { busy = false; done(bmp) }
            } catch (e: Exception) {
                runOnUiThread { busy = false; toast(getString(R.string.download_fail)) }
            }
        }.start()
    }

    private fun setWallpaper(bmp: Bitmap, flag: Int) {
        Thread {
            try {
                WallpaperManager.getInstance(this).setBitmap(bmp, null, true, flag)
                runOnUiThread { toast(getString(R.string.wallpaper_set)) }
            } catch (e: Exception) {
                runOnUiThread { toast("Couldn’t set wallpaper: ${e.message ?: "system error"}") }
            }
        }.start()
    }

    private fun saveToGallery(bmp: Bitmap) {
        try {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Drishyam_" + System.currentTimeMillis() + ".jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Drishyam")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
            val stream = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Unable to open destination")
            val ok = stream.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            if (!ok) { contentResolver.delete(uri, null, null); throw IllegalStateException("Image encoding failed") }
            toast(getString(R.string.saved_pictures))
        } catch (e: Exception) {
            toast("Couldn’t save image: ${e.message ?: "storage error"}")
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
