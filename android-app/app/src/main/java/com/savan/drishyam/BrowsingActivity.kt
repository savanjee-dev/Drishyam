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
    private fun c(id: Int) = ContextCompat.getColor(this, id)
    private var backgroundLayer: UniversalBackgroundView? = null
    private val glassPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "glass_level" || key == "low_fx" || key == "motion" || key == "motion_reduced") {
            refreshAtmosphere()
        }
    }

    private fun refreshAtmosphere() {
        backgroundLayer?.refreshGlassLevel()
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
        // Reuse the same animated ambient background layer used by Appearance.
        UniversalBackgroundView.attach(root)
        backgroundLayer = root.findViewWithTag("drishyam-universal-background")
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
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)

        // Match the Settings/Appearance floating Back control exactly: 48dp,
        // borderless glass circle, same anchored position and touch treatment.
        val back = ImageView(this).apply {
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
            attachBrowsingPress(glassLevel != "off" && !reduced)
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

        content.addView(sectionTitle("Photo sources"))
        val sourcesCard = card()
        sourcesCard.addView(label("Choose which providers Drishyam may use. Disabled providers must never be used as fallbacks.", 12f, R.color.mute, 0, 10))
        addSourceSwitch(sourcesCard, "Wikimedia Commons", "Openly licensed photographs from Wikimedia Commons.", "source_wikimedia", true, "Primary")
        addDivider(sourcesCard)
        addSourceSwitch(sourcesCard, "Openverse", "Search across multiple open-licence collections.", "source_openverse", true, "Secondary")
        addDivider(sourcesCard)
        addSourceSwitch(sourcesCard, "Unsplash", "An additional photography source.", "source_unsplash", true, "Backup")
        content.addView(sourcesCard, match())

        content.addView(sectionTitle("Quick preferences"))
        val quick = card()
        addChoiceRow(quick, "Image quality", "Choose the preferred image size.", "image_quality",
            arrayOf("Standard", "High"), "High")
        addDivider(quick)
        addPreferenceSwitch(quick, "Avoid previously seen images", "Reduce repeated photographs where the feed can identify them.", "avoid_seen", true)
        addDivider(quick)
        addClickRow(quick, "Default discovery category", "Choose the category shown when opening the feed.") {
            val categories = arrayOf("For You", "Nature", "Forest", "Mountains", "Wildlife", "Architecture", "Travel", "Ocean", "Abstract")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Default category")
                .setSingleChoiceItems(categories, categories.indexOf(prefs.getString("default_category", "For You"))) { dialog, which ->
                    prefs.edit().putString("default_category", categories[which]).apply()
                    dialog.dismiss()
                    recreate()
                }.setNegativeButton("Cancel", null).show()
        }
        content.addView(quick, match())

        content.addView(sectionTitle("Advanced browsing"))
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
            arrayOf("Any", "Portrait", "Landscape", "Square"), "Portrait")
        addDivider(advancedBody)
        addPreferenceSwitch(advancedBody, "Allow mature-content results", "Keep disabled to avoid sources that classify content as mature.", "allow_mature", false)
        addDivider(advancedBody)
        addPreferenceSwitch(advancedBody, "Load more automatically", "Continue pagination as you approach the end of the feed.", "auto_pagination", true)
        addDivider(advancedBody)
        addInfoRow(advancedBody, "Provider fallback", "Drishyam should try only enabled sources, in the configured order. If every enabled source fails, it will show a retry message.")
        addDivider(advancedBody)
        addInfoRow(advancedBody, "Licences and attribution", "Each photograph should retain its source, creator and licence details. Open the photo details to review attribution before reuse.")
        content.addView(advancedCard, match())

        content.addView(label("Your choices are saved on this device.", 11f, R.color.mute, 0, 16))
    }

    private fun attachBrowsingPress(enabled: Boolean) {
        foreground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().cancel()
                    view.animate().scaleX(0.94f).scaleY(0.94f).alpha(if (enabled) 0.82f else 1f).setDuration(90).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
                }
            }
            false
        }
    }

    private fun match() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(0) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        // Same surface fill, radius and border as Appearance's glassCard().
        background = GradientDrawable().apply {
            cornerRadius = dp(28).toFloat()
            setColor(c(R.color.glassFill))
            setStroke(dp(1), c(R.color.glassStroke))
        }
    }
    private fun pillBackground() = GradientDrawable().apply {
        cornerRadius = dp(999).toFloat()
        setColor(c(R.color.glassFill))
        setStroke(dp(1), c(R.color.glassStroke))
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
        return track
    }

    private fun toggleTrack(checked: Boolean): android.graphics.drawable.Drawable {
        val glassOn = glassLevel(this) != "off"
        val base = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                if (checked) withAlpha(c(R.color.accent), 0.94f) else withAlpha(c(R.color.accent2), 0.28f),
                if (checked) c(R.color.accent) else withAlpha(c(R.color.accent2), 0.30f),
                if (checked) withAlpha(c(R.color.accent), 0.94f) else withAlpha(c(R.color.accent2), 0.32f)
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

    private fun addChoiceRow(parent: LinearLayout, title: String, subtitle: String, key: String, choices: Array<String>, default: String) {
        val current = prefs.getString(key, default) ?: default
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        row.addView(label(title, 13f, R.color.fg, 0, 2, true))
        row.addView(label(subtitle, 11f, R.color.mute, 0, 8))
        val choicesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        choices.forEach { choice ->
            val selected = choice == current
            val item = TextView(this).apply {
                text = choice
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(c(if (selected) R.color.surface else R.color.fg))
                background = GradientDrawable().apply {
                    cornerRadius = dp(999).toFloat()
                    setColor(c(if (selected) R.color.accent else R.color.glassFill))
                    setStroke(dp(1), c(R.color.glassStroke))
                }
                setPadding(dp(10), dp(9), dp(10), dp(9))
                isClickable = true; isFocusable = true
                setOnClickListener {
                    prefs.edit().putString(key, choice).apply()
                    recreate()
                }
            }
            choicesRow.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply { if (choicesRow.childCount > 0) leftMargin = dp(5) })
        }
        row.addView(choicesRow)
        parent.addView(row)
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
