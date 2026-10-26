package com.savan.drishyam

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        installScrollBounce(findViewById(R.id.settingsScroll))
        val startupPrefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val reducedMotionAtOpen = startupPrefs.getBoolean("motion_reduced", false) || startupPrefs.getString("motion", "standard") == "reduced"
        if (!reducedMotionAtOpen) animatePageEntrance(findViewById(android.R.id.content))
        if (motionEnabled(this) && !reducedMotionAtOpen) {
            val motionTargets = listOf(
                R.id.sectionAppearance, R.id.cardTheme, R.id.sectionEffects, R.id.cardHaptics,
                R.id.cardLowFx, R.id.sectionFeed, R.id.cardSort, R.id.sectionAbout,
                R.id.rowClear, R.id.rowWhatsNew, R.id.rowCredits
            )
            motionTargets.forEachIndexed { index, id ->
                val target = findViewById<View>(id)
                target.alpha = 0f
                target.translationY = 12.dp().toFloat()
                target.animate().alpha(1f).translationY(0f)
                    .setStartDelay((45L + index * 32L).coerceAtMost(360L))
                    .setDuration(240L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }
        val prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val updateRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp())
            background = androidx.core.content.ContextCompat.getDrawable(this@SettingsActivity, R.drawable.glass_row)
            isClickable = true; isFocusable = true; pressFx()
            addView(TextView(this@SettingsActivity).apply {
                text = "Check for updates"
                textSize = 16f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.montserrat_semibold)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF.toInt()))
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "Find the latest Drishyam release and changelog"
                textSize = 12f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.poppins_regular)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFFBBBBBB.toInt()))
                setPadding(0, 4.dp(), 0, 0)
            })
            setOnClickListener { startActivity(android.content.Intent(this@SettingsActivity, UpdatesActivity::class.java)) }
        }
        (findViewById<View>(R.id.rowCredits).parent as? ViewGroup)?.let { parent ->
            parent.addView(updateRow, parent.indexOfChild(findViewById(R.id.rowCredits)) + 1,
                (findViewById<View>(R.id.rowCredits).layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    ViewGroup.MarginLayoutParams(lp.width, lp.height).apply { topMargin = 12.dp() }
                } ?: ViewGroup.MarginLayoutParams(-1, -2))
        }

        findViewById<View>(R.id.settingsBack).apply { pressFx(); setOnClickListener { finish() } }

        // The Settings hub opens focused subpages. Keep the existing controls and
        // their preference keys, but show only the requested category on each page.
        val section = intent.getStringExtra("section") ?: "all"
        fun show(viewId: Int, visible: Boolean) { findViewById<View>(viewId).visibility = if (visible) View.VISIBLE else View.GONE }
        if (section != "all") {
            val appearance = section == "appearance"
            val preferences = section == "preferences"
            val storage = section == "storage"
            val about = section == "about"
            val heading = findViewById<TextView>(R.id.settingsHeading)
            heading.text = when (section) {
                "appearance" -> "Appearance"
                "preferences" -> "Preferences"
                "storage" -> "Storage & cache"
                else -> "About Drishyam"
            }
            show(R.id.settingsHero, !appearance)
            show(R.id.sectionAppearance, appearance); show(R.id.cardTheme, appearance)
            show(R.id.sectionEffects, appearance); show(R.id.cardHaptics, appearance); show(R.id.cardLowFx, appearance)
            show(R.id.sectionFeed, preferences); show(R.id.cardSort, preferences)
            show(R.id.sectionAbout, storage || about)
            show(R.id.rowClear, storage); show(R.id.rowWhatsNew, about); show(R.id.rowCredits, about); updateRow.visibility = if (about) View.VISIBLE else View.GONE
            show(R.id.settingsVersion, about)
            if (appearance) {
                val pageRoot = findViewById<android.widget.ScrollView>(R.id.settingsScroll).getChildAt(0) as LinearLayout
                pageRoot.setPadding(20.dp(), 18.dp(), 20.dp(), 150.dp())
                val topbar = findViewById<View>(R.id.settingsBack).parent as ViewGroup
                heading.visibility = View.GONE
                val intro = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(4.dp(), 22.dp(), 4.dp(), 6.dp())
                }
                intro.addView(TextView(this).apply {
                    text = "SETTINGS"; textSize = 11f; letterSpacing = 0.16f
                    typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.montserrat_semibold)
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.accent))
                })
                intro.addView(TextView(this).apply {
                    text = "Appearance"; textSize = 38f; letterSpacing = -0.02f
                    typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.poppins_extrabold)
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.fg))
                    setPadding(0, 6.dp(), 0, 4.dp())
                    includeFontPadding = false
                })
                intro.addView(TextView(this).apply {
                    text = "Theme, haptics and visual effects."; textSize = 14f
                    typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.poppins_regular)
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.mute))
                    setPadding(0, 0, 0, 6.dp())
                })
                pageRoot.addView(intro, pageRoot.indexOfChild(findViewById(R.id.settingsHero)))
                // The prototype uses a standalone header stack under the circular back control.
                (findViewById<View>(R.id.settingsBack) as? android.widget.ImageView)?.apply {
                    background = androidx.core.content.ContextCompat.getDrawable(this@SettingsActivity, R.drawable.glass_pill)
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                }
                topbar.setPadding(0, 0, 0, 0)
            }
        }

        // Theme pills
        val themes = listOf(R.id.themeSystem to "System", R.id.themeDark to "Dark", R.id.themeLight to "Light")
        fun paintTheme() {
            val current = prefs.getString("theme", "System")
            themes.forEach { (id, v) -> findViewById<TextView>(id).styleSeg(current == v) }
        }
        themes.forEach { (id, v) ->
            findViewById<TextView>(id).apply {
                pressFx()
                setOnClickListener {
                    prefs.edit().putString("theme", v).apply()
                    MainActivity.applyTheme(this@SettingsActivity)
                    paintTheme()
                }
            }
        }
        paintTheme()

        // Prototype v9 theme cards replace the older text-only segmented row.
        val themeContainer = findViewById<LinearLayout>(R.id.cardTheme)
        themeContainer.getChildAt(2)?.visibility = View.GONE
        val themeCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 14.dp(), 0, 0)
        }
        fun refreshThemeCards() {
            val current = prefs.getString("theme", "System")
            for (i in 0 until themeCards.childCount) {
                val card = themeCards.getChildAt(i) as? com.google.android.material.card.MaterialCardView ?: continue
                val mode = listOf("System", "Dark", "Light")[i]
                card.strokeWidth = if (current == mode) 2.dp() else 1.dp()
                card.strokeColor = androidx.core.content.ContextCompat.getColor(this,
                    if (current == mode) R.color.accent2 else R.color.glassStroke)
                card.findViewWithTag<View>("theme-check")?.visibility = if (current == mode) View.VISIBLE else View.GONE
            }
        }
        listOf("System", "Dark", "Light").forEachIndexed { index, mode ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 24.dp().toFloat()
                cardElevation = 0f
                strokeWidth = 1.dp()
                strokeColor = androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.glassStroke)
                setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.glassFill))
                setContentPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                isClickable = true; isFocusable = true; pressFx()
            }
            val frame = android.widget.FrameLayout(this)
            frame.addView(ThemeMockView(this, mode), android.widget.FrameLayout.LayoutParams(-1, 92.dp()))
            val check = ThemeCheckView(this).apply { tag = "theme-check"; visibility = View.GONE }
            frame.addView(check, android.widget.FrameLayout.LayoutParams(20.dp(), 20.dp(), android.view.Gravity.TOP or android.view.Gravity.END))
            val label = TextView(this).apply {
                text = mode; textSize = 12f; gravity = android.view.Gravity.CENTER
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.poppins_semibold)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF1B2117.toInt()))
                setPadding(0, 8.dp(), 0, 0)
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(frame); addView(label) }
            card.addView(content)
            card.setOnClickListener {
                prefs.edit().putString("theme", mode).apply()
                MainActivity.applyTheme(this@SettingsActivity)
                paintTheme(); refreshThemeCards()
            }
            themeCards.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) leftMargin = 10.dp()
            })
        }
        themeContainer.addView(themeCards)
        refreshThemeCards()

        // Phase S2: build the prototype's single Effects card with Glass effects, Haptics and Motion.
        val effectsParent = findViewById<View>(R.id.cardLowFx).parent as ViewGroup
        val oldHapticsCard = findViewById<LinearLayout>(R.id.cardHaptics)
        val oldLowFxCard = findViewById<View>(R.id.cardLowFx)
        val hapticsSwitch = findViewById<MaterialSwitch>(R.id.swHaptics)
        (hapticsSwitch.parent as? ViewGroup)?.removeView(hapticsSwitch)
        oldHapticsCard.visibility = View.GONE
        oldLowFxCard.visibility = View.GONE

        fun s2Text(value: String, size: Float, muted: Boolean = false, font: Int? = null) = TextView(this).apply {
            text = value
            textSize = size
            includeFontPadding = false
            typeface = androidx.core.content.res.ResourcesCompat.getFont(
                this@SettingsActivity,
                font ?: if (size >= 14f) R.font.poppins_semibold else R.font.poppins_regular
            )
            setTextColor(androidx.core.content.ContextCompat.getColor(
                this@SettingsActivity, if (muted) R.color.mute else R.color.fg
            ))
        }
        fun addS2Divider(parent: LinearLayout) {
            parent.addView(View(this).apply {
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(this@SettingsActivity, R.color.glassStroke))
            }, LinearLayout.LayoutParams(-1, 1.dp()))
        }
        fun s2Segment(title: String, description: String, options: List<String>, selected: String, onPick: (String) -> Unit): LinearLayout {
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12.dp(), 0, 12.dp())
            }
            block.addView(s2Text(title, 14f))
            block.addView(s2Text(description, 12f, true).apply {
                setPadding(0, 2.dp(), 0, 0)
                setLineSpacing(0f, 1.4f)
            })
            val shell = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                background = androidx.core.content.ContextCompat.getDrawable(this@SettingsActivity, R.drawable.glass_pill)
            }
            val buttons = linkedMapOf<String, TextView>()
            fun paint(value: String) {
                buttons.forEach { (label, button) ->
                    val active = label == value
                    button.background = androidx.core.content.ContextCompat.getDrawable(
                        this@SettingsActivity, if (active) R.drawable.accent_pill else R.drawable.glass_clear
                    )
                    button.setTextColor(androidx.core.content.ContextCompat.getColor(
                        this@SettingsActivity, if (active) R.color.bg else R.color.mute
                    ))
                }
            }
            options.forEachIndexed { i, option ->
                val button = s2Text(option, 13f, false, R.font.montserrat_semibold).apply {
                    gravity = android.view.Gravity.CENTER
                    setPadding(8.dp(), 11.dp(), 8.dp(), 11.dp())
                    isClickable = true; isFocusable = true; pressFx()
                    setOnClickListener {
                        if (hapticsOn(this@SettingsActivity)) s2Vibrate(8)
                        paint(option)
                        onPick(option)
                    }
                }
                buttons[option] = button
                shell.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            }
            block.addView(shell, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp() })
            paint(selected)
            return block
        }
        val effectsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 6.dp(), 16.dp(), 6.dp())
            background = androidx.core.content.ContextCompat.getDrawable(this@SettingsActivity, R.drawable.glass_row)
        }
        val glassDefault = prefs.getString("glass_level", "full") ?: "full"
        effectsCard.addView(s2Segment(
            "Glass effects",
            "Turn glass off to save battery on older phones.",
            listOf("Off", "Subtle", "Full"),
            glassDefault.replaceFirstChar { it.uppercase() }
        ) { choice ->
            prefs.edit().putString("glass_level", choice.lowercase()).putBoolean("low_fx", choice == "Off").apply()
            recreate()
        })
        addS2Divider(effectsCard)

        val hapticsBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp(), 0, 12.dp())
        }
        hapticsBlock.addView(s2Text("Haptics", 14f))
        hapticsBlock.addView(s2Text("Light vibration on taps and switches.", 12f, true).apply { setPadding(0, 2.dp(), 0, 0) })
        val hapticsControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 12.dp(), 0, 0)
        }
        val testHaptics = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Test"; textSize = 12f; isAllCaps = false; insetTop = 0; insetBottom = 0
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsActivity, R.font.montserrat_semibold)
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            cornerRadius = 999.dp()
            strokeWidth = 1.dp()
            setOnClickListener {
                if (hapticsOn(this@SettingsActivity)) {
                    s2Vibrate(8)
                    Toast.makeText(this@SettingsActivity, "Haptics tested", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@SettingsActivity, "Haptics are off", Toast.LENGTH_SHORT).show()
            }
        }
        hapticsControls.addView(testHaptics, LinearLayout.LayoutParams(-2, -2))
        hapticsControls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        hapticsSwitch.isChecked = hapticsOn(this)
        hapticsSwitch.layoutParams = LinearLayout.LayoutParams(52.dp(), 30.dp())
        hapticsSwitch.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("haptics", on).apply()
        }
        hapticsControls.addView(hapticsSwitch)
        hapticsBlock.addView(hapticsControls)
        effectsCard.addView(hapticsBlock)
        addS2Divider(effectsCard)

        val motionDefault = if (android.provider.Settings.Global.getInt(contentResolver, "animator_duration_scale", 1) == 0) "Reduced" else "Standard"
        val savedMotion = prefs.getString("motion", motionDefault.lowercase()) ?: motionDefault.lowercase()
        effectsCard.addView(s2Segment(
            "Motion",
            "Reduced replaces bouncy motion with simple fades.",
            listOf("Standard", "Reduced"),
            savedMotion.replaceFirstChar { it.uppercase() }
        ) { choice ->
            prefs.edit().putString("motion", choice.lowercase()).putBoolean("motion_reduced", choice == "Reduced").apply()
            if (choice == "Reduced") {
                // Reduced motion immediately removes decorative motion and delays on this page.
                findViewById<View>(R.id.settingsScroll).animate().cancel()
            }
        })
        effectsParent.addView(effectsCard, (effectsParent as ViewGroup).indexOfChild(oldLowFxCard) + 1,
            ViewGroup.MarginLayoutParams(-1, -2).apply { topMargin = 0 })
        effectsCard.visibility = if (section == "all" || section == "appearance") View.VISIBLE else View.GONE

        // Keep the legacy switch wired for existing non-S2 settings flows.
        val lowFxSwitch = findViewById<MaterialSwitch>(R.id.swLowFx)
        lowFxSwitch.isChecked = lowFx(this)
        lowFxSwitch.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("low_fx", on).apply()
            recreate()
        }

        // Default feed order pills
        val sorts = listOf(R.id.sortHotS to "hot", R.id.sortNewS to "new", R.id.sortTopS to "top")
        fun paintSort() {
            val current = prefs.getString("sort", "hot")
            sorts.forEach { (id, v) -> findViewById<TextView>(id).styleSeg(current == v) }
        }
        sorts.forEach { (id, v) ->
            findViewById<TextView>(id).apply {
                pressFx()
                setOnClickListener { prefs.edit().putString("sort", v).apply(); paintSort() }
            }
        }
        paintSort()

        // Action rows
        // Use native Material dialogs for settings actions. These are anchored to the
        // current Activity and avoid the old custom overlay accidentally dismissing the screen.
        findViewById<View>(R.id.rowClear).apply {
            pressFx()
            setOnClickListener {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.clear_title)
                    .setMessage(R.string.clear_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear_action) { _, _ ->
                        try {
                            Glide.get(this@SettingsActivity).clearMemory()
                            Thread {
                                runCatching { Glide.get(applicationContext).clearDiskCache() }
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed) {
                                        Toast.makeText(this@SettingsActivity, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.start()
                        } catch (_: Exception) {
                            Toast.makeText(this@SettingsActivity, getString(R.string.cache_clear_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            }
        }
        findViewById<View>(R.id.rowWhatsNew).apply {
            pressFx()
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity, WhatsNewActivity::class.java))
            }
        }
        findViewById<View>(R.id.rowCredits).apply {
            pressFx()
            setOnClickListener {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.credits_title)
                    .setMessage(R.string.credits_body)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }

        findViewById<TextView>(R.id.settingsVersion).text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!PopupStack.dismiss()) super.onBackPressed()
    }
}

private fun s2Vibrate(durationMs: Long) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
    } else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator)
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
    }
}

private class ThemeMockView(context: Context, private val mode: String) : View(context) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        val d = resources.displayMetrics.density
        val bg = when (mode) { "Dark" -> 0xFF0A0D08.toInt(); "Light" -> 0xFFF3F1E7.toInt(); else -> 0xFFF3F1E7.toInt() }
        p.color = bg; canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),16f*d,16f*d,p)
        val half = width * .5f
        if (mode == "System") { p.color=0xFF0A0D08.toInt(); canvas.drawRect(half,0f,width.toFloat(),height.toFloat(),p) }
        p.color = if (mode == "Dark") 0xFF89945A.toInt() else 0xFF89945A.toInt()
        canvas.drawRoundRect(8f*d,9f*d,width*.56f,16f*d,4f*d,4f*d,p)
        p.color = if (mode == "Dark") 0xFF1B2117.toInt() else 0xFFFFFCF4.toInt()
        canvas.drawRoundRect(8f*d,24f*d,width*.40f,68f*d,10f*d,10f*d,p)
        canvas.drawRoundRect(width*.52f,24f*d,width-8f*d,68f*d,10f*d,10f*d,p)
        p.color = if (mode == "Dark") 0xFF89945A.toInt() else 0xFF414D2D.toInt()
        canvas.drawRoundRect(8f*d, height-20f*d, width-8f*d,height-8f*d,8f*d,8f*d,p)
    }
}
private class ThemeCheckView(context: Context) : View(context) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND; color = 0xFFF3F1E7.toInt() }
    override fun onDraw(canvas: android.graphics.Canvas) {
        val d = resources.displayMetrics.density
        p.style = android.graphics.Paint.Style.FILL; p.color = 0xFF414D2D.toInt()
        canvas.drawCircle(width/2f,height/2f,width/2f,p)
        p.style = android.graphics.Paint.Style.STROKE
        val path = android.graphics.Path().apply { moveTo(width*.25f,height*.52f); lineTo(width*.43f,height*.69f); lineTo(width*.76f,height*.32f) }
        canvas.drawPath(path,p)
    }
}
