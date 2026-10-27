package com.savan.drishyam

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * Settings hub (Phase 1). Built to match Drishyam_Settings_Prototype_v9.html.
 * Glass Off = the existing "low_fx" setting. Motion Reduced = the system reduce-motion setting.
 */
class SettingsHubActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Number): Int = (v.toFloat() * density).toInt()
    private fun dpf(v: Number): Float = v.toFloat() * density
    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun font(id: Int) = ResourcesCompat.getFont(this, id)

    private var lowFx = false
    private var reduced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)

        lowFx = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getBoolean("low_fx", false)
        reduced = !motionEnabled(this)
        val animate = !reduced

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(150))
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.bg))
            clipChildren = false
        }
        if (!lowFx) {
            addGlows(root, animate)
            // Ambient layer remains behind content and never intercepts touch events.
            root.addView(SettingsAmbientLayer(this, animate), FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        installScrollBounce(scroll)

        // ---- Back button: plain 48dp circle, no rectangle ----
        val back = TextView(this).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
            setTextColor(color(R.color.fg))
            background = glassCircle()
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_desc)
            attachPress(glowable = true)
            setOnClickListener { finish() }
        }
        content.addView(back)

        // ---- Header ----
        content.addView(tv("YOUR SPACE, YOUR WAY", 11f, R.color.accent, R.font.montserrat_semibold, 22, 6).apply {
            letterSpacing = 0.16f
        })
        content.addView(tv("Settings", 38f, R.color.fg, R.font.poppins_extrabold, 6, 4).apply {
            letterSpacing = -0.02f
        })
        content.addView(tv("Make Drishyam feel like yours.", 14f, R.color.mute, R.font.poppins_regular, 0, 6))

        // ---- Hero card ----
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(color(R.color.hero), color(R.color.glassFill))
            ).apply {
                cornerRadius = dpf(30)
                setStroke(dp(1), color(R.color.glassStroke))
            }
        }
        hero.addView(tv("DRISHYAM  /  MOSS & OLIVE", 11f, R.color.accent, R.font.montserrat_bold).apply {
            letterSpacing = 0.16f
        })
        hero.addView(tv("A more expressive way to see.", 24f, R.color.fg, R.font.poppins_bold, 8, 8))
        hero.addView(tv("Shape your space, tune the details, keep the focus on photography.", 13f, R.color.mute, R.font.poppins_regular))
        hero.addView(tv("VERSION ${BuildConfig.VERSION_NAME}  ·  BY SAVAN", 10f, R.color.mute, R.font.poppins_regular, 14, 0).apply {
            letterSpacing = 0.06f
        })
        content.addView(hero, matchWrap(18))

        // ---- Personalise: tiles ----
        content.addView(label("Personalise"))
        val themeName = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getString("theme", "System") ?: "System"
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(tile(R.drawable.ic_tile_appearance, "Appearance", themeName, appearancePreview()) {
            openSettings("appearance")
        }, tileParams(left = true))
        row1.addView(tile(R.drawable.ic_tile_browsing, "Browsing", "Hot · India on", browsingPreview()) {
            toast("Browsing page: next prototype step")
        }, tileParams(left = false))
        content.addView(row1, matchWrap(0))

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(tile(R.drawable.ic_tile_storage, "Storage", "${cacheLabel()} MB cached", storagePreview()) {
            toast("Storage page: next prototype step")
        }, tileParams(left = true))
        row2.addView(tile(R.drawable.ic_tile_about, "About", "Version ${BuildConfig.VERSION_NAME}", aboutPreview()) {
            toast("About page: next prototype step")
        }, tileParams(left = false))
        content.addView(row2, matchWrap(12))

        // ---- Quick actions (unchanged behaviour) ----
        content.addView(label("Quick actions"))
        content.addView(actionRow(R.drawable.ic_row_download, "Check for updates", "Find the latest verified release") {
            startActivity(Intent(this, UpdatesActivity::class.java))
        })
        content.addView(actionRow(R.drawable.ic_row_sparkle, "What's new", "Release notes, release by release") {
            startActivity(Intent(this, WhatsNewActivity::class.java))
        })

        // ---- More: Our story card ----
        content.addView(label("More"))
        content.addView(ourStoryCard(animate), matchWrap(0))

        // ---- Footer: two Pacifico lines ----
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(26), 0, 0)
        }
        footer.addView(tv("Made with curiosity.", 18f, R.color.fg, R.font.pacifico_regular).apply {
            gravity = Gravity.CENTER
            setShadowLayer(dpf(2), 0f, dpf(1), 0x2E1B2112)
        })
        footer.addView(tv("Built for photography.", 18f, R.color.fg, R.font.pacifico_regular, 2, 0).apply {
            gravity = Gravity.CENTER
            setShadowLayer(dpf(2), 0f, dpf(1), 0x2E1B2112)
        })
        content.addView(footer, matchWrap(0))

        // ---- Entrance: rise 16dp and fade, 40ms apart ----
        if (animate) {
            val items = (0 until content.childCount).map { content.getChildAt(it) }
            items.forEachIndexed { i, v ->
                v.alpha = 0f
                v.translationY = dpf(16)
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * 40L).setDuration(420L)
                    .setInterpolator(SPRING).start()
            }
        }
    }

    // ================= Tiles =================

    private fun tile(icon: Int, title: String, subtitle: String, preview: View, onTap: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(176)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_card)
            isClickable = true
            isFocusable = true
            attachPress(glowable = true)
            setOnClickListener { onTap() }
            addView(iconSquare(icon))
            addView(FrameLayout(this@SettingsHubActivity).apply { addView(preview) },
                LinearLayout.LayoutParams(-1, dp(26)).apply { topMargin = dp(12) })
            addView(View(this@SettingsHubActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(tv(title, 16f, R.color.fg, R.font.montserrat_bold))
            addView(tv(subtitle, 12f, R.color.mute, R.font.poppins_regular, 0, 0).apply { setPadding(0, dp(2), 0, 0) })
        }
    }

    private fun tileParams(left: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (left) rightMargin = dp(6) else leftMargin = dp(6)
        }

    private fun iconSquare(iconRes: Int): FrameLayout {
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(16)
                setColor(withAlpha(color(R.color.accent2), 0.22f))
            }
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        }
    }

    // Previews (prototype: .prev, 26dp tall)
    private fun appearancePreview(): View = View(this).apply {
        background = HubSplitSwatchDrawable(density, color(R.color.accent))
        layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER_VERTICAL or Gravity.START)
    }

    private fun browsingPreview(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (i in 0 until 3) {
            addView(View(this@SettingsHubActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpf(999)
                    setColor(if (i == 0) color(R.color.accent) else withAlpha(color(R.color.accent2), 0.22f))
                }
            }, LinearLayout.LayoutParams(dp(34), dp(14)).apply { if (i > 0) leftMargin = dp(6) })
        }
    }

    private fun storagePreview(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(999)
            setColor(withAlpha(color(R.color.accent2), 0.22f))
        }
        addView(View(this@SettingsHubActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(999)
                setColor(color(R.color.accent))
            }
        }, LinearLayout.LayoutParams(0, dp(8), 0.08f))
        addView(View(this@SettingsHubActivity), LinearLayout.LayoutParams(0, dp(8), 0.92f))
        layoutParams = FrameLayout.LayoutParams(-1, dp(8), Gravity.CENTER_VERTICAL)
    }

    private fun aboutPreview(): View = FrameLayout(this).apply {
        val ring = View(this@SettingsHubActivity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), withAlpha(color(R.color.accent2), 0.22f))
            }
        }
        addView(ring, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        addView(View(this@SettingsHubActivity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(color(R.color.accent2), color(R.color.accent))
            ).apply { shape = GradientDrawable.OVAL }
        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER_VERTICAL or Gravity.START)
    }

    // ================= Quick action rows =================

    private fun actionRow(icon: Int, title: String, subtitle: String, onTap: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_row)
            isClickable = true
            isFocusable = true
            attachPress(glowable = true)
            setOnClickListener { onTap() }
            layoutParams = matchWrap(0).apply { bottomMargin = dp(10) }
            addView(iconSquare(icon))
            val copy = LinearLayout(this@SettingsHubActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                addView(tv(title, 15f, R.color.fg, R.font.poppins_semibold))
                addView(tv(subtitle, 12f, R.color.mute, R.font.poppins_regular, 3, 0))
            }
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(this@SettingsHubActivity).apply {
                setImageResource(R.drawable.ic_row_chevron)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
        }
    }

    // ================= Our story card =================

    private fun ourStoryCard(animate: Boolean): FrameLayout {
        val flowBg = HubFlowGradientDrawable(density, 30f)
        val card = FrameLayout(this).apply {
            minimumHeight = dp(224)
            clipChildren = false
            clipToPadding = false
            background = flowBg
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 0)
            attachPress(glowable = true)
            setOnClickListener {
                startActivity(Intent(this@SettingsHubActivity, MainActivity::class.java).putExtra("open_about", true))
            }
        }

        // Calm, wide cinematic composition: copy on the left, portrait floating on the right.
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(24), dp(4), dp(24))
            clipChildren = false
            clipToPadding = false
        }
        copy.addView(TextView(this).apply {
            text = "OUR STORY"
            textSize = 10f
            letterSpacing = 0.16f
            setTextColor(Color.WHITE)
            typeface = font(R.font.montserrat_bold)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(999)
                setColor(0x26FFFFFF)
                setStroke(dp(1), 0x30FFFFFF)
            }
            setPadding(dp(11), dp(6), dp(11), dp(6))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        copy.addView(TextView(this).apply {
            text = "The people & ideas behind Drishyam"
            textSize = 21f
            setLineSpacing(0f, 1.08f)
            setTextColor(Color.WHITE)
            typeface = font(R.font.montserrat_extrabold)
            setPadding(0, dp(12), dp(2), dp(7))
            breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
        })

        copy.addView(TextView(this).apply {
            text = "Started by one curious photographer, one frame at a time."
            textSize = 12f
            setLineSpacing(0f, 1.28f)
            alpha = 0.92f
            setTextColor(Color.WHITE)
            typeface = font(R.font.poppins_regular)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        })

        val readButton = TextView(this).apply {
            text = "Read the story  →"
            textSize = 12.5f
            setTextColor(0xFF1B2117.toInt())
            typeface = font(R.font.montserrat_bold)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(999)
                setColor(0xF5FFFFFF.toInt())
                setStroke(dp(1), 0x55FFFFFF)
            }
            elevation = dpf(3)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@SettingsHubActivity, MainActivity::class.java).putExtra("open_about", true))
            }
            attachPress(glowable = true)
        }
        copy.addView(readButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })

        card.addView(copy, FrameLayout.LayoutParams(0, -1, Gravity.START).apply {
            width = (resources.displayMetrics.widthPixels * 0.59f).toInt() - dp(20)
        })

        val photoBox = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val halo = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(withAlpha(0xFFD0C79A.toInt(), 0.34f), 0x00D0C79A)
                gradientRadius = dpf(86)
            }
            alpha = 0.88f
        }
        photoBox.addView(halo, FrameLayout.LayoutParams(dp(154), dp(154), Gravity.CENTER))

        val photo = HubFadedCircleImage(this).apply {
            setImageResource(R.drawable.savan)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Savan, lead developer"
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = dpf(5)
        }
        photoBox.addView(photo, FrameLayout.LayoutParams(dp(124), dp(124), Gravity.CENTER))

        photoBox.addView(TextView(this).apply {
            text = "LEAD DEVELOPER"
            textSize = 8f
            letterSpacing = 0.06f
            setTextColor(0xFF1B2117.toInt())
            typeface = font(R.font.montserrat_bold)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(999)
                setColor(0xF5FFFFFF.toInt())
                setStroke(dp(1), 0x55FFFFFF)
            }
            elevation = dpf(6)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(12)
        })

        card.addView(photoBox, FrameLayout.LayoutParams(dp(150), dp(184), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = dp(8)
        })

        if (animate) {
            startFlow(flowBg)
            startBob(photo)
            startHaloPulse(halo)
            startBob(readButton)
        }
        return card
    }

    // ================= Background glows =================

    private fun motionEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        } else true
    }

    private fun installScrollBounce(scroll: ScrollView) {
        scroll.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    private fun addGlows(root: FrameLayout, animate: Boolean) {
        data class G(val colorRes: Int, val alpha: Float, val dur: Long, val gravity: Int, val top: Int, val bottom: Int, val start: Int, val end: Int)
        val list = listOf(
            G(R.color.accent2, 0.52f, 5600, Gravity.TOP or Gravity.START, -70, 0, -90, 0),
            G(R.color.hero, 0.48f, 7200, Gravity.TOP or Gravity.END, 240, 0, 0, -110),
            G(R.color.accent, 0.32f, 9000, Gravity.BOTTOM or Gravity.START, 0, 60, 0, 0)
        )
        for (g in list) {
            val c = color(g.colorRes)
            val v = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(c, withAlpha(c, 0f))
                    gradientRadius = dpf(140)
                }
                alpha = g.alpha
            }
            val lp = FrameLayout.LayoutParams(dp(280), dp(280), g.gravity).apply {
                topMargin = dp(g.top); bottomMargin = dp(g.bottom)
                if ((g.gravity and Gravity.END) == Gravity.END) rightMargin = dp(g.end) else leftMargin = dp(g.start)
            }
            root.addView(v, 0, lp)
            if (animate) {
                ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dpf(86)),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dpf(68)),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.24f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.24f)).apply {
                    duration = g.dur
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = EASE_IN_OUT
                    start()
                }
            }
        }
    }

    // ================= Effects =================

    /** Press: scale 0.92 (90ms) and springback; glow when glowable and Glass is on. */
    private fun View.attachPress(glowable: Boolean) {
        val glow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(0x8089945A.toInt(), 0x0089945A)
            gradientRadius = dpf(90)
        }
        glow.alpha = 0
        val useGlow = glowable && !lowFx && !reduced
        if (useGlow) foreground = glow
        var glowAnim: ValueAnimator? = null
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f)).apply {
                        duration = if (reduced) 0L else 90L
                        interpolator = EASE_OUT
                        start()
                    }
                    if (useGlow) {
                        glow.setGradientCenter(e.x / v.width, e.y / v.height)
                        glowAnim?.cancel()
                        glowAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 450L
                            interpolator = EASE
                            addUpdateListener { a ->
                                val t = a.animatedValue as Float
                                glow.alpha = (255 * t).toInt()
                                glow.gradientRadius = dpf(90) * (0.4f + 0.6f * t)
                            }
                            start()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (useGlow) {
                        glowAnim?.cancel()
                        glowAnim = ValueAnimator.ofInt(glow.alpha, 0).apply {
                            duration = 450L
                            interpolator = EASE
                            addUpdateListener { a -> glow.alpha = a.animatedValue as Int }
                            start()
                        }
                    }
                    if (reduced) { v.scaleX = 1f; v.scaleY = 1f } else springBack(v)
                }
            }
            false
        }
    }

    private fun springBack(v: View) {
        fun keys(name: String) = PropertyValuesHolder.ofKeyframe(name,
            Keyframe.ofFloat(0f, 0.92f), Keyframe.ofFloat(0.45f, 1.06f),
            Keyframe.ofFloat(0.70f, 0.985f), Keyframe.ofFloat(1f, 1f))
        ObjectAnimator.ofPropertyValuesHolder(v, keys("scaleX"), keys("scaleY")).apply {
            duration = 600L
            interpolator = EASE_OUT
            start()
        }
    }

    private fun startFlow(bg: HubFlowGradientDrawable) {
        if (reduced) return
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = EASE_IN_OUT
            addUpdateListener { a -> bg.progress = a.animatedValue as Float }
            start()
        }
    }

    private fun startSheen(sheen: View, card: FrameLayout) {
        if (reduced || lowFx) return
        card.post {
            val w = card.width.toFloat()
            ObjectAnimator.ofFloat(sheen, View.TRANSLATION_X, -0.6f * w, 1.2f * w).apply {
                duration = 3800L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = EASE_IN_OUT
                start()
            }
        }
    }

    private fun startTwinkle(v: View, delay: Long) {
        if (reduced || lowFx) return
        ObjectAnimator.ofPropertyValuesHolder(v,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.25f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1.15f)).apply {
            duration = 2600L
            startDelay = delay
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = EASE_IN_OUT
            start()
        }
    }

    private fun startNudge(v: View) {
        if (reduced) return
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, dpf(5)).apply {
            duration = 1600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = EASE_IN_OUT
            start()
        }
    }

    private fun startBob(photo: View) {
        if (reduced) return
        ObjectAnimator.ofFloat(photo, View.TRANSLATION_Y, -dpf(3), -dpf(17)).apply {
            duration = 2600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = EASE_IN_OUT
            start()
        }
    }

    private fun startHaloPulse(halo: View) {
        if (reduced || lowFx) return
        ObjectAnimator.ofPropertyValuesHolder(halo,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.35f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.94f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.94f, 1.08f)).apply {
            duration = 2800L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = EASE_IN_OUT
            start()
        }
    }

    // ================= Navigation and toast =================

    private fun openSettings(section: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", section))
    }

    private fun toast(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun cacheLabel(): String {
        val bytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return String.format(java.util.Locale.US, "%.1f", bytes / (1024.0 * 1024.0))
    }

    // ================= Helpers =================

    private fun label(text: String): TextView =
        tv(text.uppercase(), 11f, R.color.accent, R.font.montserrat_semibold, 28, 12).apply { letterSpacing = 0.14f }

    private fun tv(text: String, size: Float, colorRes: Int, fontRes: Int, top: Int = 0, bottom: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color(colorRes))
            typeface = font(fontRes)
            setPadding(0, dp(top), 0, dp(bottom))
        }

    private fun matchWrap(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun glassCircle(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color(R.color.glassFill))
        setStroke(dp(1), color(R.color.glassStroke))
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private val SPRING = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
        private val EASE_OUT = PathInterpolator(0f, 0f, 0.58f, 1f)
        private val EASE_IN_OUT = PathInterpolator(0.42f, 0f, 0.58f, 1f)
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}

/** Hard diagonal split (135deg), clipped to a 9dp rounded square with a 1dp border. */
private class HubSplitSwatchDrawable(private val density: Float, private val border: Int) : Drawable() {
    private val radius = 9f * density
    private val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3F1E7.toInt() }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0D08.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = border
    }
    override fun draw(canvas: Canvas) {
        val b = bounds
        val l = b.left.toFloat(); val t = b.top.toFloat(); val r = b.right.toFloat(); val btm = b.bottom.toFloat()
        val clip = Path().apply { addRoundRect(l, t, r, btm, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val topLeft = Path().apply { moveTo(l, t); lineTo(r, t); lineTo(l, btm); close() }
        val bottomRight = Path().apply { moveTo(r, t); lineTo(r, btm); lineTo(l, btm); close() }
        canvas.drawPath(topLeft, light)
        canvas.drawPath(bottomRight, dark)
        canvas.restore()
        canvas.drawPath(clip, stroke)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Sheen band: 40% wide, skewed -18 degrees, soft white gradient. */
private class HubSheenView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val band = w * 0.4f
        canvas.save()
        canvas.skew(-0.3249f, 0f)
        paint.shader = LinearGradient(0f, 0f, band, 0f,
            intArrayOf(0x00FFFFFF, 0x42FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, band, h, paint)
        canvas.restore()
    }
}

/** Developer photo: circle crop with the radial fade mask (50% 44%; 52%, 70%, 90%). */
private class HubFadedCircleImage(context: android.content.Context) : ImageView(context) {
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        super.onDraw(canvas)
        val far = Math.hypot((w * 0.5f).toDouble(), (h * 0.56f).toDouble()).toFloat()
        mask.shader = RadialGradient(w * 0.5f, h * 0.44f, far,
            intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x99000000.toInt(), 0x00000000),
            floatArrayOf(0f, 0.52f, 0.70f, 0.90f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, mask)
        canvas.restoreToCount(layer)
    }
}


/** Story card background: 120deg gradient, background-size 300%, position shifts with progress 0..1. */
private class HubFlowGradientDrawable(private val density: Float, private val radiusDp: Float) : Drawable() {
    var progress: Float = 0f
        set(value) {
            field = value
            invalidateSelf()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(
        0xFF1F2A16.toInt(), 0xFF414D2D.toInt(), 0xFF89945A.toInt(),
        0xFFD0C79A.toInt(), 0xFF414D2D.toInt(), 0xFF1F2A16.toInt()
    )
    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val shift = (w * 2f) * progress
        paint.shader = LinearGradient(b.left - shift, b.top.toFloat(), b.left - shift + w * 3f, b.top + h, colors, null, Shader.TileMode.CLAMP)
        val r = radiusDp * density
        canvas.drawRoundRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), r, r, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
