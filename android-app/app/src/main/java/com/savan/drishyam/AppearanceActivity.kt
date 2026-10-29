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
import android.view.ViewOutlineProvider
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
    internal fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun font(id: Int) = ResourcesCompat.getFont(this, id)
    private val prefs by lazy { getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE) }

    private var lowFx = false
    internal var reduced = false
    private var haptics = true
    private var theme = "System"
    internal var glassLevel = "full"
    private var motion = "standard"

    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var appearanceScroll: ScrollView
    internal lateinit var backButton: ImageView
    internal val themeCards = mutableListOf<LinearLayout>()
    private val themeNames = listOf("System", "Dark", "Light")
    internal val ambientGlows = mutableListOf<View>()
    private var ambientStarfield: AppearanceStarfieldView? = null
    internal val reflectionTargets = mutableListOf<Pair<View, Boolean>>()
    private var reflectionOverlay: AppearanceReflectionOverlay? = null
    private var themeRevealOverlay: View? = null
    private var themeTransitionInProgress = false
    private var themeTapScreen: Pair<Float, Float>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        setTheme(R.style.Theme_Drishyam_Portal)
        super.onCreate(savedInstanceState)
        applyActivityTransitionPreference(this)

        theme = prefs.getString("theme", "System") ?: "System"
        haptics = prefs.getBoolean("haptics", true)
        glassLevel = prefs.getString("glass_level", null) ?: if (prefs.getBoolean("low_fx", false)) "off" else "full"
        motion = prefs.getString("motion", null) ?: if (prefs.getBoolean("motion_reduced", systemReduceMotion())) "reduced" else "standard"
        lowFx = glassLevel == "off"
        reduced = motion == "reduced"

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.bg)); clipChildren = false }
        UniversalBackgroundView.attach(root)
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
                intArrayOf(
                    color(R.color.bg),
                    withAlpha(color(R.color.bg), 0.92f),
                    withAlpha(color(R.color.bg), 0.68f),
                    withAlpha(color(R.color.bg), 0.34f),
                    Color.TRANSPARENT
                ))
        }
        root.addView(appearanceScroll, FrameLayout.LayoutParams(-1, -1))
        // Keep the wider, softer top fade above scrolling content while the fixed Back
        // button is drawn above it and fades only as the page scrolls.
        root.addView(topFeather, FrameLayout.LayoutParams(-1, dp(148), Gravity.TOP))
        root.addView(View(this).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(color(R.color.bg), withAlpha(color(R.color.bg), 0.56f), Color.TRANSPARENT))
        }, FrameLayout.LayoutParams(-1, dp(88), Gravity.BOTTOM))
        setContentView(root)
        // Theme changes recreate this Activity. Do not replay the page-opening portal
        // during that recreation; the in-page theme reveal owns this transition.
        PortalTransition.enter(this, animateOpening = savedInstanceState == null)
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
        registerReflection(backButton, circular = true)
        val reflections = AppearanceReflectionOverlay(this, this)
        reflectionOverlay = reflections
        reflections.visibility = if (glassLevel != "off") View.VISIBLE else View.INVISIBLE
        // This visual-only overlay must never intercept taps on the Back control or page content.
        reflections.isClickable = false
        reflections.isFocusable = false
        reflections.isFocusableInTouchMode = false
        root.addView(reflections, FrameLayout.LayoutParams(-1, -1))
        // Keep the floating Back control above the full-screen reflection layer so
        // it reliably receives taps instead of depending on child dispatch order.
        root.bringChildToFront(backButton)
        applyGlassLevel()
        addLoop { reflectionOverlay?.setElapsed(it) }
        appearanceScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = (scrollY.toFloat() / dp(84f)).coerceIn(0f, 1f)
            val visible = 1f - progress
            backButton.alpha = visible
            backButton.isClickable = visible > 0.05f
            backButton.isFocusable = visible > 0.05f
            backButton.scaleX = 1f - 0.08f * progress
            backButton.scaleY = 1f - 0.08f * progress
        }
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
            setOnClickListener { view -> PortalTransition.close(this@AppearanceActivity, view) }
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

        // ---- Independent effect controls ----
        page.addView(label("Effects"))
        val effectsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = glassCard()
        }
        effectsCard.addView(glassItem())
        effectsCard.addView(divider())
        effectsCard.addView(motionItem())
        page.addView(effectsCard, matchWrap(0))

        page.addView(label("Haptics"))
        val hapticsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = glassCard()
        }
        hapticsCard.addView(hapticsItem())
        page.addView(hapticsCard, matchWrap(0))

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
            // The touch glow clips itself to this rounded card outline, preventing
            // rectangular foreground artefacts while preserving the radial touch feedback.
            clipToOutline = true
            attachPress(glowable = true)
            setOnClickListener { chooseTheme(name, this) }
        }
        val holder = FrameLayout(this)
        holder.addView(content, FrameLayout.LayoutParams(-1, -2))
        holder.addView(check)
        card.addView(holder, LinearLayout.LayoutParams(-1, -2))
        registerReflection(card)
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

    // Theme selection has its own circular reveal, independent from page navigation.
    // The reveal begins at the actual ACTION_DOWN point on the selected card.
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
        val systemDark = (android.content.res.Resources.getSystem().configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val targetDark = when (name) {
            "Dark" -> true
            "Light" -> false
            else -> systemDark
        }
        val currentDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val appearanceWillChange = currentDark != targetDark

        theme = name
        prefs.edit().putString("theme", name).apply()
        refreshThemeCards()

        themeRevealOverlay?.let { root.removeView(it) }
        val targetColor = if (targetDark) 0xFF0A0D08.toInt() else 0xFFF3F1E7.toInt()
        val overlay = View(this).apply { setBackgroundColor(targetColor) }
        themeRevealOverlay = overlay
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        val touched = themeTapScreen
        themeTapScreen = null
        val cx = ((touched?.first ?: run {
            val loc = IntArray(2); card.getLocationOnScreen(loc)
            loc[0] + card.width / 2f
        }) - rootLoc[0]).coerceIn(0f, root.width.toFloat())
        val cy = ((touched?.second ?: run {
            val loc = IntArray(2); card.getLocationOnScreen(loc)
            loc[1] + card.height / 2f
        }) - rootLoc[1]).coerceIn(0f, root.height.toFloat())
        val radius = hypot(maxOf(cx, root.width - cx), maxOf(cy, root.height - cy))
        val anim = ViewAnimationUtils.createCircularReveal(overlay, cx.toInt(), cy.toInt(), 0f, radius)
        anim.duration = if (reduced) 0L else 420L
        anim.interpolator = PathInterpolator(0.20f, 0.78f, 0.20f, 1f)

        var finished = false
        fun applyThemeBehindCover() {
            if (finished) return
            finished = true
            AppCompatDelegate.setDefaultNightMode(targetMode)
            if (!appearanceWillChange || isFinishing || isDestroyed) {
                if (overlay.parent === root) root.removeView(overlay)
                if (themeRevealOverlay === overlay) themeRevealOverlay = null
                themeTransitionInProgress = false
                themeCards.forEach { it.isEnabled = true }
            }
        }
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = applyThemeBehindCover()
            override fun onAnimationCancel(animation: android.animation.Animator) {
                overlay.visibility = View.VISIBLE
                overlay.setBackgroundColor(targetColor)
                applyThemeBehindCover()
            }
        })
        anim.start()
    }

    override fun onDestroy() {
        themeRevealOverlay?.let { overlay -> (overlay.parent as? ViewGroup)?.removeView(overlay) }
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

    // ---------- Haptics: Test button and shared toggle ----------
    // Shared custom ON/OFF control used by Visual Effects and Haptics.
    private fun effectToggle(initial: Boolean, onChanged: (Boolean) -> Unit): FrameLayout {
        var checked = initial
        val track = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    if (checked) withAlpha(color(R.color.accent), 0.88f) else withAlpha(color(R.color.accent2), 0.28f),
                    if (checked) color(R.color.accent) else withAlpha(color(R.color.accent2), 0.30f),
                    if (checked) withAlpha(color(R.color.accent), 0.94f) else withAlpha(color(R.color.accent2), 0.32f)
                )).apply {
                cornerRadius = dpf(999)
                setStroke(dp(1), withAlpha(Color.WHITE, 0.72f))
            }
            isClickable = true
            isFocusable = true
            clipChildren = true
            clipToPadding = true
            contentDescription = if (checked) "On" else "Off"
        }
        val thumb = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            elevation = dpf(2)
            translationX = if (checked) dpf(22) else 0f
        }
        track.addView(thumb, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            leftMargin = dp(3)
        })
        registerReflection(track)
        track.setOnClickListener {
            checked = !checked
            track.contentDescription = if (checked) "On" else "Off"
            fadeFill(track.background as GradientDrawable,
                if (checked) color(R.color.accent) else withAlpha(color(R.color.accent2), 0.3f))
            ObjectAnimator.ofFloat(thumb, View.TRANSLATION_X, if (checked) dpf(22) else 0f).apply {
                duration = if (reduced) 0L else 350L
                interpolator = SPRING
                start()
            }
            track.animate().cancel()
            track.alpha = if (checked && !reduced) 0.96f else 1f
            if (checked && !reduced) track.animate().alpha(1f).setDuration(220L).setInterpolator(EASE_OUT).start()
            onChanged(checked)
        }
        return track
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
            background = if (Color.luminance(color(R.color.bg)) > 0.5f) elevatedGlassPill() else glassPill()
            if (Color.luminance(color(R.color.bg)) > 0.5f) {
                // Use a real, visible lower rim as well as elevation; elevation alone
                // was too subtle against the cream page background.
                elevation = dpf(5)
                translationZ = dpf(1)
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = false
            }
            isClickable = true
            isFocusable = true
            attachPress(glowable = false)
        }
        registerReflection(test)
        test.setOnClickListener {
            if (haptics) {
                vibrate(18)
                test.text = "Buzz!"
            } else {
                test.text = "Off"
            }
            test.postDelayed({ test.text = "Test" }, 900L)
        }
        item.addView(test, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })

        val sw = effectToggle(haptics) { enabled ->
            haptics = enabled
            prefs.edit().putBoolean("haptics", enabled).apply()
            if (enabled) vibrate(10)
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
            prefs.edit().putString("motion", value).putBoolean("motion_reduced", value == "reduced").apply()
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
            val lightMode = Color.luminance(color(R.color.bg)) > 0.5f
            background = if (lightMode) elevatedGlassPill() else glassPill()
            clipChildren = false
            // Pair restrained elevation with a visible warm lower rim for
            // intentional depth on Light Mode's low-contrast cream surface.
            if (lightMode) {
                elevation = dpf(5)
                translationZ = dpf(1)
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = false
            }
        }
        registerReflection(seg)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val indicator = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(withAlpha(color(R.color.accent), 0.88f), color(R.color.accent), withAlpha(color(R.color.accent), 0.94f))).apply {
                cornerRadius = dpf(999)
                setStroke(dp(1), withAlpha(Color.WHITE, 0.38f))
            }
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

    private fun registerReflection(view: View, circular: Boolean = false) {
        reflectionTargets += view to circular
    }

    private fun glassPill(): Drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(withAlpha(Color.WHITE, 0.10f), color(R.color.glassFill), withAlpha(color(R.color.accent2), 0.10f))).apply {
        cornerRadius = dpf(999)
        setStroke(dp(1), withAlpha(Color.WHITE, 0.52f))
    }

    /**
     * Light Mode only: a restrained warm-olive rim sits just below the glass
     * surface, creating visible physical separation without a fixed white stripe.
     * The moving reflection overlay remains responsible for the animated shine.
     */
    private fun elevatedGlassPill(): Drawable {
        val light = GradientDrawable().apply {
            cornerRadius = dpf(999)
            setColor(withAlpha(color(R.color.accent), 0.16f))
            setStroke(dp(1), withAlpha(color(R.color.accent2), 0.26f))
        }
        val surface = glassPill()
        val texture = FineGlassTextureDrawable(density)
        return LayerDrawable(arrayOf(light, surface, texture)).apply {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 0, 0, 0, dp(3))
            setLayerInset(2, dp(1), dp(1), dp(1), dp(4))
        }
    }

    private fun glassCircle(withBorder: Boolean): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color(R.color.glassFill))
        if (withBorder) setStroke(dp(1), color(R.color.glassStroke))
    }

    private fun pill(c: Int): Drawable = GradientDrawable().apply { cornerRadius = dpf(999); setColor(c) }
    private fun rounded(c: Int, r: Float): Drawable = GradientDrawable().apply { cornerRadius = dpf(r); setColor(c) }

    internal fun withAlpha(c: Int, alpha: Float): Int =
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
        val glow = AppearanceTouchGlow(density, dpf(24f))
        fun useGlow() = glowable && glassLevel != "off" && !reduced
        if (glowable) foreground = glow
        var anim: ValueAnimator? = null
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (themeCards.any { it === v }) {
                        themeTapScreen = e.rawX to e.rawY
                    }
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
                            duration = 520L
                            interpolator = EASE
                            addUpdateListener { a ->
                                val t = a.animatedValue as Float
                                // Begin tight at the finger, then spread outward while softening.
                                glow.radiusScale = 0.10f + 0.90f * t
                                val intensity = if (glassLevel == "subtle") 128 else 255
                                glow.glowAlpha = (intensity * (0.82f - 0.22f * t)).toInt()
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

    // ============================ Background glows and loop clock ============================

    private fun addGlows() {
        // Match the Settings page's three independently moving ambient gradients and starfield.
        val lightMode = Color.luminance(color(R.color.bg)) > 0.5f
        data class G(val c: Int, val alpha: Float, val dur: Long, val gravity: Int, val top: Int, val bottom: Int, val start: Int, val end: Int, val x0: Float, val x1: Float, val y0: Float, val y1: Float)
        val list = listOf(
            G(if (lightMode) 0xFF89945A.toInt() else color(R.color.accent2), if (lightMode) 0.88f else 0.52f, 5600, Gravity.TOP or Gravity.START, -70, 0, -90, 0, -74f, 96f, -28f, 118f),
            G(if (lightMode) 0xFFB7A879.toInt() else color(R.color.hero), if (lightMode) 0.82f else 0.48f, 7200, Gravity.TOP or Gravity.END, 240, 0, 0, -110, 88f, -96f, -92f, 62f),
            G(if (lightMode) 0xFFD5C9A5.toInt() else color(R.color.accent), if (lightMode) 0.68f else 0.32f, 9000, Gravity.BOTTOM or Gravity.START, 0, 60, 0, 0, -92f, 76f, 72f, -104f)
        )
        list.forEach { g ->
            val v = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(g.c, withAlpha(g.c, 0f))
                    gradientRadius = dpf(if (lightMode) 250 else 140)
                }
                alpha = g.alpha
                tag = g.alpha
            }
            ambientGlows.add(v)
            v.visibility = if (glassLevel != "off") View.VISIBLE else View.GONE
            val lp = FrameLayout.LayoutParams(dp(280), dp(280), g.gravity).apply {
                topMargin = dp(g.top); bottomMargin = dp(g.bottom)
                if ((g.gravity and Gravity.END) == Gravity.END) rightMargin = dp(g.end) else leftMargin = dp(g.start)
            }
            root.addView(v, 0, lp)
            if (!reduced) {
                ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, dpf(g.x0), dpf(g.x1)),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dpf(g.y0), dpf(g.y1)),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.24f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.24f)).apply {
                    duration = g.dur
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    interpolator = EASE_IN_OUT
                    start()
                    currentPlayTime = SystemClock.uptimeMillis() % (g.dur * 2L)
                }
            }
        }
        ambientStarfield = AppearanceStarfieldView(this, reduced, lightMode)
        root.addView(ambientStarfield, 0, FrameLayout.LayoutParams(-1, -1))
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
            glow.visibility = if (factor > 0f) View.VISIBLE else View.GONE
        }
        ambientStarfield?.alpha = factor
        ambientStarfield?.visibility = if (factor > 0f) View.VISIBLE else View.GONE
        reflectionOverlay?.alpha = factor
        // Glass reflections belong to Glass Effects, not the separate ambient Visual Effects toggle.
        reflectionOverlay?.visibility = if (factor > 0f) View.VISIBLE else View.INVISIBLE
    }

    private val loops = mutableListOf<(Long) -> Unit>()
    private var loopRunning = false
    private val loopFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopRunning) return
            // Use Android's monotonic uptime as the shared animation clock across Settings and Appearance.
            val t = SystemClock.uptimeMillis()
            for (l in loops) l(t)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun addLoop(block: (Long) -> Unit) { loops.add(block) }

    override fun onResume() {
        super.onResume()
        if (!loopRunning && loops.isNotEmpty() && !reduced) {
            loopRunning = true
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
        private val EASE_IN_OUT = PathInterpolator(0.42f, 0f, 0.58f, 1f)
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}

/**
 * Shared moving reflection layer for registered glass controls.
 * Uses the page clock, avoiding separate animators for each control.
 */
private class AppearanceReflectionOverlay(context: Context, private val activity: AppearanceActivity) : View(context) {
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()
    private val rect = android.graphics.RectF()
    private val location = IntArray(2)
    private val overlayLocation = IntArray(2)
    private val density = resources.displayMetrics.density
    private val lightMode = Color.luminance(activity.color(R.color.bg)) > 0.5f
    private var elapsed = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setElapsed(time: Long) { elapsed = time; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activity.glassLevel == "off" || activity.reduced || !isShown) return
        getLocationOnScreen(overlayLocation)
        val phase = (elapsed % 7600L).toFloat() / 7600f
        // Keep the original surface colours dominant; dark mode needs a much
        // quieter sheen so charcoal and olive never wash out to grey or pale green.
        val baseAlpha = when {
            lightMode && activity.glassLevel == "subtle" -> 0.36f
            lightMode -> 0.56f
            activity.glassLevel == "subtle" -> 0.18f
            else -> 0.30f
        }
        val glowColor = if (lightMode) Color.rgb(255, 251, 229) else Color.rgb(207, 218, 185)
        val edgeColor = if (lightMode) Color.rgb(255, 255, 246) else Color.rgb(165, 179, 139)

        activity.reflectionTargets.forEach { (target, circular) ->
            if (!target.isShown || target.width <= 0 || target.height <= 0 || target.alpha <= 0.01f) return@forEach
            target.getLocationOnScreen(location)
            val left = (location[0] - overlayLocation[0]).toFloat()
            val top = (location[1] - overlayLocation[1]).toFloat()
            rect.set(left, top, left + target.width, top + target.height)
            val radius = if (circular) minOf(rect.width(), rect.height()) / 2f else rect.height() / 2f
            path.reset()
            path.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(path)

            // Follow the nearest moving ambient gradient; its position determines
            // where the reflection is brightest on this control.
            val targetCx = location[0] + target.width / 2f
            val targetCy = location[1] + target.height / 2f
            var nearestGlow: View? = null
            var nearestDistance = Float.MAX_VALUE
            var lightX = 0.5f
            var lightY = 0.18f
            activity.ambientGlows.forEach { glow ->
                if (glow.visibility != View.VISIBLE || glow.alpha <= 0.01f) return@forEach
                val glowLoc = IntArray(2)
                glow.getLocationOnScreen(glowLoc)
                val gx = glowLoc[0] + glow.width / 2f
                val gy = glowLoc[1] + glow.height / 2f
                val dx = gx - targetCx
                val dy = gy - targetCy
                val dist = dx * dx + dy * dy
                if (dist < nearestDistance) {
                    nearestDistance = dist
                    nearestGlow = glow
                    // Project the source direction across the control surface.
                    lightX = (0.5f + dx / maxOf(target.width.toFloat() * 1.6f, 1f)).coerceIn(-0.15f, 1.15f)
                    lightY = (0.5f + dy / maxOf(target.height.toFloat() * 2.2f, 1f)).coerceIn(-0.20f, 1.20f)
                }
            }
            // The page clock adds a very slow drift to the light position so the
            // reflection remains alive even while a gradient is near its turning point.
            val drift = 0.08f * kotlin.math.sin(phase * 2f * PI.toFloat())
            lightX = (lightX + drift).coerceIn(-0.15f, 1.15f)
            val isThemeCard = activity.themeCards.any { it === target }
            val isBackControl = target === activity.backButton
            val controlAlpha = when {
                isThemeCard -> baseAlpha * 0.42f
                isBackControl -> baseAlpha * 0.72f
                else -> baseAlpha
            }
            val effectiveAlpha = controlAlpha * target.alpha * (nearestGlow?.alpha ?: 1f).coerceIn(0f, 1f)

            // Full-surface radial reflection: the light source follows the ambient
            // gradients, with a smooth falloff in both directions and no hard band.
            reflectionPaint.shader = RadialGradient(
                rect.left + rect.width() * lightX,
                rect.top + rect.height() * lightY,
                maxOf(rect.width(), rect.height()) * 1.12f,
                intArrayOf(activity.withAlpha(glowColor, 0.34f * effectiveAlpha),
                    activity.withAlpha(glowColor, 0.13f * effectiveAlpha),
                    activity.withAlpha(glowColor, 0f)),
                floatArrayOf(0f, 0.38f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, reflectionPaint)
            reflectionPaint.shader = null

            // Reflect the perimeter without drawing a competing second ring on cards.
            edgePaint.style = Paint.Style.STROKE
            edgePaint.color = activity.withAlpha(edgeColor, (if (isThemeCard) 0.24f else 0.40f) * effectiveAlpha)
            edgePaint.strokeWidth = maxOf(0.7f * density, 1f)
            rect.inset(edgePaint.strokeWidth / 2f, edgePaint.strokeWidth / 2f)
            canvas.drawRoundRect(rect, radius, radius, edgePaint)
            if (!isThemeCard) {
                edgePaint.color = activity.withAlpha(if (lightMode) 0xFF8B9468.toInt() else Color.BLACK,
                    (if (lightMode) 0.08f else 0.10f) * effectiveAlpha)
                edgePaint.strokeWidth = 0.7f * density
                canvas.drawLine(rect.left + radius * 0.35f, rect.bottom - edgePaint.strokeWidth,
                    rect.right - radius * 0.35f, rect.bottom - edgePaint.strokeWidth, edgePaint)
            }
            rect.inset(-edgePaint.strokeWidth / 2f, -edgePaint.strokeWidth / 2f)
            edgePaint.style = Paint.Style.FILL
            canvas.restoreToCount(save)
        }
    }
}

/** Settings-matched, lightweight animated starfield. */
private class AppearanceStarfieldView(context: Context, private val reducedMotion: Boolean, private val lightMode: Boolean) : View(context) {
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
                (0.65f + random.nextFloat() * 1.35f) * density, random.nextFloat() * 6.28318f,
                0.12f + random.nextFloat() * 0.34f,
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
            currentPlayTime = SystemClock.uptimeMillis() % duration
        }
    }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        stars.forEachIndexed { index, star ->
            val drift = progress * star.speed
            val x = ((star.x + drift * 0.10f) % 1f) * w
            val y = ((star.y + drift) % 1f) * h
            val twinkle = 0.72f + 0.28f * kotlin.math.sin(progress * 6.28318f + star.phase)
            val a = (star.alpha * twinkle * (if (reducedMotion) 0.52f else 1f) * 255f).toInt().coerceIn(0,255)
            paint.color = if (lightMode) { if (index % 4 == 0) Color.argb(a,183,168,121) else Color.argb(a,112,126,77) } else Color.argb(a,218,222,165)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x,y,star.radius,paint)
            if (star.radius > 1.55f * density) {
                paint.alpha = (a * 0.22f).toInt(); paint.strokeWidth = 0.7f * density
                canvas.drawLine(x-star.radius*2.2f,y,x+star.radius*2.2f,y,paint)
                canvas.drawLine(x,y-star.radius*2.2f,x,y+star.radius*2.2f,paint)
            }
        }
    }
}

/** Touch light for the Appearance page: 180dp radial glow from rgba(137,148,90,0.5) to transparent at 70%. */
private class AppearanceTouchGlow(
    private val density: Float,
    private val cornerRadius: Float
) : Drawable() {
    var centreX = 0f
    var centreY = 0f
    var radiusScale = 1f
        set(value) { field = value; invalidateSelf() }
    var glowAlpha = 0
        set(value) { field = value; invalidateSelf() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = android.graphics.Path()
    private val clipRect = android.graphics.RectF()
    override fun draw(canvas: Canvas) {
        if (glowAlpha <= 0) return
        val bounds = bounds
        clipRect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        val r = 180f * density * radiusScale
        paint.shader = RadialGradient(centreX, centreY, r,
            intArrayOf(0x8089945A.toInt(), 0x5589945A, 0x0089945A),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = glowAlpha
        canvas.drawCircle(centreX, centreY, r, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { glowAlpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Deterministic satin-glass microtexture for elevated Light Mode pills.
 * Sparse, low-contrast grains catch the moving reflection without a fixed band,
 * image asset, random generation, or continuous texture animation.
 */
private class FineGlassTextureDrawable(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = android.graphics.RectF()
    private val path = android.graphics.Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        path.reset()
        path.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, android.graphics.Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(path)

        val stepX = (6.5f * density).coerceAtLeast(4f)
        val stepY = (5.5f * density).coerceAtLeast(4f)
        var row = 0
        var y = rect.top + stepY * 0.5f
        while (y < rect.bottom) {
            var col = 0
            var x = rect.left + stepX * 0.5f + if (row % 2 == 0) 0f else stepX * 0.42f
            while (x < rect.right) {
                // Stable pseudo-random pattern: naturally irregular, but never flickers.
                val seed = kotlin.math.abs(kotlin.math.sin((col * 12.9898 + row * 78.233) * 0.73)).toFloat()
                if (seed > 0.38f) {
                    val alpha = (3f + seed * 9f).toInt().coerceIn(3, 12)
                    paint.color = Color.argb(alpha, if ((row + col) % 3 == 0) 255 else 91,
                        if ((row + col) % 3 == 0) 255 else 103, if ((row + col) % 3 == 0) 246 else 61)
                    paint.style = Paint.Style.FILL
                    val radius = (0.35f + seed * 0.42f) * density
                    canvas.drawCircle(x, y, radius, paint)
                }
                col++
                x += stepX
            }
            row++
            y += stepY
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
