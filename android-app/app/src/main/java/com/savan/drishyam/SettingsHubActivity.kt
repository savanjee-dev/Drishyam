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
    private var appearanceValueView: TextView? = null
    private var lowFxEnabled: Boolean = false
    private var reducedMotionEnabled: Boolean = false

    override fun onResume() {
        super.onResume()
        refreshCacheSize()
        appearanceValueView?.text = currentThemeLabel()
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
        lowFxEnabled = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("low_fx", false)
        reducedMotionEnabled = (!motionEnabled(this) && !lowFxEnabled) ||
            getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("motion_reduced", false) || systemReducedMotion()
        addS1AmbientGlows(ambient)
        page.addView(ambient, android.widget.FrameLayout.LayoutParams(-1, -1))
        page.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(page)
        installScrollBounce(scroll)

        val back = android.widget.FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.glassFill))
                setStroke(1.dp(), ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke))
            }
            layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_desc)
            attachPress(glowable = true, lowFx = lowFxEnabled, reduced = reducedMotionEnabled)
            setOnClickListener { finish() }
            addView(BackArrowView(this@SettingsHubActivity, ContextCompat.getColor(this@SettingsHubActivity, R.color.fg)),
                android.widget.FrameLayout.LayoutParams(22.dp(), 22.dp(), Gravity.CENTER))
        }
        root.addView(back)

        val eyebrow = addText(root, "Your space, your way".uppercase(), 11f, R.color.accent, 22, 6)
        eyebrow.typeface = ResourcesCompat.getFont(this, R.font.montserrat_semibold)
        eyebrow.letterSpacing = 0.16f
        val heading = addText(root, "Settings", 38f, R.color.fg, 6, 4)
        heading.typeface = ResourcesCompat.getFont(this, R.font.poppins_extrabold)
        heading.letterSpacing = -0.02f
        heading.setLineSpacing(0f, 1.1f)
        addText(root, "Make Drishyam feel like yours.", 14f, R.color.mute, 0, 6)

        val hero = MaterialCardView(this).apply {
            radius = 30.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke)
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(ContextCompat.getColor(this@SettingsHubActivity, R.color.hero),
                    ContextCompat.getColor(this@SettingsHubActivity, R.color.glassFill))
            ).apply { cornerRadius = 30.dp().toFloat(); setStroke(1.dp(), ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke)) }
            setContentPadding(22.dp(), 22.dp(), 22.dp(), 22.dp())
        }
        val heroBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = addText(heroBody, "DRISHYAM  /  MOSS & OLIVE", 11f, R.color.accent, 0, 10)
        brand.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        brand.letterSpacing = 0.16f
        val heroTitle = addText(heroBody, "A more expressive way to see.", 24f, R.color.fg, 8, 8)
        heroTitle.typeface = ResourcesCompat.getFont(this, R.font.poppins_bold)
        heroTitle.setLineSpacing(0f, 1.2f)
        addText(heroBody, "Shape your space, tune the details, keep the focus on photography.", 13f, R.color.mute, 0, 0).apply { setLineSpacing(2.dp().toFloat(), 1.5f) }
        val version = addText(heroBody, "VERSION ${BuildConfig.VERSION_NAME}  ·  BY SAVAN", 10f, R.color.mute, 14, 0)
        version.letterSpacing = 0.06f
        hero.addView(heroBody)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18.dp() })

        val section = addText(root, "PERSONALISE", 11f, R.color.accent, 28, 12)
        section.typeface = ResourcesCompat.getFont(this, R.font.montserrat_semibold)
        section.letterSpacing = 0.14f
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(tile("appearance", "Appearance", currentThemeLabel()) {
            startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", "appearance"))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 6.dp() })
        row1.addView(tile("browsing", "Browsing", "Hot · India on") {
            startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", "preferences"))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 6.dp() })
        tiles.addView(row1, LinearLayout.LayoutParams(-1, -2))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2.addView(tile("storage", "Storage", "0.0 MB cached") {
            startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", "storage"))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 6.dp() })
        row2.addView(tile("about", "About", "Version ${BuildConfig.VERSION_NAME}") {
            startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", "about"))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 6.dp() })
        tiles.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp() })
        root.addView(tiles)

        val quick = addText(root, "QUICK ACTIONS", 11f, R.color.accent, 28, 12)
        quick.typeface = ResourcesCompat.getFont(this, R.font.montserrat_semibold)
        quick.letterSpacing = 0.14f
        addAction(root, "updates", "Check for updates", "Find the latest verified release") {
            startActivity(Intent(this, UpdatesActivity::class.java))
        }
        addAction(root, "whatsnew", "What’s new", "Release notes, release by release") {
            startActivity(Intent(this, WhatsNewActivity::class.java))
        }

        val more = addText(root, "MORE", 11f, R.color.accent, 28, 12)
        more.typeface = ResourcesCompat.getFont(this, R.font.montserrat_semibold)
        more.letterSpacing = 0.14f
        addOurStory(root)
        addText(root, "Made with curiosity.\nBuilt for photography.", 18f, R.color.fg, 26, 0).apply {
            gravity = Gravity.CENTER
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.pacifico_regular)
            setLineSpacing((-2).dp().toFloat(), 1f)
        }
        staggerIn((0 until root.childCount).map { root.getChildAt(it) }, resources.displayMetrics.density, enabled = motionEnabled(this) && !reducedMotionEnabled)
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
                "Browsing" -> repeat(3) { i -> addView(View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 999.dp().toFloat(); setColor(if (i == 0) 0xFF89945A.toInt() else 0x3889945A) } }, LinearLayout.LayoutParams(34.dp(), 14.dp()).apply { rightMargin = if (i < 2) 6.dp() else 0 }) }
                "Storage" -> {
                    val track = android.widget.FrameLayout(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 999.dp().toFloat(); setColor(0x3889945A) } }
                    val fill = View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 999.dp().toFloat(); setColor(0xFF89945A.toInt()) } }
                    track.addView(fill, android.widget.FrameLayout.LayoutParams(0, -1, Gravity.START))
                    track.post { fill.layoutParams = android.widget.FrameLayout.LayoutParams((track.width * 0.08f).toInt(), -1, Gravity.START) }
                    addView(track, LinearLayout.LayoutParams(-1, 8.dp()))
                }
                else -> addView(View(this@SettingsHubActivity).apply { background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFD0C79A.toInt(), 0xFF414D2D.toInt())).apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setStroke(3.dp(), 0x3889945A) } }, LinearLayout.LayoutParams(26.dp(), 26.dp()))
            }
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, 26.dp()).apply { topMargin = 12.dp() })
        body.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
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
            if (title == "Appearance") appearanceValueView = this
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
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.poppins_semibold)
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
            minimumHeight = 250.dp()
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            background = storyBackground
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
                if (motionEnabled(this@SettingsHubActivity) && !reducedMotionEnabled && !lowFxEnabled && !gradientAnimator.isStarted) gradientAnimator.start()
            }
            override fun onViewDetachedFromWindow(v: View) {
                gradientAnimator.cancel()
            }
        })
        val body = android.widget.FrameLayout(this).apply {
            setPadding(22.dp(), 22.dp(), 22.dp(), 22.dp())
            clipChildren = false
            clipToPadding = false
        }
        val cardWidthPx = (resources.displayMetrics.widthPixels - 40.dp()).coerceAtLeast(280.dp())
        val innerWidthPx = cardWidthPx - 44.dp()
        // Reserve a compact portrait column while giving the headline enough width to avoid excessive wrapping.
        val copyWidthPx = minOf((innerWidthPx * 0.60f).toInt(), innerWidthPx - 126.dp()).coerceAtLeast(150.dp())
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply {
            text = "OUR STORY"
            textSize = 10f
            letterSpacing = 0.16f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_bold)
            setTextColor(0xCCFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999.dp().toFloat()
                setColor(0x33FFFFFF)
            }
            setPadding(11.dp(), 6.dp(), 11.dp(), 6.dp())
        })
        copy.addView(TextView(this).apply {
            text = "The people & ideas behind Drishyam"
            textSize = 20f
            maxLines = 3
            includeFontPadding = false
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_extrabold)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 10.dp(), 0, 6.dp())
            setLineSpacing(0f, 1.15f)
            maxWidth = copyWidthPx
        })
        copy.addView(TextView(this).apply {
            text = "Started by one curious photographer, one frame at a time."
            textSize = 12f
            maxLines = 3
            setTextColor(0xE6FFFFFF.toInt())
            maxWidth = copyWidthPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            setLineSpacing(2.dp().toFloat(), 1.5f)
        })
        val storyButton = TextView(this).apply {
            text = "Read the story →"
            textSize = 12f
            maxLines = 1
            includeFontPadding = false
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_bold)
            setTextColor(0xFF26301C.toInt())
            gravity = Gravity.CENTER
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 999.dp().toFloat()
                setColor(android.graphics.Color.WHITE)
            }
            elevation = 8.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = 10.dp() }
        }
        copy.addView(storyButton)
        body.addView(copy, android.widget.FrameLayout.LayoutParams(copyWidthPx, -2, Gravity.TOP or Gravity.START))

        // Soft radial halo behind the existing developer portrait, preserving R.drawable.savan.
        val halo = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x80D0C79A.toInt(), 0x40D0C79A, 0x00D0C79A)
            ).apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
            alpha = 0.75f
            // Respect both reduced-motion and Glass Effects Off preferences.
            if (motionEnabled(this@SettingsHubActivity) && !reducedMotionEnabled && !lowFxEnabled) {
                post {
                    fun pulseHalo() {
                        animate().alpha(1f).scaleX(1.08f).scaleY(1.08f).setDuration(1400).withEndAction {
                            animate().alpha(0.35f).scaleX(0.94f).scaleY(0.94f).setDuration(1400).withEndAction {
                                if (isAttachedToWindow && motionEnabled(this@SettingsHubActivity) && !reducedMotionEnabled && !lowFxEnabled) pulseHalo()
                            }.start()
                        }.start()
                    }
                    pulseHalo()
                }
            }
        }
        body.addView(halo, android.widget.FrameLayout.LayoutParams(136.dp(), 136.dp(),
            Gravity.BOTTOM or Gravity.END).apply { rightMargin = (-46).dp(); bottomMargin = 2.dp() })

        val portrait = android.widget.FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0x33D0C79A)
            }
            clipChildren = false
            clipToPadding = false
        }
        val photo = FadedCircleImage(this).apply {
            setImageResource(R.drawable.developer_photo)
            contentDescription = "Savan, lead developer"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setStroke(3.dp(), 0xB3FFFFFF.toInt())
            }
            clipToOutline = true
        }
        portrait.addView(photo, android.widget.FrameLayout.LayoutParams(108.dp(), 108.dp(), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val photoTag = TextView(this).apply {
            text = "LEAD DEVELOPER"
            textSize = 8.5f
            letterSpacing = 0.08f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_bold)
            setTextColor(0xFF1B2117.toInt())
            setPadding(9.dp(), 4.dp(), 9.dp(), 4.dp())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999.dp().toFloat()
                setColor(android.graphics.Color.WHITE)
            }
            elevation = 4.dp().toFloat()
        }
        portrait.addView(photoTag, android.widget.FrameLayout.LayoutParams(-2, -2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (-4).dp() })
        body.addView(portrait, android.widget.FrameLayout.LayoutParams(116.dp(), 128.dp(),
            Gravity.BOTTOM or Gravity.END).apply { rightMargin = 0; bottomMargin = 0 })
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
        body.addView(sheen, android.widget.FrameLayout.LayoutParams(1, 1,
            Gravity.TOP or Gravity.START).apply { leftMargin = (-24).dp(); topMargin = (-24).dp() })
        val sheenAnimator = android.animation.ObjectAnimator.ofFloat(sheen, View.TRANSLATION_X, 0f, 1f).apply {
            duration = 3800L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.RESTART
            interpolator = S1Fx.easeInOut
        }
        sheen.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                card.post {
                    val params = sheen.layoutParams as android.widget.FrameLayout.LayoutParams
                    params.width = (card.width * 0.4f).toInt()
                    params.height = body.height
                    params.leftMargin = (-24).dp()
                    params.topMargin = (-24).dp()
                    params.bottomMargin = (-24).dp()
                    sheen.layoutParams = params
                    sheen.translationX = -0.6f * card.width
                    sheenAnimator.setFloatValues(-0.6f * card.width, 1.2f * card.width)
                    if (motionEnabled(this@SettingsHubActivity) && !reducedMotionEnabled && !lowFxEnabled && !sheenAnimator.isStarted) sheenAnimator.start()
                }
            }
            override fun onViewDetachedFromWindow(v: View) { sheenAnimator.cancel() }
        })
        val sparkles = SparkleView(this, 0xD9FFFFFF.toInt()).apply {
            visibility = if (lowFxEnabled) View.GONE else View.VISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        body.addView(sparkles, android.widget.FrameLayout.LayoutParams(16.dp(), 16.dp(),
            Gravity.TOP or Gravity.END).apply { topMargin = (-4).dp(); rightMargin = 40.dp() })
        val spark2 = SparkleView(this, 0xD9FFFFFF.toInt()).apply {
            visibility = if (lowFxEnabled) View.GONE else View.VISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        body.addView(spark2, android.widget.FrameLayout.LayoutParams(11.dp(), 11.dp(),
            Gravity.TOP or Gravity.END).apply { topMargin = 34.dp(); rightMargin = 146.dp() })
        body.bringChildToFront(copy)
        body.bringChildToFront(portrait)
        body.bringChildToFront(sparkles)
        body.bringChildToFront(spark2)
        if (motionEnabled(this) && !reducedMotionEnabled && !lowFxEnabled) {
            sparkles.post { startTwinkle(sparkles, 0L) }
            spark2.post { startTwinkle(spark2, 1200L) }
            storyButton.post { startNudge(storyButton, resources.displayMetrics.density) }
        }

        // Keep the card immediately visible when reduced motion or Glass Effects Off is enabled.
        if (motionEnabled(this) && !reducedMotionEnabled && !lowFxEnabled) {
            body.alpha = 0f
            body.translationY = 16.dp().toFloat()
            body.animate().alpha(1f).translationY(0f)
                .setDuration(420L).setInterpolator(S1Fx.outCurve).start()
            startBob(portrait, resources.displayMetrics.density)
        } else {
            body.alpha = 1f
            body.translationY = 0f
            halo.alpha = 0.75f
            sheen.alpha = if (lowFxEnabled) 0f else 0.8f
            sparkles.visibility = if (lowFxEnabled) View.GONE else View.VISIBLE
            spark2.visibility = if (lowFxEnabled) View.GONE else View.VISIBLE
        }

        card.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(card, LinearLayout.LayoutParams(-1, -2))
    }

    private class BackArrowView(context: android.content.Context, private val tint: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val path = Path().apply {
                moveTo(width * 0.78f, height * 0.5f)
                lineTo(width * 0.22f, height * 0.5f)
                moveTo(width * 0.22f, height * 0.5f)
                lineTo(width * 0.48f, height * 0.24f)
                moveTo(width * 0.22f, height * 0.5f)
                lineTo(width * 0.48f, height * 0.76f)
            }
            canvas.drawPath(path, paint)
        }
    }

    private class PrototypeIconView(context: android.content.Context, private val kind: String, private val tint: Int) : ImageView(context) {
        init {
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = android.content.res.ColorStateList.valueOf(tint)
            setImageResource(when (kind) {
                "appearance" -> R.drawable.ic_tile_appearance
                "browsing" -> R.drawable.ic_tile_browsing
                "storage" -> R.drawable.ic_tile_storage
                "about" -> R.drawable.ic_tile_about
                "updates" -> R.drawable.ic_row_download
                "whatsnew" -> R.drawable.ic_row_sparkle
                else -> R.drawable.ic_row_chevron
            })
        }
    }

    private fun currentThemeLabel(): String {
        val theme = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getString("theme", "System") ?: "System"
        return if (theme.equals("System", true)) "System default" else theme
    }

    private fun systemReducedMotion(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !android.animation.ValueAnimator.areAnimatorsEnabled()) return true
        return try {
            android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) { false }
    }

    private fun addS1AmbientGlows(parent: android.widget.FrameLayout) {
        if (lowFxEnabled) return
        data class Glow(val color: Int, val alpha: Float, val duration: Long, val gravity: Int, val top: Int, val bottom: Int, val left: Int, val right: Int)
        val d = resources.displayMetrics.density
        val glows = listOf(
            Glow(0xFF89945A.toInt(), 0.42f, 12000L, Gravity.TOP or Gravity.START, -70, 0, -90, 0),
            Glow(0xFFD0C79A.toInt(), 0.42f, 14000L, Gravity.TOP or Gravity.END, 240, 0, 0, -110),
            Glow(0xFF414D2D.toInt(), 0.22f, 16000L, Gravity.BOTTOM or Gravity.START, 0, 60, (resources.displayMetrics.widthPixels / d * 0.08f).toInt(), 0)
        )
        glows.forEach { glow ->
            val view = object : View(this) {
                private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = glow.color
                    maskFilter = android.graphics.BlurMaskFilter(60f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    if (Build.VERSION.SDK_INT < 31) canvas.drawCircle(width / 2f, height / 2f, 140f * d, glowPaint)
                }
            }.apply {
                alpha = glow.alpha
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if (Build.VERSION.SDK_INT >= 31) {
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(glow.color) }
                    setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60f * d, 60f * d, android.graphics.Shader.TileMode.CLAMP))
                } else setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            parent.addView(view, android.widget.FrameLayout.LayoutParams((280 * d).toInt(), (280 * d).toInt(), glow.gravity).apply {
                topMargin = (glow.top * d).toInt(); bottomMargin = (glow.bottom * d).toInt()
                leftMargin = (glow.left * d).toInt(); rightMargin = (glow.right * d).toInt()
            })
            if (motionEnabled(this) && !reducedMotionEnabled) {
                val animator = android.animation.ObjectAnimator.ofPropertyValuesHolder(view,
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, 40f * d),
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, 30f * d),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f)).apply {
                    duration = glow.duration; repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE; interpolator = S1Fx.easeInOut
                }
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) { if (!animator.isStarted) animator.start() }
                    override fun onViewDetachedFromWindow(v: View) { animator.cancel() }
                })
            }
        }
    }

    private class SparkleView(context: android.content.Context, tint: Int) : ImageView(context) {
        init {
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = android.content.res.ColorStateList.valueOf(tint)
            setImageResource(R.drawable.ic_sparkle_fill)
        }
    }

    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, top: Int, bottom: Int): TextView {
        val tv = TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, when {
                value == "Settings" -> R.font.poppins_extrabold
                size >= 17f -> R.font.montserrat_bold
                size == 11f -> R.font.montserrat_semibold
                else -> R.font.poppins_regular
            })
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, color))
            setPadding(0, top.dp(), 0, bottom.dp())
        }
        parent.addView(tv)
        return tv
    }
}
