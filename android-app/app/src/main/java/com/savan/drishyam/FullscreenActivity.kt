package com.savan.drishyam

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import org.json.JSONObject

class FullscreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)
        animatePageEntrance(findViewById(android.R.id.content))

        val payload = intent.getStringExtra("p")
        val p = try {
            if (payload.isNullOrBlank()) throw IllegalArgumentException("Missing photo data")
            Photo.from(JSONObject(payload))
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn’t open this photo in full screen.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val image = findViewById<ImageView>(R.id.fullscreenImage)
        Glide.with(this).load(p.full).thumbnail(Glide.with(this).load(p.thumb)).fitCenter().into(image)
        val close = findViewById<View>(R.id.closeFullscreen)
        close.pressFx()
        close.setOnClickListener { finish() }
        image.setOnClickListener {
            if (!motionEnabled(this)) {
                close.visibility = if (close.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            } else if (close.visibility == View.VISIBLE) {
                close.animate().cancel()
                close.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(130L)
                    .withEndAction {
                        close.visibility = View.GONE
                        close.alpha = 1f
                        close.scaleX = 1f
                        close.scaleY = 1f
                    }.start()
            } else {
                close.animate().cancel()
                close.alpha = 0f
                close.scaleX = 0.92f
                close.scaleY = 0.92f
                close.visibility = View.VISIBLE
                close.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.3f)).start()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }
}
