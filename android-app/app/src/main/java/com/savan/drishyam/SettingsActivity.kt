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
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        installScrollBounce(findViewById(R.id.settingsScroll))
        animatePageEntrance(findViewById(android.R.id.content))
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
            setOnClickListener { UpdateManager.check(this@SettingsActivity, findViewById(R.id.glassSource), manual = true) }
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
            findViewById<TextView>(R.id.settingsHeading).text = when (section) {
                "appearance" -> "Appearance"
                "preferences" -> "Preferences"
                "storage" -> "Storage & cache"
                else -> "About Drishyam"
            }
            val appearance = section == "appearance"
            val preferences = section == "preferences"
            val storage = section == "storage"
            val about = section == "about"
            show(R.id.sectionAppearance, appearance); show(R.id.cardTheme, appearance)
            show(R.id.sectionEffects, appearance); show(R.id.cardHaptics, appearance); show(R.id.cardLowFx, appearance)
            show(R.id.sectionFeed, preferences); show(R.id.cardSort, preferences)
            show(R.id.sectionAbout, storage || about)
            show(R.id.rowClear, storage); show(R.id.rowWhatsNew, about); show(R.id.rowCredits, about); updateRow.visibility = if (about) View.VISIBLE else View.GONE
            show(R.id.settingsVersion, about)
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

        // Toggles
        val haptic = findViewById<MaterialSwitch>(R.id.swHaptics)
        haptic.isChecked = hapticsOn(this)
        haptic.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("haptics", on).apply() }

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
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(R.string.whats_new_title)
                    .setMessage(R.string.whats_new_body)
                    .setPositiveButton(R.string.ok, null)
                    .show()
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

        findViewById<TextView>(R.id.settingsVersion).text = getString(R.string.settings_version)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!PopupStack.dismiss()) super.onBackPressed()
    }
}
