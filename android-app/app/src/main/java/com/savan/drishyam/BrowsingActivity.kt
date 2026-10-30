package com.savan.drishyam

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Browsing preferences for Plan - MilkyWay.
 * Values are persisted locally; provider use is gated by these values in the feed pipeline.
 */
class BrowsingActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("drishyam_browsing", MODE_PRIVATE) }
    private val density get() = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private fun dp(value: Float) = (value * density).toInt()
    private fun c(id: Int) = ContextCompat.getColor(this, id)
    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    private var backgroundLayer: UniversalBackgroundView? = null
    private val ambientGlows = mutableListOf<View>()
    private val glassCards = mutableListOf<View>()
    private val toggleRefreshers = mutableListOf<() -> Unit>()
    private val glassPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "glass_level" || key == "low_fx" || key == "motion" || key == "motion_reduced") {
            refreshAtmosphere()
            refreshGlassComponents()
        }
    }

    private fun refreshAtmosphere() {
        backgroundLayer?.refreshGlassLevel()
        val factor = when (glassLevel(this)) { "off" -> 0f; "subtle" -> 0.45f; else -> 1f }
        ambientGlows.forEach { glow ->
            val base = glow.tag as? Float ?: 1f
            glow.alpha = base * factor
            glow.visibility = if (factor > 0f) View.VISIBLE else View.GONE
        }
    }

    private fun refreshGlassComponents() {
        glassCards.forEach { it.background = cardBackground() }
        toggleRefreshers.forEach { it.invoke() }
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(glassPreferenceListener)
        refreshAtmosphere()
    }

    override fun onPause() {
        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(glassPreferenceListener)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        setTheme(R.style.Theme_Drishyam_Portal)
        super.onCreate(savedInstanceState)
        applyActivityTransitionPreference(this)
        val reduced = !motionEnabled(this)
        val glassLevel = glassLevel(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply {
            setBackgroundColor(c(R.color.bg))
            clipChildren = false
        }
        // Reuse Appearance's live background plus the same three drifting ambient glows as Settings.
        UniversalBackgroundView.attach(root)
        backgroundLayer = root.findViewWithTag("drishyam-universal-background")
        addBrowsingGlows(root)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(150))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        PortalTransition.enter(this)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            content.setPadding(dp(20), top + dp(32), dp(20), dp(150))
            root.findViewWithTag<View>("browsing-back")?.let { button ->
                (button.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.topMargin = top + dp(36)
                    button.layoutParams = lp
                }
            }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)

        // Match the Settings/Appearance floating Back control exactly: 48dp,
        // borderless glass circle, same anchored position and touch treatment.
        val back = ImageView(this).apply {
            tag = "browsing-back"
            setImageResource(R.drawable.ic_back)
            imageTintList = android.content.res.ColorStateList.valueOf(c(R.color.fg))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c(R.color.glassFill))
            }
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_desc)
            pressFx()
            setOnClickListener { view -> PortalTransition.close(this@BrowsingActivity, view) }
        }
        content.addView(View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(64)))
        root.addView(back, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(20)
            topMargin = dp(36)
        })
        root.bringChildToFront(back)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = (scrollY.toFloat() / dp(84f)).coerceIn(0f, 1f)
            back.alpha = 1f - progress
            back.isClickable = progress < 0.95f
            back.isFocusable = progress < 0.95f
            back.scaleX = 1f - 0.08f * progress
            back.scaleY = 1f - 0.08f * progress
        }

        content.addView(label("PERSONALISE YOUR DISCOVERY", 11f, R.color.accent, 10, 5))
        content.addView(label("Browsing", 36f, R.color.fg, 0, 2, true))
        content.addView(label("Choose where Drishyam discovers photographs and how it explores them.", 14f, R.color.mute, 0, 18))

        content.addView(sectionTitle("Photo Sources"))
        val sourcesCard = card()
        sourcesCard.addView(label("Choose which providers Drishyam may use. Disabled providers must never be used as fallbacks.", 12f, R.color.mute, 0, 10))
        addSourceSwitch(sourcesCard, "Wikimedia Commons", "Openly licensed photographs from Wikimedia Commons.", "source_wikimedia", true, "Primary")
        addDivider(sourcesCard)
        addSourceSwitch(sourcesCard, "Openverse", "Search across multiple open-licence collections.", "source_openverse", true, "Secondary")
        addDivider(sourcesCard)
        addSourceSwitch(sourcesCard, "Unsplash", "An additional photography source.", "source_unsplash", true, "Backup")
        content.addView(sourcesCard, match())

        content.addView(sectionTitle("Quick Preferences"))
        val quick = card()
        addChoiceRow(quick, "Image quality", "Choose the preferred image size.", "image_quality",
            arrayOf("Standard", "High", "Raw"), "High")
        addDivider(quick)
        addPreferenceSwitch(quick, "Avoid previously seen images", "Reduce repeated photographs where the feed can identify them.", "avoid_seen", true)
        addDivider(quick)
        val selectedCategory = prefs.getString("default_category", "For You") ?: "For You"
        val categoryValue = TextView(this).apply {
            text = selectedCategory
            textSize = 11f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold)
            setTextColor(c(R.color.fg))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = elevatedSelectorBackground()
            elevation = dp(5).toFloat()
            translationZ = dp(1).toFloat()
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = false
            isClickable = true; isFocusable = true
            pressFx()
        }
        val categoryRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true; isFocusable = true
        }
        categoryRow.addView(label("Default discovery category", 13f, R.color.fg, 0, 7, true))
        val categoryValueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { showCategoryPicker(categoryValue) }
        }
        categoryValueRow.addView(label("Currently selected", 11f, R.color.mute, 0, 0).apply {
            setPadding(0, 0, dp(10), 0)
        })
        val openCategoryPicker = { showCategoryPicker(categoryValue) }
        categoryValue.setOnClickListener { openCategoryPicker() }
        categoryValueRow.addView(categoryValue, LinearLayout.LayoutParams(-2, -2))
        categoryRow.addView(categoryValueRow)
        categoryValueRow.setOnClickListener { openCategoryPicker() }
        categoryRow.setOnClickListener { openCategoryPicker() }
        quick.addView(categoryRow)
        content.addView(quick, match())

        content.addView(sectionTitle("Advanced Browsing"))
        val advancedCard = card()
        val advancedToggle = TextView(this).apply {
            text = if (prefs.getBoolean("advanced_expanded", false)) "Advanced controls   ▴" else "Advanced controls   ▾"
            textSize = 14f
            setTextColor(c(R.color.fg))
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold)
            setPadding(dp(2), dp(8), dp(2), dp(8))
            isClickable = true
            isFocusable = true
        }
        val advancedBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (prefs.getBoolean("advanced_expanded", false)) View.VISIBLE else View.GONE }
        advancedToggle.setOnClickListener {
            val expanded = advancedBody.visibility != View.VISIBLE
            advancedBody.visibility = if (expanded) View.VISIBLE else View.GONE
            advancedToggle.text = if (expanded) "Advanced controls   ▴" else "Advanced controls   ▾"
            prefs.edit().putBoolean("advanced_expanded", expanded).apply()
        }
        advancedCard.addView(advancedToggle)
        addDivider(advancedCard)
        advancedCard.addView(advancedBody)
        addChoiceRow(advancedBody, "Preferred orientation", "Choose the shape of photographs to prioritise.", "orientation",
            arrayOf("Portrait", "Landscape", "Random", "Square"), "Portrait")
        addDivider(advancedBody)
        addPreferenceSwitch(advancedBody, "Allow mature-content results", "Keep disabled to avoid sources that classify content as mature.", "allow_mature", false)
        addDivider(advancedBody)
        addPreferenceSwitch(advancedBody, "Load more automatically", "Continue pagination as you approach the end of the feed.", "auto_pagination", true)
        addDivider(advancedBody)
        addInfoRow(advancedBody, "Provider fallback", "Drishyam should try only enabled sources, in the configured order. If every enabled source fails, it will show a retry message.")
        addDivider(advancedBody)
        addInfoRow(advancedBody, "Licences and attribution", "Each photograph should retain its source, creator and licence details. Open the photo details to review attribution before reuse.")
        content.addView(advancedCard, match())

        content.addView(label("Your choices are saved on this device.", 11f, R.color.mute, 0, 16).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            translationX = dp(8).toFloat()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun addBrowsingGlows(root: FrameLayout) {
        val light = Color.luminance(c(R.color.bg)) > 0.5f
        val specs = listOf(
            floatArrayOf(-70f, -90f, -28f, 118f, 5600f),
            floatArrayOf(240f, 88f, -92f, 62f, 7200f),
            floatArrayOf(0f, -92f, 72f, -104f, 9000f)
        )
        specs.forEachIndexed { index, g ->
            val tint = if (light) intArrayOf(0xFF89945A.toInt(), 0xFFB7A879.toInt(), 0xFFD5C9A5.toInt())[index]
                else intArrayOf(c(R.color.accent2), c(R.color.hero), c(R.color.accent))[index]
            val baseAlpha = if (light) floatArrayOf(0.88f, 0.82f, 0.68f)[index]
                else floatArrayOf(0.52f, 0.48f, 0.32f)[index]
            val glow = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(tint, withAlpha(tint, 0f))
                    gradientRadius = dp(if (light) 250 else 140).toFloat()
                }
                tag = baseAlpha
            }
            ambientGlows.add(glow)
            root.addView(glow, 0, FrameLayout.LayoutParams(dp(280), dp(280),
                intArrayOf(Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END, Gravity.BOTTOM or Gravity.START)[index]).apply {
                topMargin = dp(g[0].toInt())
                if (index == 1) rightMargin = dp(-110) else if (index == 0) leftMargin = dp(-90)
                if (index == 2) bottomMargin = dp(60)
            })
            if (motionEnabled(this)) {
                android.animation.ObjectAnimator.ofPropertyValuesHolder(glow,
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, dp(g[1].toInt()).toFloat(), dp(g[1].toInt() * -1).toFloat()),
                    android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dp(g[2].toInt()).toFloat(), dp(g[3].toInt()).toFloat()),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.24f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.24f)).apply {
                    duration = g[4].toLong()
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
        refreshAtmosphere()
    }

    private fun cardBackground(): android.graphics.drawable.Drawable =
        GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(c(R.color.glassFill))
            setStroke(dp(1), c(R.color.glassStroke))
        }

    private fun match() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(0) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = cardBackground()
        glassCards.add(this)
    }
    private fun pillBackground() : android.graphics.drawable.Drawable {
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.WHITE, 0.22f), c(R.color.glassFill), withAlpha(c(R.color.accent2), 0.12f))).apply {
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), withAlpha(Color.WHITE, 0.58f))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(base)).apply {
            setLayerInset(0, 0, 0, 0, dp(2))
        }
    }

    private fun elevatedSelectorBackground(): android.graphics.drawable.Drawable {
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.WHITE, 0.34f), c(R.color.glassFill), withAlpha(c(R.color.accent2), 0.16f))).apply {
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), withAlpha(Color.WHITE, 0.66f))
        }
        val lowerRim = GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), withAlpha(c(R.color.accent2), 0.22f))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(lowerRim, base)).apply {
            setLayerInset(0, 0, dp(2), 0, 0)
        }
    }
    private fun label(textValue: String, size: Float, colorId: Int, top: Int = 0, bottom: Int = 0, bold: Boolean = false) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(c(colorId))
        typeface = androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity,
            if (bold) R.font.poppins_extrabold else if (size <= 12f) R.font.montserrat_semibold else R.font.poppins_regular)
        setPadding(0, dp(top), 0, dp(bottom))
    }
    private fun sectionTitle(value: String) = label(value, 18f, R.color.fg, 22, 10, true)
    private fun addSourceSwitch(parent: LinearLayout, title: String, subtitle: String, key: String, default: Boolean, role: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        text.addView(label(title, 14f, R.color.fg, 0, 2, true))
        text.addView(label("$role  ·  $subtitle", 11f, R.color.mute))
        val toggle = glassToggle(prefs.getBoolean(key, default), "Enable $title") { checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
        row.addView(text)
        row.addView(toggle, LinearLayout.LayoutParams(dp(52), dp(30)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
    }
    private fun addPreferenceSwitch(parent: LinearLayout, title: String, subtitle: String, key: String, default: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        text.addView(label(title, 13f, R.color.fg, 0, 2, true))
        text.addView(label(subtitle, 11f, R.color.mute))
        val toggle = glassToggle(prefs.getBoolean(key, default), title) { checked ->
            prefs.edit().putBoolean(key, checked).apply()
        }
        row.addView(text); row.addView(toggle, LinearLayout.LayoutParams(dp(52), dp(30)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
    }
    private fun glassToggle(initial: Boolean, description: String, onChanged: (Boolean) -> Unit): FrameLayout {
        var checked = initial
        val thumb = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), 0x66FFFFFF)
            }
            elevation = dp(2).toFloat()
        }
        val track = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
            contentDescription = "$description: ${if (checked) "On" else "Off"}"
            isClickable = true
            isFocusable = true
            background = toggleTrack(checked)
            setOnClickListener {
                checked = !checked
                background = toggleTrack(checked)
                contentDescription = "$description: ${if (checked) "On" else "Off"}"
                thumb.animate().cancel()
                thumb.animate().translationX(if (checked) dp(22).toFloat() else 0f)
                    .setDuration(300).setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start()
                onChanged(checked)
            }
        }
        track.addView(thumb, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(3) })
        thumb.translationX = if (checked) dp(22).toFloat() else 0f
        toggleRefreshers.add { track.background = toggleTrack(checked) }
        track.pressFx()
        return track
    }

    private fun toggleTrack(checked: Boolean): android.graphics.drawable.Drawable {
        val glassOn = glassLevel(this) != "off"
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                if (checked) withAlpha(c(R.color.accent), 0.88f) else withAlpha(c(R.color.mute), 0.68f),
                if (checked) c(R.color.accent) else withAlpha(c(R.color.mute), 0.72f),
                if (checked) withAlpha(c(R.color.accent), 0.94f) else withAlpha(c(R.color.mute), 0.78f)
            )).apply {
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), withAlpha(Color.WHITE, if (glassOn) 0.72f else 0.24f))
        }
        if (!glassOn) return base
        // A restrained top-edge sheen gives the switch the same glass depth as Appearance.
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(Color.WHITE, 0.24f), withAlpha(Color.WHITE, 0.035f), Color.TRANSPARENT)).apply {
            cornerRadius = dp(999).toFloat()
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(base, sheen))
    }

    private data class SegmentTarget(val left: Int, val top: Int, val width: Int, val height: Int)

    private fun moveSegmentIndicator(indicator: View, target: SegmentTarget, reduced: Boolean) {
        val lp = indicator.layoutParams as FrameLayout.LayoutParams
        val from = SegmentTarget(lp.leftMargin, lp.topMargin, lp.width, lp.height)
        indicator.animate().cancel()
        if (reduced) {
            lp.leftMargin = target.left; lp.topMargin = target.top
            lp.width = target.width; lp.height = target.height
            indicator.layoutParams = lp
            return
        }
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340L
            interpolator = android.view.animation.PathInterpolator(0.22f, 0.72f, 0.18f, 1f)
            addUpdateListener { value ->
                val t = value.animatedValue as Float
                val params = indicator.layoutParams as FrameLayout.LayoutParams
                params.leftMargin = (from.left + (target.left - from.left) * t).toInt()
                params.topMargin = (from.top + (target.top - from.top) * t).toInt()
                params.width = (from.width + (target.width - from.width) * t).toInt()
                params.height = (from.height + (target.height - from.height) * t).toInt()
                indicator.layoutParams = params
            }
        }
        animator.start()
    }

    private fun addChoiceRow(parent: LinearLayout, title: String, subtitle: String, key: String, choices: Array<String>, default: String) {
        var selected = prefs.getString(key, default) ?: default
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        row.addView(label(title, 13f, R.color.fg, 0, 2, true))
        row.addView(label(subtitle, 11f, R.color.mute, 0, 8))

        val twoRows = choices.size == 4
        val columns = if (twoRows) 2 else choices.size
        val grid = FrameLayout(this).apply {
            background = if (Color.luminance(c(R.color.bg)) > 0.5f) elevatedSelectorBackground() else pillBackground()
            elevation = if (Color.luminance(c(R.color.bg)) > 0.5f) dp(5).toFloat() else dp(2).toFloat()
            translationZ = if (Color.luminance(c(R.color.bg)) > 0.5f) dp(1).toFloat() else 0f
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = false
            clipChildren = false
            clipToPadding = false
        }
        val indicator = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(withAlpha(c(R.color.accent), 0.88f), c(R.color.accent), withAlpha(c(R.color.accent), 0.94f))).apply {
                cornerRadius = dp(999).toFloat()
                setStroke(dp(1), withAlpha(Color.WHITE, 0.48f))
            }
            elevation = dp(3).toFloat()
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            // Keep option labels above the moving glass pill without adding
            // permanent borders or shadows to unselected options.
            elevation = dp(4).toFloat()
        }
        val buttons = mutableListOf<TextView>()
        val buttonRows = choices.toList().chunked(columns)
        buttonRows.forEachIndexed { rowIndex, rowChoices ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowChoices.forEach { choice ->
                val button = TextView(this).apply {
                    text = choice; tag = choice
                    textSize = 13f; isSingleLine = true; includeFontPadding = false
                    gravity = Gravity.CENTER
                    typeface = androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold)
                    setPadding(dp(8), dp(22), dp(8), dp(22))
                    minHeight = dp(68)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(999).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    elevation = 0f
                    isClickable = true; isFocusable = true
                    pressFx()
                    setOnClickListener {
                        if (choice == selected) return@setOnClickListener
                        selected = choice
                        prefs.edit().putString(key, choice).apply()
                        buttons.forEach { b ->
                            val active = b.tag == selected
                            b.setTextColor(c(if (active) R.color.bg else R.color.mute))
                            b.setTypeface(
                                androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold),
                                if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                            )
                        }
                        grid.post {
                            moveSegmentIndicator(indicator, insetTarget(targetFor(this, line, column), dp(2), dp(4)), !motionEnabled(this@BrowsingActivity))
                        }
                    }
                }
                buttons.add(button)
                line.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            }
            if (rowChoices.size < columns) line.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            column.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                if (rowIndex > 0) topMargin = dp(6)
            })
        }
        grid.addView(indicator, FrameLayout.LayoutParams(0, 0))
        grid.addView(column, FrameLayout.LayoutParams(-1, -2))
        fun paintChoiceLabels() {
            buttons.forEach { b ->
                val active = b.tag == selected
                b.setTextColor(c(if (active) R.color.bg else R.color.mute))
                b.setTypeface(
                    androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold),
                    if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
        }
        fun placeInitial() {
            val active = buttons.firstOrNull { it.tag == selected } ?: return
            val parentLine = active.parent as LinearLayout
            val target = insetTarget(targetFor(active, parentLine, column), dp(2), dp(4))
            val lp = indicator.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin = target.left; lp.topMargin = target.top
            lp.width = target.width; lp.height = target.height
            indicator.layoutParams = lp
        }
        paintChoiceLabels()
        grid.post { placeInitial() }
        row.addView(grid, LinearLayout.LayoutParams(-1, -2))
        parent.addView(row)
    }

    private fun insetTarget(target: SegmentTarget, horizontal: Int, vertical: Int): SegmentTarget =
        SegmentTarget(target.left + horizontal, target.top + vertical,
            (target.width - horizontal * 2).coerceAtLeast(1),
            (target.height - vertical * 2).coerceAtLeast(1))

    private fun targetFor(button: View, line: View, column: View): SegmentTarget =
        SegmentTarget(
            column.left + line.left + button.left,
            column.top + line.top + button.top,
            button.width,
            button.height
        )

    private fun showCategoryPicker(summary: TextView) {
        val categories = listOf("For You", "Nature", "Wildlife", "Forest", "Mountains", "Ocean", "Travel", "Architecture", "Abstract")
        var selected = prefs.getString("default_category", "For You") ?: "For You"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(c(R.color.glassDialog))
                setStroke(dp(1), c(R.color.glassStroke))
            }
        }
        panel.addView(label("Default category", 20f, R.color.fg, 0, 4, true))
        panel.addView(label("Choose your starting point", 12f, R.color.mute, 0, 12))
        val grid = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val indicator = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(withAlpha(c(R.color.accent), 0.92f), c(R.color.accent), withAlpha(c(R.color.accent), 0.96f))).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), withAlpha(Color.WHITE, 0.48f))
            }
            elevation = dp(3).toFloat()
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(4).toFloat()
        }
        val buttons = mutableListOf<TextView>()
        categories.chunked(2).forEachIndexed { rowIndex, pair ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { category ->
                val option = TextView(this).apply {
                    text = category; tag = category
                    textSize = 14f; gravity = Gravity.CENTER
                    typeface = androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold)
                    setPadding(dp(8), dp(14), dp(8), dp(14))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(Color.TRANSPARENT)
                    }
                    elevation = 0f
                    isClickable = true; isFocusable = true
                    pressFx()
                    setOnClickListener {
                        if (category == selected) return@setOnClickListener
                        selected = category
                        prefs.edit().putString("default_category", category).apply()
                        summary.text = category
                        buttons.forEach { b ->
                            val active = b.tag == selected
                            b.setTextColor(c(if (active) R.color.bg else R.color.fg))
                            b.setTypeface(
                                androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold),
                                if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                            )
                        }
                        grid.post {
                            moveSegmentIndicator(indicator, insetTarget(targetFor(this, line, column), dp(5), dp(4)), !motionEnabled(this@BrowsingActivity))
                        }
                    }
                }
                buttons.add(option)
                line.addView(option, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (line.childCount > 0) leftMargin = dp(8)
                })
            }
            if (pair.size == 1) line.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            column.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                if (rowIndex > 0) topMargin = dp(8)
            })
        }
        grid.addView(indicator, FrameLayout.LayoutParams(0, 0))
        grid.addView(column, FrameLayout.LayoutParams(-1, -2))
        fun paintCategoryLabels() {
            buttons.forEach { b ->
                val active = b.tag == selected
                b.setTextColor(c(if (active) R.color.bg else R.color.fg))
                b.setTypeface(
                    androidx.core.content.res.ResourcesCompat.getFont(this@BrowsingActivity, R.font.montserrat_semibold),
                    if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
        }
        paintCategoryLabels()
        grid.post {
            buttons.firstOrNull { it.tag == selected }?.let { active ->
                val line = active.parent as LinearLayout
                val target = insetTarget(targetFor(active, line, column), dp(5), dp(4))
                val lp = indicator.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = target.left; lp.topMargin = target.top
                lp.width = target.width; lp.height = target.height
                indicator.layoutParams = lp
            }
        }
        panel.addView(grid)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(panel).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val width = (resources.displayMetrics.widthPixels - dp(40)).coerceAtMost(dp(420))
            dialog.window?.setLayout(width, -2)
        }
        dialog.show()
    }

    private fun addClickRow(parent: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        row.addView(label(title, 13f, R.color.fg, 0, 2, true))
        row.addView(label(subtitle, 11f, R.color.mute))
        parent.addView(row)
    }
    private fun addInfoRow(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(label(title, 13f, R.color.fg, 10, 2, true))
        parent.addView(label(subtitle, 11f, R.color.mute, 0, 8))
    }
    private fun addDivider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(c(R.color.glassStroke)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) })
    }
}
