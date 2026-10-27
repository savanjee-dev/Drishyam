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
    private var lightMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        lowFx = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getBoolean("low_fx", false)
        reduced = !motionEnabled(this)
        lightMode = Color.luminance(color(R.color.bg)) > 0.5f
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
            root.addView(SettingsAmbientLayer(this, animate), FrameLayout.LayoutParams(-1, -1))
            root.addView(SettingsStarfieldView(this, reduced, lightMode), FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        // Fixed feathered edges fade scrolling content into the page surface, without a solid bar.
        root.addView(View(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color(R.color.bg), withAlpha(color(R.color.bg), 0.78f), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, dp(104), Gravity.TOP))
        root.addView(View(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(color(R.color.bg), withAlpha(color(R.color.bg), 0.78f), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, dp(132), Gravity.BOTTOM))
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val topInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            content.setPadding(dp(20), topInset + dp(18), dp(20), dp(150))
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
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
            // Keep decorative stars and touch feedback inside the rounded card.
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpf(30f))
                }
            }
            background = flowBg
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 0)
            attachPress(glowable = true)
            setOnClickListener {
                startActivity(Intent(this@SettingsHubActivity, MainActivity::class.java).putExtra("open_about", true))
            }
        }

        // Stars are created before content so they shimmer across the card without obscuring text, CTA, or portrait.
        if (animate && !lowFx) {
            // Keep sparkles around the card perimeter and in the open seams, not over copy, CTA, or portrait.
            val starSpecs = listOf(
                Triple(0.03f, 0.06f, 11f), Triple(0.25f, 0.035f, 10f),
                Triple(0.49f, 0.035f, 12f), Triple(0.72f, 0.035f, 10f),
                Triple(0.92f, 0.06f, 12f), Triple(0.965f, 0.27f, 10f),
                Triple(0.965f, 0.49f, 12f), Triple(0.965f, 0.72f, 10f),
                Triple(0.91f, 0.91f, 12f), Triple(0.70f, 0.94f, 10f),
                Triple(0.47f, 0.94f, 12f), Triple(0.24f, 0.94f, 10f),
                Triple(0.035f, 0.82f, 11f), Triple(0.025f, 0.61f, 10f),
                Triple(0.025f, 0.38f, 12f), Triple(0.60f, 0.09f, 10f)
            )
            starSpecs.forEachIndexed { index, spec ->
                val star = TextView(this).apply {
                    text = if (index % 2 == 0) "✦" else "✧"
                    textSize = spec.third
                    gravity = Gravity.CENTER
                    setTextColor(if (index % 3 == 0) 0xFFD5C9A5.toInt() else 0xFFF4EED0.toInt())
                    alpha = 0.96f
                    // Keep sparkles above elevated photo/button children; perimeter placement avoids covering content.
                    elevation = dpf(8f)
                    setShadowLayer(dpf(4f), 0f, 0f, 0xFFD5C9A5.toInt())
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    isClickable = false
                    isFocusable = false
                }
                card.addView(star, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP or Gravity.START))
                card.post {
                    // Render above the gradient and content; perimeter coordinates keep the text and portrait clear.
                    star.bringToFront()
                    star.x = (card.width * spec.first).coerceAtMost((card.width - star.width).toFloat())
                    star.y = (card.height * spec.second).coerceAtMost((card.height - star.height).toFloat())
                    startStarDrift(star, index)
                }
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

        val readArrow = TextView(this).apply {
            text = "→"
            textSize = 14f
            setTextColor(0xFF1B2117.toInt())
            typeface = font(R.font.montserrat_bold)
            gravity = Gravity.CENTER
            rotation = -35f // The arrow tip aims diagonally up-right toward the portrait.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val readButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
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
            contentDescription = "Read the story"
            addView(TextView(this@SettingsHubActivity).apply {
                text = "Read the story"
                textSize = 12.5f
                setTextColor(0xFF1B2117.toInt())
                typeface = font(R.font.montserrat_bold)
                gravity = Gravity.CENTER
            })
            addView(readArrow, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                leftMargin = dp(4)
            })
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
            startArrowNudge(readArrow)
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
            G(R.color.accent2, if (lightMode) 0.88f else 0.52f, 5600, Gravity.TOP or Gravity.START, -70, 0, -90, 0),
            G(R.color.hero, if (lightMode) 0.82f else 0.48f, 7200, Gravity.TOP or Gravity.END, 240, 0, 0, -110),
            G(R.color.accent, if (lightMode) 0.68f else 0.32f, 9000, Gravity.BOTTOM or Gravity.START, 0, 60, 0, 0)
        )
        list.forEachIndexed { index, g ->
            // Light mode uses Drishyam's olive-led Moss & Champagne identity.
            val c = if (lightMode) when (index) {
                0 -> 0xFF89945A.toInt()
                1 -> 0xFFB7A879.toInt()
                else -> 0xFFD5C9A5.toInt()
            } else color(g.colorRes)
            val v = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(c, withAlpha(c, 0f))
                    gradientRadius = dpf(if (lightMode) 250 else 140)
                }
                alpha = g.alpha
            }
            val lp = FrameLayout.LayoutParams(dp(280), dp(280), g.gravity).apply {
                topMargin = dp(g.top); bottomMargin = dp(g.bottom)
                if ((g.gravity and Gravity.END) == Gravity.END) rightMargin = dp(g.end) else leftMargin = dp(g.start)
            }
            root.addView(v, 0, lp)
            if (animate) {
                val motion = when (index) {
                    0 -> floatArrayOf(-74f, 96f, -28f, 118f)
                    1 -> floatArrayOf(88f, -96f, -92f, 62f)
                    else -> floatArrayOf(-92f, 76f, 72f, -104f)
                }
                ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, dpf(motion[0]), dpf(motion[1])),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dpf(motion[2]), dpf(motion[3])),
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

    /** Press feedback combines a water-like ripple with a soft, fading sunlight glow. */
    private fun View.attachPress(glowable: Boolean) {
        val touchEffect = TouchRippleGlowDrawable(density, lightMode)
        val useTouchEffect = glowable && !lowFx
        if (useTouchEffect) foreground = touchEffect
        var touchAnimator: ValueAnimator? = null
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
                    if (useTouchEffect) {
                        touchAnimator?.cancel()
                        touchEffect.begin(e.x, e.y)
                        touchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = if (reduced) 2400L else 1050L
                            interpolator = EASE_OUT
                            addUpdateListener { a -> touchEffect.setProgress(a.animatedValue as Float) }
                            addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    touchEffect.finish()
                                }
                            })
                            start()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

    private fun startStarDrift(star: View, index: Int) {
        if (reduced || lowFx) return
        val dx = if (index % 2 == 0) dpf(12) else -dpf(10)
        val dy = if (index % 3 == 0) -dpf(10) else dpf(8)
        ObjectAnimator.ofPropertyValuesHolder(star,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dx, 0f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dy, 0f)).apply {
            duration = 4200L + index * 170L
            startDelay = index * 180L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = EASE_IN_OUT
            start()
        }
        startTwinkle(star, index * 130L)
    }

    private fun startArrowNudge(arrow: View) {
        if (reduced) return
        // Only the arrow follows a short diagonal path toward the portrait.
        arrow.translationX = 0f
        arrow.translationY = 0f
        ObjectAnimator.ofPropertyValuesHolder(arrow,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, dpf(5), 0f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -dpf(5), 0f)).apply {
            duration = 1350L
            repeatCount = ObjectAnimator.INFINITE
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

/** Touch effect: an expanding, softly fading ripple ring plus a diffuse radial light. */
private class TouchRippleGlowDrawable(private val density: Float, private val lightMode: Boolean) : Drawable() {
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.35f * density
    }
    private var centerX = 0f
    private var centerY = 0f
    private var progress = 1f
    private var active = false

    fun begin(x: Float, y: Float) {
        centerX = x.coerceIn(0f, bounds.width().toFloat())
        centerY = y.coerceIn(0f, bounds.height().toFloat())
        progress = 0f
        active = true
        invalidateSelf()
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidateSelf()
    }

    fun finish() {
        active = false
        glowPaint.shader = null
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (!active || bounds.isEmpty) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        // Clip the complete interaction to the control's rounded silhouette.
        val corner = (kotlin.math.min(w, h) * 0.16f).coerceAtLeast(1f)
        val clip = Path().apply {
            addRoundRect(0f, 0f, w, h, corner, corner, Path.Direction.CW)
        }
        canvas.clipPath(clip)

        val maxRadius = kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat() * 0.72f
        val radius = (maxRadius * (0.18f + 0.82f * progress)).coerceAtLeast(1f)
        val fade = (1f - progress).coerceIn(0f, 1f)
        glowPaint.shader = RadialGradient(
            centerX, centerY, radius,
            if (lightMode) intArrayOf(0x9989945A.toInt(), 0x77B7A879, 0x55D5C9A5, 0x00D5C9A5)
            else intArrayOf(0xA689945A.toInt(), 0x7289945A, 0x3889945A, 0x0089945A),
            floatArrayOf(0f, 0.34f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.alpha = (fade * 255f).toInt()
        canvas.drawCircle(centerX, centerY, radius, glowPaint)

        // Layered wave fronts make the ripple feel less flat.
        ripplePaint.color = 0xFF89945A.toInt()
        ripplePaint.alpha = (fade * 220f).toInt()
        ripplePaint.strokeWidth = density * (1.7f - 0.8f * progress)
        canvas.drawCircle(centerX, centerY, maxRadius * progress, ripplePaint)
        ripplePaint.alpha = (fade * 125f).toInt()
        ripplePaint.strokeWidth = density * 2.4f
        canvas.drawCircle(centerX, centerY, maxRadius * (progress * 0.72f), ripplePaint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        glowPaint.alpha = alpha
        ripplePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        glowPaint.colorFilter = colorFilter
        ripplePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Ambient stars drift across the whole page; reduced motion uses extremely slow movement. */
private class SettingsStarfieldView(context: Context, private val reducedMotion: Boolean, private val lightMode: Boolean) : View(context) {
    private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float, val speed: Float, val alpha: Float)
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = ArrayList<Star>()
    private var animator: ValueAnimator? = null
    private var progress = 0f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val random = java.util.Random(713L)
        repeat(58) {
            stars += Star(0.04f + random.nextFloat() * 0.92f, random.nextFloat(),
                (0.65f + random.nextFloat() * 1.35f) * density,
                random.nextFloat() * 6.28318f, 0.12f + random.nextFloat() * 0.34f,
                (if (lightMode) 0.62f else 0.22f) + random.nextFloat() * (if (lightMode) 0.34f else 0.48f))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (reducedMotion) 90000L else 26000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        stars.forEach { star ->
            val drift = progress * star.speed
            val x = ((star.x + drift * 0.10f) % 1f) * w
            val y = ((star.y + drift) % 1f) * h
            val twinkle = 0.72f + 0.28f * kotlin.math.sin(progress * 6.28318f + star.phase)
            val motionFactor = if (reducedMotion) 0.52f else 1f
            val a = (star.alpha * twinkle * motionFactor * 255f).toInt().coerceIn(0, 255)
            paint.color = if (lightMode) {
                if (stars.indexOf(star) % 4 == 0) Color.argb(a, 183, 168, 121)
                else Color.argb(a, 112, 126, 77)
            } else Color.argb(a, 218, 222, 165)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, star.radius, paint)
            if (star.radius > 1.55f * density) {
                paint.alpha = (a * 0.22f).toInt()
                paint.strokeWidth = 0.7f * density
                canvas.drawLine(x - star.radius * 2.2f, y, x + star.radius * 2.2f, y, paint)
                canvas.drawLine(x, y - star.radius * 2.2f, x, y + star.radius * 2.2f, paint)
            }
        }
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
