package com.savan.drishyam

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

class SettingsHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 34.dp())
            setBackgroundColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.bg))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, 12.dp())
            addView(root)
        }
        setContentView(scroll)
        installScrollBounce(scroll)
        animatePageEntrance(scroll)

        val back = TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.fg))
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_pill)
            layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
            isClickable = true
            isFocusable = true
            pressFx()
            setOnClickListener { finish() }
            contentDescription = getString(R.string.back_desc)
        }
        root.addView(back)

        val eyebrow = addText(root, "YOUR SPACE, YOUR WAY", 11f, R.color.accent, 22, 6)
        eyebrow.letterSpacing = 0.16f
        val heading = addText(root, "Settings", 36f, R.color.fg, 0, 4)
        heading.typeface = ResourcesCompat.getFont(this, R.font.ericaone)
        addText(root, "Make Drishyam feel like yours.", 14f, R.color.mute, 0, 18)

        val versionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_card)
        }
        val brand = addText(versionCard, "DRISHYAM  /  MOSS & OLIVE", 11f, R.color.accent, 0, 8)
        brand.letterSpacing = 0.08f
        val version = addText(versionCard, "A more immersive way to see.", 20f, R.color.fg, 0, 5)
        version.typeface = ResourcesCompat.getFont(this, R.font.montserrat_semibold)
        addText(versionCard, "Version ${BuildConfig.VERSION_NAME} · by Savan", 12f, R.color.mute, 0, 0)
        root.addView(versionCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp() })

        val section = addText(root, "PREFERENCES", 11f, R.color.accent, 26, 8)
        section.letterSpacing = 0.14f
        addCategory(root, "Appearance", "Theme, haptics and visual effects", "appearance", "◐")
        addCategory(root, "Browsing preferences", "Choose your default feed order", "preferences", "↕")
        addCategory(root, "Storage & cache", "Manage temporary image thumbnails", "storage", "⌁")

        val aboutSection = addText(root, "ABOUT DRISHYAM", 11f, R.color.accent, 26, 8)
        aboutSection.letterSpacing = 0.14f
        addCategory(root, "What's new & credits", "Release notes, fonts and photo licences", "about", "✦")
        addActionCategory(root, "Our story", "Meet the people and ideas behind Drishyam", "◎") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_about", true))
            finish()
        }

        addText(root, "Made with curiosity. Built for photography.", 12f, R.color.mute, 28, 0).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun addCategory(root: LinearLayout, title: String, desc: String, section: String, glyph: String) {
        addActionCategory(root, title, desc, glyph) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", section))
        }
    }

    private fun addActionCategory(root: LinearLayout, title: String, desc: String, glyph: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 15.dp(), 16.dp(), 15.dp())
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_row)
            isClickable = true
            isFocusable = true
            pressFx()
            setOnClickListener { action() }
        }
        val icon = TextView(this).apply {
            text = glyph
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.accent))
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_pill)
            layoutParams = LinearLayout.LayoutParams(46.dp(), 46.dp())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(icon)
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 0, 0, 0)
        }
        val titleView = addText(copy, title, 16f, R.color.fg, 0, 4)
        titleView.typeface = ResourcesCompat.getFont(this, R.font.montserrat_semibold)
        addText(copy, desc, 12f, R.color.mute, 0, 0)
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val arrow = TextView(this).apply {
            text = "›"
            textSize = 28f
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.mute))
        }
        card.addView(arrow)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp() })
    }

    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, top: Int, bottom: Int): TextView {
        val tv = TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, if (size >= 17f) R.font.montserrat_semibold else R.font.poppins_regular)
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, color))
            setPadding(0, top.dp(), 0, bottom.dp())
        }
        parent.addView(tv)
        return tv
    }
}
