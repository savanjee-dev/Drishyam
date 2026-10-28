package com.savan.drishyam

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Appearance page (Phase S2). Built to match Drishyam_Settings_Prototype_v9.html, section "appearance".
 * Storage keys (shared with the rest of the app): theme ("System"/"Dark"/"Light"), haptics (boolean),
 * low_fx (boolean, true when Glass effects is Off), glass_level ("off"/"subtle"/"full"), motion ("standard"/"reduced").
 */
class AppearanceActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Number): Int = (v.toFloat() * density).toInt()
    private fun dpf(v: Number): Float = v.toFloat() * density
    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun font(id: Int) = ResourcesCompat.getFont(this, id)
    private val prefs by lazy { getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE) }

    private var lowFx = false
    private var reduced = false
    private var haptics = true
    private var theme = "System"
    private var glassLevel = "full"
    private var motion = "standard"

    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var appearanceScroll: ScrollView
    private lateinit var backButton: ImageView
    private var themeRevealOverlay: View? = null
    private var themeTransitionInProgress = false
    private val themeCards = mutableListOf<LinearLayout>()
    private val themeNames = listOf("System", "Dark", "Light")
    private val ambientGlows = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)

        theme = prefs.getString("theme", "System") ?: "System"
        haptics = prefs.getBoolean("haptics", true)
        glassLevel = prefs.getString("glass_level", null) ?: if (prefs.getBoolean("low_fx", false)) "off" else "full"
        motion = prefs.getString("motion", null) ?: if (systemReduceMotion()) "reduced" else "standard"
        lowFx = glassLevel == "off"
        reduced = motion == "reduced"

        window.setBackgroundDrawable(ColorDrawable(color(R.color.bg)))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.bg)); clipChildren = false }
        addGlows()

        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(150))
            clipChildren = false
            clipToPadding = false
        }
        appearanceScroll = ScrollView(this).apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(page)
        }
        // Keep the top feather fade behind the floating back control and scrolling content.
        val topFeather = View(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color(R.color.bg), withAlpha(color(R.color.bg), 0.78f), Color.TRANSPARENT))
        }
        root.addView(appearanceScroll, FrameLayout.LayoutParams(-1, -1))
        // Keep the top fade above scrolling content so it remains visible while scrolling.
        // The floating back control is inserted above this fade below.
        root.addView(topFeather, FrameLayout.LayoutParams(-1, dp(104), Gravity.TOP))
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
            // Move the complete header group down by 24dp and keep a further 16dp
            // of separation between the anchored back button and the SETTINGS eyebrow.
            page.setPadding(dp(20), topInset + dp(32), dp(20), dp(150))
            (backButton.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.topMargin = topInset + dp(36)
                backButton.layoutParams = lp
            }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        PageStretch.attach(appearanceScroll, density, reduced)

        buildPage()
        // Draw the button above the feather fade but below the scrolling page, so
        // header content passes over it instead of being tinted by its glass surface.
        root.addView(backButton, 5, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(20)
            topMargin = dp(36)
        })
        appearanceScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = (scrollY.toFloat() / dp(84f)).coerceIn(0f, 1f)
            backButton.alpha = 1f - progress
            backButton.scaleX = 1f - 0.08f * progress
            backButton.scaleY = 1f - 0.08f * progress
        }
        startEntrance()
    }

    // ============================ Page ============================

    private fun buildPage() {
        // Back button (48 circle, glass, no border)
        backButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.fg))
            scaleType = ImageView.ScaleType.CENTER
            background = glassCircle(withBorder = false)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_desc)
            attachPress(glowable = true)
            setOnClickListener { finish() }
        }
        // Keep the header below the anchored back control with the agreed 16dp gap.
        page.addView(View(this), LinearLayout.LayoutParams(dp(48), dp(64)))

        // Eyebrow, title, lead
        page.addView(tv("Settings", 11f, R.color.accent, R.font.poppins_semibold, 10, 0).apply { letterSpacing = 0.16f; text = "SETTINGS" })
        page.addView(tv("Appearance", 38f, R.color.fg, R.font.poppins_extrabold, 1, 1).apply { letterSpacing = -0.02f })
        page.addView(tv("Theme, haptics and visual effects.", 14f, R.color.mute, R.font.poppins_regular, 0, 4))

        // ---- Theme ----
        page.addView(label("Theme"))
        val themes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (name in themeNames) {
            val card = themeCard(name)
            themeCards.add(card)
            themes.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                when (name) {
                    "System" -> rightMargin = dp(5)
                    "Dark" -> { leftMargin = dp(5); rightMargin = dp(5) }
                    else -> leftMargin = dp(5)
                }
            })
        }
        page.addView(themes, matchWrap(0))
        refreshThemeCards()

        // ---- Effects ----
        page.addView(label("Effects"))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = glassCard()
        }
        card.addView(glassItem())
        card.addView(divider())
        card.addView(hapticsItem())
        card.addView(divider())
        card.addView(motionItem())
        page.addView(card, matchWrap(0))

        page.addView(tv("Changes apply straight away.", 12f, R.color.mute, R.font.poppins_regular, 24, 0).apply {
            gravity = Gravity.CENTER
        })
    }

    // ---------- Theme card (prototype .theme-card, .mock, .check) ----------
    private fun themeCard(name: String): LinearLayout {
        val mock = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpf(16)
                setStroke(dp(1), withAlpha(color(R.color.accent2), 0.25f))
                setColor(Color.TRANSPARENT)
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpf(16))
                }
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92)).apply { bottomMargin = dp(8) }
        }
        val fillColor = when (name) {
            "Dark" -> 0xFF0A0D08.toInt()
            "Light" -> 0xFFF3F1E7.toInt()
            else -> 0xFFF3F1E7.toInt()
        }
        mock.background = GradientDrawable().apply {
            cornerRadius = dpf(16)
            setStroke(dp(1), withAlpha(0xFF89945A.toInt(), 0.25f))
            setColor(if (name == "System") 0xFF0A0D08.toInt() else fillColor)
        }
        if (name == "System") {
            // Set the 50/50 split after layout so the left half has a real measured width.
            val leftHalf = View(this).apply { setBackgroundColor(0xFFF3F1E7.toInt()) }
            mock.addView(leftHalf, FrameLayout.LayoutParams(0, -1, Gravity.START))
            mock.post {
                leftHalf.layoutParams = FrameLayout.LayoutParams(mock.width / 2, -1, Gravity.START)
            }
        }
        val accent = 0xFF414D2D.toInt()
        val accent2 = 0xFF89945A.toInt()
        val squareLight = if (name == "Dark") 0xFF1B2117.toInt() else 0xFFFFFCF4.toInt()
        val squareRight = if (name == "Light") 0xFFFFFCF4.toInt() else 0xFF1B2117.toInt()

        // Exact prototype colours and dimensions: m-bar #89945A; m-pill #89945A only in Dark.
        val bar = View(this).apply { background = pill(accent2) }
        // Squares: top 24, width 36%, height 44, radius 10; left 8 and left 50%
        val sqA = View(this).apply { background = rounded(squareLight, 10f) }
        val sqB = View(this).apply { background = rounded(squareRight, 10f) }
        // Pill: left 8, right 8, bottom 8, height 12
        val pillView = View(this).apply { background = pill(if (name == "Dark") accent2 else accent) }

        mock.addView(bar, FrameLayout.LayoutParams(-1, dp(7), Gravity.TOP or Gravity.START).apply { topMargin = dp(9); leftMargin = dp(8) })
        mock.addView(sqA, FrameLayout.LayoutParams(-1, dp(44), Gravity.TOP or Gravity.START).apply { topMargin = dp(24); leftMargin = dp(8) })
        mock.addView(sqB, FrameLayout.LayoutParams(-1, dp(44), Gravity.TOP or Gravity.START).apply { topMargin = dp(24) })
        mock.addView(pillView, FrameLayout.LayoutParams(-1, dp(12), Gravity.BOTTOM).apply { bottomMargin = dp(8); leftMargin = dp(8); rightMargin = dp(8) })
        mock.post {
            val w = mock.width.toFloat()
            val rightFraction = if (name == "System") 0.44f else 0.40f
            bar.layoutParams = FrameLayout.LayoutParams((w - dp(8) - rightFraction * w).toInt(), dp(7), Gravity.TOP or Gravity.START).apply { topMargin = dp(9); leftMargin = dp(8) }
            sqA.layoutParams = FrameLayout.LayoutParams((0.36f * w).toInt(), dp(44), Gravity.TOP or Gravity.START).apply { topMargin = dp(24); leftMargin = dp(8) }
            sqB.layoutParams = FrameLayout.LayoutParams((0.36f * w).toInt(), dp(44), Gravity.TOP or Gravity.START).apply { topMargin = dp(24); leftMargin = (0.5f * w).toInt() }
        }

        val check = ImageView(this).apply {
            setImageResource(R.drawable.ic_check)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = pill(accent)
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END).apply { topMargin = dp(10); rightMargin = dp(10) }
            visibility = View.GONE
            tag = "check"
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(mock)
            addView(tv(name, 12f, R.color.fg, R.font.poppins_semibold).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) }
            })
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = glassCard(radius = 24f, strokeWidth = 1.5f)
            isClickable = true
            isFocusable = true
            tag = name
            // Avoid the clipped touch-glow foreground that can expose a pale rectangular edge.
            // Keep the existing scale press feedback and rounded glass card surface.
            clipToOutline = true
            attachPress(glowable = false)
            setOnClickListener { chooseTheme(name, this) }
        }
        val holder = FrameLayout(this)
        holder.addView(content, FrameLayout.LayoutParams(-1, -2))
        holder.addView(check)
        card.addView(holder, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun refreshThemeCards() {
        for (card in themeCards) {
            val selected = card.tag == theme
            card.background = glassCard(radius = 24f, strokeWidth = 1.5f, strokeColor = if (selected) color(R.color.accent2) else null,
                ringColor = if (selected) withAlpha(color(R.color.accent2), 0.22f) else null)
            val holder = card.getChildAt(0) as FrameLayout
            holder.getChildAt(1).visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    // Reveal the target surface fully before changing AppCompat's night mode.
    // If the mode changes, keep the opaque target-colour overlay attached until this
    // Activity is destroyed, so the old-themed page is never exposed during recreation.
    private fun chooseTheme(name: String, card: View) {
        if (themeTransitionInProgress || name == theme) return
        themeTransitionInProgress = true
        themeCards.forEach { it.isEnabled = false }
        buzz(8)

        val targetMode = when (name) {
            "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "Light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        // Resolve the device's effective system appearance, not the Activity's
        // current AppCompat appearance (which may still be explicitly Light or Dark).
        val systemDark = (android.content.res.Resources.getSystem().configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val targetDark = when (name) {
            "Dark" -> true
            "Light" -> false
            else -> systemDark
        }
        val currentDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val appearanceWillChange = currentDark != targetDark

        theme = name
        prefs.edit().putString("theme", name).apply()
        refreshThemeCards()

        themeRevealOverlay?.let { root.removeView(it) }
        val targetColor = if (targetDark) 0xFF0A0D08.toInt() else 0xFFF3F1E7.toInt()
        val overlay = View(this).apply { setBackgroundColor(targetColor) }
        themeRevealOverlay = overlay
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val loc = IntArray(2)
        card.getLocationOnScreen(loc)
        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        val cx = (loc[0] - rootLoc[0] + card.width / 2).toFloat()
        val cy = (loc[1] - rootLoc[1] + card.height / 2).toFloat()
        val r = hypot(maxOf(cx, root.width - cx), maxOf(cy, root.height - cy))
        val anim = ViewAnimationUtils.createCircularReveal(overlay, cx.toInt(), cy.toInt(), 0f, r)
        anim.duration = 380L
        anim.interpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

        var finished = false
        fun applyThemeBehindCover() {
            if (finished) return
            finished = true
            // The reveal has reached full coverage. Apply night mode while the opaque
            // cover still hides the old UI; onDestroy removes it as recreation completes.
            AppCompatDelegate.setDefaultNightMode(targetMode)
            if (!appearanceWillChange || isFinishing || isDestroyed) {
                if (overlay.parent === root) root.removeView(overlay)
                if (themeRevealOverlay === overlay) themeRevealOverlay = null
                themeTransitionInProgress = false
                themeCards.forEach { it.isEnabled = true }
            }
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                applyThemeBehindCover()
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                // A cancelled reveal must not uncover the old theme before switching.
                overlay.visibility = View.VISIBLE
                overlay.setBackgroundColor(targetColor)
                applyThemeBehindCover()
            }
        })
        anim.start()
    }

    override fun onDestroy() {
        themeRevealOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        themeRevealOverlay = null
        themeTransitionInProgress = false
        super.onDestroy()
    }

    // ---------- Glass effects (segmented: Off / Subtle / Full) ----------
    private fun glassItem(): LinearLayout {
        val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(14)) }
        item.addView(itemTitle("Glass effects", "Turn glass off to save battery on older phones."))
        item.addView(segment(listOf("Off" to "off", "Subtle" to "subtle", "Full" to "full"), glassLevel) { value ->
            glassLevel = value
            prefs.edit().putString("glass_level", value).putBoolean("low_fx", value == "off").apply()
            lowFx = value == "off"
            applyGlassLevel()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return item
    }

    // ---------- Haptics: Test button, switch with halo ----------
    private fun hapticsItem(): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(14))
        }
        val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        txt.addView(itemTitle("Haptics", "Light vibration on taps and switches."))
        item.addView(txt, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })

        val test = TextView(this).apply {
            text = "Test"
            textSize = 12f
            typeface = font(R.font.montserrat_semibold)
            gravity = Gravity.CENTER
            setTextColor(color(R.color.fg))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minWidth = dp(64)
            background = glassPill()
            isClickable = true
            isFocusable = true
            attachPress(glowable = false)
        }
        test.setOnClickListener {
            vibrate(18)
            test.text = if (haptics) "Buzz!" else "Off"
            test.postDelayed({ test.text = "Test" }, 900L)
        }
        item.addView(test, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })

        val sw = FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(999); setColor(if (haptics) color(R.color.accent) else withAlpha(color(R.color.accent2), 0.3f)) }
            isClickable = true
            isFocusable = true
            // Keep switch feedback contained by its rounded track; no child may spill
            // outside the track and reveal a rectangular clipping edge.
            clipChildren = true
            clipToPadding = true
        }
        val thumb = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dpf(2)
            translationX = if (haptics) dpf(22) else 0f
        }
        sw.addView(thumb, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(3) })
        sw.layoutParams = LinearLayout.LayoutParams(dp(52), dp(30))
        sw.setOnClickListener {
            haptics = !haptics
            prefs.edit().putBoolean("haptics", haptics).apply()
            fadeFill(sw.background as GradientDrawable, if (haptics) color(R.color.accent) else withAlpha(color(R.color.accent2), 0.3f))
            ObjectAnimator.ofFloat(thumb, View.TRANSLATION_X, if (haptics) dpf(22) else 0f).apply {
                duration = if (reduced) 0L else 350L
                interpolator = SPRING
                start()
            }
            if (haptics) {
                // Keep the activation feedback inside the rounded track. The thumb and
                // track already animate; an expanding child halo was clipped by ancestors
                // and could appear as a pale square around the whole control.
                sw.animate().cancel()
                sw.alpha = 0.96f
                if (!reduced) {
                    sw.animate().alpha(1f).setDuration(220L).setInterpolator(EASE_OUT).start()
                } else {
                    sw.alpha = 1f
                }
                vibrate(10)
            }
        }
        item.addView(sw, LinearLayout.LayoutParams(dp(52), dp(30)))
        return item
    }

    // ---------- Motion: Standard / Reduced ----------
    private fun motionItem(): LinearLayout {
        val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(14)) }
        item.addView(itemTitle("Motion", "Reduced replaces bouncy motion with simple fades."))
        item.addView(segment(listOf("Standard" to "standard", "Reduced" to "reduced"), motion) { value ->
            motion = value
            prefs.edit().putString("motion", value).apply()
            reduced = value == "reduced"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return item
    }

    private fun systemReduceMotion(): Boolean =
        try { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }

    // ---------- Segmented control (prototype .seg, .ind; stretch from SegmentStretch) ----------
    private fun segment(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit): FrameLayout {
        var selected = current
        val seg = FrameLayout(this).apply {
            background = glassPill()
            clipChildren = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val indicator = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(999); setColor(color(R.color.accent)) }
        }
        val buttons = mutableListOf<TextView>()
        for ((label, value) in options) {
            val b = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = font(R.font.montserrat_semibold)
                setPadding(dp(6), dp(10), dp(6), dp(10))
                tag = value
                isClickable = true
                isFocusable = true
            }
            buttons.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        seg.addView(indicator, FrameLayout.LayoutParams(0, -1).apply { topMargin = dp(4); bottomMargin = dp(4) })
        seg.addView(row, FrameLayout.LayoutParams(-1, -2))

        fun paintLabels(animate: Boolean) {
            buttons.forEach { b ->
                val to = if (b.tag == selected) color(R.color.bg) else color(R.color.mute)
                b.setTextColor(to)
                b.setTypeface(font(R.font.montserrat_semibold), if (b.tag == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        // Bounds of an option inside the segment (px)
        fun boundsOf(b: TextView): Pair<Float, Float> =
            Pair((row.left + b.left).toFloat(), b.width.toFloat())

        // Place the indicator at once, on screen open
        seg.post {
            val target = buttons.first { it.tag == selected }
            SegmentStretch.place(indicator, boundsOf(target).first, boundsOf(target).second)
        }
        paintLabels(false)

        for (b in buttons) {
            b.setOnClickListener {
                if (b.tag == selected) return@setOnClickListener
                buzz(8)
                selected = b.tag as String
                onPick(selected)
                paintLabels(false)
                SegmentStretch.animate(indicator, boundsOf(b), density, reduced)
            }
        }
        return seg
    }

    // ============================ Helpers ============================

    private fun label(text: String): TextView =
        tv(text.uppercase(), 11f, R.color.accent, R.font.poppins_semibold, 28, 12).apply {
            letterSpacing = 0.14f
            setPadding(dp(4), dp(28), 0, dp(12))
        }

    private fun itemTitle(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(tv(title, 14f, R.color.fg, R.font.poppins_semibold))
        addView(tv(subtitle, 12f, R.color.mute, R.font.poppins_regular, 2, 0).apply { setLineSpacing(0f, 1.4f) })
    }

    /** Fades a TextView's colour over 300 ms, ease (prototype: transition color .3s). */
    private fun fadeTextColor(view: TextView, to: Int) {
        ValueAnimator.ofObject(ArgbEvaluator(), view.currentTextColor, to).apply {
            duration = 300L
            interpolator = EASE
            addUpdateListener { a -> view.setTextColor(a.animatedValue as Int) }
            start()
        }
    }

    /** Fades a shape's fill colour over 300 ms, ease (prototype: .switch transition background .3s). */
    private fun fadeFill(shape: GradientDrawable, to: Int) {
        val from = if (shape.color != null) shape.color!!.defaultColor else to
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 300L
            interpolator = EASE
            addUpdateListener { a -> shape.setColor(a.animatedValue as Int) }
            start()
        }
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(withAlpha(color(R.color.accent2), 0.42f))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }

    private fun tv(value: String, size: Float, colorRes: Int, fontRes: Int, top: Int = 0, bottom: Int = 0): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color(colorRes))
            typeface = font(fontRes)
            setPadding(0, dp(top), 0, dp(bottom))
        }

    private fun matchWrap(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun glassCard(radius: Float = 28f, strokeWidth: Float = 1f, strokeColor: Int? = null, ringColor: Int? = null): Drawable {
        val glass = GradientDrawable().apply {
            cornerRadius = dpf(radius)
            setColor(color(R.color.glassFill))
            setStroke(dp(strokeWidth), strokeColor ?: color(R.color.glassStroke))
        }
        if (ringColor == null) return glass
        val ring = GradientDrawable().apply { cornerRadius = dpf(radius + 3); setColor(ringColor) }
        return LayerDrawable(arrayOf(ring, glass)).apply {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, dp(3), dp(3), dp(3), dp(3))
        }
    }

    private fun glassPill(): Drawable = GradientDrawable().apply {
        cornerRadius = dpf(999)
        setColor(color(R.color.glassFill))
        setStroke(dp(1), color(R.color.glassStroke))
    }

    private fun glassCircle(withBorder: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color(R.color.glassFill))
        if (withBorder) setStroke(dp(1), color(R.color.glassStroke))
    }

    private fun pill(c: Int): Drawable = GradientDrawable().apply { cornerRadius = dpf(999); setColor(c) }
    private fun rounded(c: Int, r: Float): Drawable = GradientDrawable().apply { cornerRadius = dpf(r); setColor(c) }

    private fun withAlpha(c: Int, alpha: Float): Int =
        Color.argb((255 * alpha).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun buzz(ms: Long) {
        if (!haptics) return
        val v = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }

    private fun vibrate(ms: Long) = buzz(ms)

    // ============================ Press (prototype .press, .glowable) ============================

    private fun View.attachPress(glowable: Boolean) {
        val glow = AppearanceTouchGlow(density)
        fun useGlow() = glowable && glassLevel != "off" && !reduced
        if (glowable) foreground = glow
        var anim: ValueAnimator? = null
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ObjectAnimator.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f)).apply {
                        duration = if (reduced) 0L else 90L
                        interpolator = OUT
                        start()
                    }
                    if (useGlow()) {
                        glow.centreX = e.x
                        glow.centreY = e.y
                        anim?.cancel()
                        anim = ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = 450L
                            interpolator = EASE
                            addUpdateListener { a ->
                                val t = a.animatedValue as Float
                                glow.radiusScale = 0.4f + 0.6f * t
                                val intensity = if (glassLevel == "subtle") 128 else 255
                                glow.glowAlpha = (intensity * t).toInt()
                            }
                            start()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (useGlow()) {
                        anim?.cancel()
                        anim = ValueAnimator.ofInt(glow.glowAlpha, 0).apply {
                            duration = 450L
                            interpolator = EASE
                            addUpdateListener { a -> glow.glowAlpha = a.animatedValue as Int }
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
            android.animation.Keyframe.ofFloat(0f, 0.92f), android.animation.Keyframe.ofFloat(0.45f, 1.06f),
            android.animation.Keyframe.ofFloat(0.70f, 0.985f), android.animation.Keyframe.ofFloat(1f, 1f))
        ObjectAnimator.ofPropertyValuesHolder(v, keys("scaleX"), keys("scaleY")).apply {
            duration = 600L
            interpolator = OUT
            start()
        }
    }

    // ============================ Entrance and page open ============================

    private fun startEntrance() {
        // Page open (prototype .sub): scale .98 to 1 and opacity 0 to 1, 260 ms, after 150 ms
        page.alpha = 0f
        page.scaleX = 0.98f
        page.scaleY = 0.98f
        if (!reduced) {
            page.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(150L).setDuration(260L).setInterpolator(OUT).start()
        } else {
            page.alpha = 1f; page.scaleX = 1f; page.scaleY = 1f
        }
        // Blocks rise 16dp and fade in, 40 ms apart (prototype data-stagger)
        if (!reduced) {
            for (i in 0 until page.childCount) {
                val child = page.getChildAt(i)
                child.alpha = 0f
                child.translationY = dpf(16)
                child.animate().alpha(1f).translationY(0f).setStartDelay(i * 40L).setDuration(420L).setInterpolator(SPRING).start()
            }
        }
    }

    // ============================ Background glows and loop clock ============================

    private fun addGlows() {
        data class G(val colorRes: Int, val alpha: Float, val dur: Long, val gravity: Int, val top: Int, val bottom: Int, val start: Int, val end: Int)
        val list = listOf(
            G(R.color.accent2, 0.66f, 10000, Gravity.TOP or Gravity.START, -70, 0, -90, 0),
            G(R.color.hero, 0.58f, 12000, Gravity.TOP or Gravity.END, 240, 0, 0, -110),
            G(R.color.accent, 0.38f, 14000, Gravity.BOTTOM or Gravity.START, 0, 60, 0, 0)
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
                tag = g.alpha
            }
            ambientGlows.add(v)
            val lp = FrameLayout.LayoutParams(dp(280), dp(280), g.gravity).apply {
                topMargin = dp(g.top); bottomMargin = dp(g.bottom)
                if ((g.gravity and Gravity.END) == Gravity.END) rightMargin = dp(g.end) else leftMargin = dp(g.start)
            }
            root.addView(v, 0, lp)
            addLoop { t ->
                // Phase S2 ambient background: offset axes create slow organic drift rather than a diagonal loop.
                val px = pingPong(t, g.dur)
                val py = pingPong(t, (g.dur * 1.37f).toLong(), g.dur / 4)
                v.translationX = dpf(104) * (px - 0.5f)
                v.translationY = dpf(78) * (py - 0.5f)
                v.scaleX = 1f + 0.18f * px
                v.scaleY = 1f + 0.20f * py
                // Explicitly invalidate the glow drawable on every frame so its
                // radial gradient stays visibly animated across device renderers.
                v.invalidate()
            }
        }
        applyGlassLevel()
    }

    private fun applyGlassLevel() {
        val factor = when (glassLevel) {
            "off" -> 0f
            "subtle" -> 0.45f
            else -> 1f
        }
        lowFx = glassLevel == "off"
        ambientGlows.forEach { glow ->
            val baseAlpha = glow.tag as? Float ?: 1f
            glow.alpha = baseAlpha * factor
            glow.visibility = if (factor == 0f) View.INVISIBLE else View.VISIBLE
        }
    }

    private val loops = mutableListOf<(Long) -> Unit>()
    private var loopStart = 0L
    private var loopRunning = false
    private val loopFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopRunning) return
            val t = SystemClock.uptimeMillis() - loopStart
            for (l in loops) l(t)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun addLoop(block: (Long) -> Unit) { loops.add(block) }

    override fun onResume() {
        super.onResume()
        if (!loopRunning && loops.isNotEmpty() && !reduced) {
            loopRunning = true
            loopStart = SystemClock.uptimeMillis()
            Choreographer.getInstance().postFrameCallback(loopFrame)
        }
    }

    override fun onPause() {
        loopRunning = false
        Choreographer.getInstance().removeFrameCallback(loopFrame)
        super.onPause()
    }

    private fun pingPong(t: Long, period: Long, delay: Long = 0L): Float {
        val local = (t - delay).coerceAtLeast(0L) % (2 * period)
        val x = local.toFloat() / period.toFloat()
        val p = if (x <= 1f) x else 2f - x
        return easeInOut(p)
    }

    private fun easeInOut(p: Float): Float = (0.5 - 0.5 * cos(PI * p.toDouble())).toFloat()

    companion object {
        private val SPRING = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
        private val OUT = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
        private val EASE_OUT = PathInterpolator(0f, 0f, 0.58f, 1f)
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}

/** Touch light for the Appearance page: 180dp radial glow from rgba(137,148,90,0.5) to transparent at 70%. */
private class AppearanceTouchGlow(private val density: Float) : Drawable() {
    var centreX = 0f
    var centreY = 0f
    var radiusScale = 1f
        set(value) { field = value; invalidateSelf() }
    var glowAlpha = 0
        set(value) { field = value; invalidateSelf() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        if (glowAlpha <= 0) return
        val r = 90f * density * radiusScale
        paint.shader = RadialGradient(centreX, centreY, r,
            intArrayOf(0x8089945A.toInt(), 0x0089945A), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP)
        paint.alpha = glowAlpha
        canvas.drawCircle(centreX, centreY, r, paint)
    }
    override fun setAlpha(alpha: Int) { glowAlpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}