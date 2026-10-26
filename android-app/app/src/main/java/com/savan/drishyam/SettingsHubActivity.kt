package com.savan.drishyam

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color
import java.io.File
import java.util.Locale
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView

/**
 * S1 Settings hub. Appearance remains a link to the existing S2 entry point;
 * browsing/storage/about are intentionally prototype toasts until their phases.
 */
class SettingsHubActivity : AppCompatActivity() {
    private var storageValueView: TextView? = null
    private var lowFxEnabled: Boolean = false
    private var reducedMotionEnabled: Boolean = false

    override fun onResume() {
        super.onResume()
        refreshCacheSize()
    }

    private fun refreshCacheSize() {
        fun sizeOf(file: File?): Long {
            if (file == null || !file.exists()) return 0L
            if (file.isFile) return file.length()
            return try { file.listFiles()?.sumOf { sizeOf(it) } ?: 0L } catch (_: SecurityException) { 0L }
        }
        val bytes = sizeOf(cacheDir) + (externalCacheDirs?.sumOf { sizeOf(it) } ?: 0L)
        val mb = bytes / (1024.0 * 1024.0)
        storageValueView?.text = String.format(Locale.US, "%.1f MB cached", if (mb < 0.05) 0.0 else mb)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 150.dp())
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, 12.dp())
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(root)
        }
        val page = android.widget.FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.bg))
            clipChildren = false
        }
        val ambient = android.widget.FrameLayout(this)
        reducedMotionEnabled = !motionEnabled(this)
        lowFxEnabled = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("low_fx", false)
        addAmbientGlows(ambient, this, animate = !reducedMotionEnabled, lowFx = lowFxEnabled)
        page.addView(ambient, android.widget.FrameLayout.LayoutParams(-1, -1))
        page.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(page)
        installScrollBounce(scroll)

        val back = TextView(this).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.fg))
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_pill)
            layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
            isClickable = true
            isFocusable = true
            attachPress(glowable = true, lowFx = lowFxEnabled, reduced = reducedMotionEnabled)
            setOnClickListener { finish() }
            contentDescription = getString(R.string.back_desc)
        }
        root.addView(back)

        val eyebrow = addText(root, "Your space, your way".uppercase(), 11f, R.color.accent, 22, 0)
        eyebrow.letterSpacing = 0.16f
        val heading = addText(root, "Settings", 38f, R.color.fg, 6, 4)
        heading.letterSpacing = -0.02f
        heading.setLineSpacing(0f, 1.1f)
        addText(root, "Make Drishyam feel like yours.", 14f, R.color.mute, 0, 6)

        val hero = MaterialCardView(this).apply {
            radius = 30.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke)
            val darkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                if (darkMode) intArrayOf(0xFF222A1A.toInt(), 0x991B2117.toInt())
                else intArrayOf(0xFFE2E6CF.toInt(), 0x99FFFCF4.toInt())
            ).apply { cornerRadius = 30.dp().toFloat() }
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            setContentPadding(22.dp(), 22.dp(), 22.dp(), 22.dp())
        }
        val heroBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = addText(heroBody, "DRISHYAM  /  MOSS & OLIVE", 11f, R.color.accent, 0, 10)
        brand.letterSpacing = 0.16f
        val heroTitle = addText(heroBody, "A more expressive way to see.", 24f, R.color.fg, 0, 8)
        heroTitle.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        addText(heroBody, "Shape your space, tune the details, keep the focus on photography.", 13f, R.color.mute, 0, 14)
        val version = addText(heroBody, "VERSION 2.7.16  ·  BY SAVAN", 10f, R.color.mute, 0, 0)
        version.letterSpacing = 0.06f
        hero.addView(heroBody)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18.dp() })

        val section = addText(root, "PERSONALISE", 11f, R.color.accent, 28, 12)
        section.letterSpacing = 0.14f
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(tile("appearance", "Appearance", "System") {
            startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", "appearance"))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 6.dp() })
        row1.addView(tile("browsing", "Browsing", "Hot · India on") {
            Toast.makeText(this@SettingsHubActivity, "Browsing page: next prototype step", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = 6.dp() })
        tiles.addView(row1, LinearLayout.LayoutParams(-1, -2))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2.addView(tile("storage", "Storage", "0.0 MB cached") {
            Toast.makeText(this@SettingsHubActivity, "Storage page: next prototype step", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = 6.dp() })
        row2.addView(tile("about", "About", "Version 2.7.16") {
            Toast.makeText(this@SettingsHubActivity, "About page: next prototype step", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = 6.dp() })
        tiles.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp() })
        root.addView(tiles)

        val quick = addText(root, "QUICK ACTIONS", 11f, R.color.accent, 28, 12)
        quick.letterSpacing = 0.14f
        addAction(root, "updates", "Check for updates", "Find the latest verified release") {
            startActivity(Intent(this, UpdatesActivity::class.java))
        }
        addAction(root, "whatsnew", "What’s new", "Release notes, release by release") {
            startActivity(Intent(this, WhatsNewActivity::class.java))
        }

        val more = addText(root, "MORE", 11f, R.color.accent, 28, 12)
        more.letterSpacing = 0.14f
        addOurStory(root)
        addText(root, "Made with curiosity.\\nBuilt for photography.", 18f, R.color.fg, 26, 0).apply {
            gravity = Gravity.CENTER
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.pacifico_regular)
            setLineSpacing(2.dp().toFloat(), 1f)
        }
        staggerIn((0 until root.childCount).map { root.getChildAt(it) }, resources.displayMetrics.density, enabled = motionEnabled(this))
    }

    private fun tile(iconName: String, title: String, value: String, action: () -> Unit): View {
        val card = MaterialCardView(this).apply {
            radius = 30.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke)
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            setCardBackgroundColor(if (lowFxEnabled) ContextCompat.getColor(this@SettingsHubActivity, R.color.surface) else if (night) 0x991B2117.toInt() else 0x99FFFCF4.toInt())
            isClickable = true
            isFocusable = true
            attachPress(glowable = true, lowFx = lowFxEnabled, reduced = reducedMotionEnabled)
            setOnClickListener { action() }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            minimumHeight = 176.dp()
        }
        val iconTile = android.widget.FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(0x3889945A)
            }
            addView(PrototypeIconView(this@SettingsHubActivity, iconName, 0xFF89945A.toInt()),
                android.widget.FrameLayout.LayoutParams(26.dp(), 26.dp(), Gravity.CENTER))
        }
        body.addView(iconTile, LinearLayout.LayoutParams(46.dp(), 46.dp()).apply { bottomMargin = 12.dp() })
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            when (title) {
                "Appearance" -> {
                    addView(View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFF3F1E7.toInt(), 0xFF0A0D08.toInt())).apply { cornerRadius = 9.dp().toFloat(); setStroke(1.dp(), 0x6689945A) } }, LinearLayout.LayoutParams(26.dp(), 26.dp()))
                }
                "Browsing" -> repeat(3) { i -> addView(View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 999.dp().toFloat(); setColor(if (i == 0) 0xFF89945A.toInt() else 0x3889945A) } }, LinearLayout.LayoutParams(34.dp(), 14.dp()).apply { rightMargin = 4.dp() }) }
                "Storage" -> {
                    val track = android.widget.FrameLayout(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 999.dp().toFloat(); setColor(0x3889945A) } }
                    track.addView(View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 999.dp().toFloat(); setColor(0xFF89945A.toInt()) } }, android.widget.FrameLayout.LayoutParams(18.dp(), -1))
                    addView(track, LinearLayout.LayoutParams(-1, 8.dp()))
                }
                else -> addView(View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFD0C79A.toInt(), 0xFF414D2D.toInt())).apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setStroke(3.dp(), 0x3889945A) } }, LinearLayout.LayoutParams(26.dp(), 26.dp()))
            }
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, 26.dp()).apply { topMargin = 12.dp() })
        body.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_bold)
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.fg))
            setPadding(0, 0, 0, 2.dp())
        })
        body.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.mute))
            maxLines = 1
            includeFontPadding = false
            if (title == "Storage") storageValueView = this
        })
        card.addView(body)
        return card
    }

    private fun addAction(root: LinearLayout, glyph: String, title: String, desc: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 15.dp(), 16.dp(), 15.dp())
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_row)
            isClickable = true
            isFocusable = true
            attachPress(glowable = true, lowFx = lowFxEnabled, reduced = reducedMotionEnabled)
            setOnClickListener { action() }
        }
        card.addView(android.widget.FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 16.dp().toFloat(); setColor(0x2689945A) }
            addView(PrototypeIconView(this@SettingsHubActivity, glyph, ContextCompat.getColor(this@SettingsHubActivity, R.color.accent)), android.widget.FrameLayout.LayoutParams(26.dp(), 26.dp(), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(46.dp(), 46.dp())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14.dp(), 0, 0, 0) }
        copy.addView(TextView(this).apply {
            text = title; textSize = 15f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_semibold)
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.fg))
        })
        copy.addView(TextView(this).apply {
            text = desc; textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.mute))
            setPadding(0, 4.dp(), 0, 0)
        })
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(PrototypeIconView(this, "chevron", ContextCompat.getColor(this, R.color.mute)), LinearLayout.LayoutParams(20.dp(), 20.dp()))
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14.dp(); bottomMargin = 10.dp() })
    }

    private fun addOurStory(root: LinearLayout) {
        val storyBackground = StoryFlowDrawable(resources.displayMetrics.density)
        val card = MaterialCardView(this).apply {
            radius = 34.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 0
            minimumHeight = 214.dp()
            background = storyBackground
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            attachPress(glowable = true, lowFx = lowFxEnabled, reduced = reducedMotionEnabled)
            setOnClickListener {
                startActivity(Intent(this@SettingsHubActivity, MainActivity::class.java).putExtra("open_about", true))
            }
        }
        // Smoothly shift all four gradient stops on a 9-second ping-pong loop.
        // This animates the actual card gradient, not just a separate overlay.
        val gradientAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9000L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = S1Fx.easeInOut
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                storyBackground.progress = fraction
                storyBackground.invalidateSelf()
                card.invalidate()
            }
        }
        card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (motionEnabled(this@SettingsHubActivity) && !lowFxEnabled && !gradientAnimator.isStarted) gradientAnimator.start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                gradientAnimator.cancel()
            }
        })
        val body = android.widget.FrameLayout(this).apply {
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Keep the heading clear of the portrait; the copy column is constrained to 48% of screen width.
        }
        copy.addView(TextView(this).apply {
            text = "OUR STORY"
            textSize = 10f
            letterSpacing = 0.16f
            setTextColor(0xCCFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999.dp().toFloat()
                setColor(0x33FFFFFF)
            }
            setPadding(11.dp(), 6.dp(), 11.dp(), 6.dp())
        })
        copy.addView(TextView(this).apply {
            text = "The people & ideas behind Drishyam"
            textSize = 24f
            maxLines = 3
            includeFontPadding = false
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_extrabold)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 14.dp(), 0, 8.dp())
            maxWidth = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density - 40f) * 0.58f * resources.displayMetrics.density).toInt()
        })
        copy.addView(TextView(this).apply {
            text = "Started by one curious photographer, one frame at a time."
            textSize = 13f
            setTextColor(0xE6FFFFFF.toInt())
            maxWidth = (resources.displayMetrics.widthPixels - 238.dp()).coerceAtLeast(100.dp())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            setLineSpacing(2.dp().toFloat(), 1.35f)
        })
        val storyButton = TextView(this).apply {
            text = "Read the story →"
            textSize = 13f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_semibold)
            setTextColor(0xFF26301C.toInt())
            gravity = Gravity.CENTER
            setPadding(18.dp(), 12.dp(), 18.dp(), 12.dp())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 999.dp().toFloat()
                setColor(android.graphics.Color.WHITE)
            }
            elevation = 8.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 22.dp() }
        }
        copy.addView(storyButton)
        body.addView(copy, android.widget.FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels - 238.dp()).coerceAtLeast(100.dp()), -2, Gravity.TOP or Gravity.START
        ))

        // Soft radial halo behind the existing developer portrait, preserving R.drawable.savan.
        val halo = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x80D0C79A.toInt(), 0x40D0C79A, 0x00D0C79A)
            ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
            alpha = 0.75f
            // Respect both reduced-motion and Glass Effects Off preferences.
            if (motionEnabled(this@SettingsHubActivity) && !lowFxEnabled) {
                post {
                    fun pulseHalo() {
                        animate().alpha(1f).scaleX(1.08f).scaleY(1.08f).setDuration(1400).withEndAction {
                            animate().alpha(0.35f).scaleX(0.94f).scaleY(0.94f).setDuration(1400).withEndAction {
                                if (isAttachedToWindow && motionEnabled(this@SettingsHubActivity) && !lowFxEnabled) pulseHalo()
                            }.start()
                        }.start()
                    }
                    pulseHalo()
                }
            }
        }
        body.addView(halo, android.widget.FrameLayout.LayoutParams(160.dp(), 160.dp(),
            Gravity.BOTTOM or Gravity.END).apply { rightMargin = (-8).dp(); bottomMargin = 8.dp() })

        val portrait = android.widget.FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33D0C79A)
            }
            clipChildren = false
            clipToPadding = false
        }
        val photo = FadedCircleImage(this).apply {
            setImageResource(R.drawable.savan)
            contentDescription = "Savan, lead developer"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setStroke(3.dp(), 0xB3FFFFFF.toInt())
            }
            clipToOutline = true
        }
        portrait.addView(photo, android.widget.FrameLayout.LayoutParams(132.dp(), 132.dp(), Gravity.CENTER))
        val photoTag = TextView(this).apply {
            text = "LEAD DEVELOPER"
            textSize = 8.5f
            letterSpacing = 0.08f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_semibold)
            setTextColor(0xFF1B2117.toInt())
            setPadding(9.dp(), 4.dp(), 9.dp(), 4.dp())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999.dp().toFloat()
                setColor(android.graphics.Color.WHITE)
            }
            elevation = 4.dp().toFloat()
        }
        portrait.addView(photoTag, android.widget.FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 18.dp() })
        body.addView(portrait, android.widget.FrameLayout.LayoutParams(140.dp(), 140.dp(),
            Gravity.BOTTOM or Gravity.END).apply { rightMargin = (-8).dp(); bottomMargin = (-16).dp() })
        // A diagonal sheen and two restrained sparkle marks complete the story-card treatment.
        val sheen = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF)
            )
            rotation = -18f
            alpha = 0.8f
            isClickable = false
        }
        body.addView(sheen, android.widget.FrameLayout.LayoutParams(42.dp(), 360.dp(),
            Gravity.TOP or Gravity.START).apply { leftMargin = (-70).dp() })
        val sheenAnimator = android.animation.ObjectAnimator.ofFloat(
            sheen, View.TRANSLATION_X, (-70).dp().toFloat(), 420.dp().toFloat()
        ).apply {
            duration = 3800L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            interpolator = S1Fx.easeInOut
        }
        sheen.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (motionEnabled(this@SettingsHubActivity) && !lowFxEnabled && !sheenAnimator.isStarted) sheenAnimator.start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                sheenAnimator.cancel()
            }
        })
        val sparkles = TextView(this).apply {
            text = "✦  ·"
            textSize = 16f
            letterSpacing = 0.08f
            setTextColor(0xD9FFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        body.addView(sparkles, android.widget.FrameLayout.LayoutParams(24.dp(), 24.dp(),
            Gravity.TOP or Gravity.END).apply { topMargin = 20.dp(); rightMargin = 64.dp() })
        val spark2 = TextView(this).apply {
            text = "✦"
            textSize = 11f
            setTextColor(0xD9FFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        body.addView(spark2, android.widget.FrameLayout.LayoutParams(18.dp(), 18.dp(),
            Gravity.TOP or Gravity.END).apply { topMargin = 58.dp(); rightMargin = 170.dp() })
        body.bringChildToFront(copy)
        body.bringChildToFront(portrait)
        body.bringChildToFront(sparkles)
        body.bringChildToFront(spark2)
        if (motionEnabled(this) && !lowFxEnabled) {
            sparkles.post { startTwinkle(sparkles, 0L) }
            spark2.post { startTwinkle(spark2, 1200L) }
            storyButton.post { startNudge(storyButton, resources.displayMetrics.density) }
        }

        // Keep the card immediately visible when reduced motion or Glass Effects Off is enabled.
        if (motionEnabled(this) && !lowFxEnabled) {
            body.alpha = 0f
            body.translationY = 16.dp().toFloat()
            body.animate().alpha(1f).translationY(0f)
                .setDuration(420L).setInterpolator(S1Fx.outCurve).start()
            startBob(portrait, resources.displayMetrics.density)
        } else {
            body.alpha = 1f
            body.translationY = 0f
            halo.alpha = 0.75f
            sheen.alpha = 0f
            sparkles.alpha = 1f
            spark2.alpha = 1f
        }

        card.addView(body)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp() })
    }

    private class PrototypeIconView(context: android.content.Context, private val kind: String, private val tint: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = 2.dp().toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val sx = width / 24f; val sy = height / 24f
            canvas.save(); canvas.scale(sx, sy)
            fun line(vararg pts: Float) { val p = Path(); p.moveTo(pts[0], pts[1]); var i=2; while(i<pts.size){ p.lineTo(pts[i],pts[i+1]); i+=2 }; canvas.drawPath(p, paint) }
            when(kind) {
                "appearance" -> { canvas.drawArc(RectF(4f,4f,20f,20f), -90f, 180f, false, paint); line(12f,4f,12f,20f) }
                "browsing" -> { for(y in listOf(6f,12f,18f)){ canvas.drawCircle(5f,y,1f,paint); line(9f,y,19f,y) } }
                "storage" -> { canvas.drawOval(RectF(4f,4f,20f,9f),paint); line(4f,6.5f,4f,17f); line(20f,6.5f,20f,17f); canvas.drawArc(RectF(4f,14.5f,20f,20f),0f,180f,false,paint); canvas.drawArc(RectF(4f,9f,20f,15f),0f,180f,false,paint) }
                "about" -> { canvas.drawCircle(12f,12f,8f,paint); canvas.drawCircle(12f,8f,0.8f,paint); line(12f,11f,12f,16f) }
                "updates" -> { line(12f,3f,12f,15f); line(7f,10f,12f,15f,17f,10f); line(5f,19f,19f,19f) }
                "whatsnew" -> { line(12f,3f,13.5f,9.5f,20f,12f,13.5f,13.5f,12f,20f,10.5f,13.5f,4f,12f,10.5f,9.5f,12f,3f) }
                "chevron" -> { line(9f,6f,15f,12f,9f,18f) }
            }
            canvas.restore()
        }
    }

    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, top: Int, bottom: Int): TextView {
        val tv = TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, when {
                value == "Settings" -> R.font.poppins_extrabold
                size >= 17f -> R.font.montserrat_bold
                size == 11f -> R.font.poppins_semibold
                else -> R.font.poppins_regular
            })
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, color))
            setPadding(0, top.dp(), 0, bottom.dp())
        }
        parent.addView(tv)
        return tv
    }
}
