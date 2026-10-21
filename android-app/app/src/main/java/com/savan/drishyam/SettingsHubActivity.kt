package com.savan.drishyam

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 36.dp())
            setBackgroundColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.bg))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll)
        installScrollBounce(scroll)
        animatePageEntrance(scroll)
        val header = TextView(this).apply {
            text = "‹     Drishyam Settings"
            textSize = 27f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_bold)
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(this@SettingsHubActivity, R.color.accent)))
            setPadding(0, 8.dp(), 0, 10.dp())
            isClickable = true
            setOnClickListener { finish() }
        }
        root.addView(header)
        addText(root, "Manage your experience", 14f, R.color.mute, 0, 18)
        addCategory(root, "Appearance", "Theme, liquid-glass intensity, haptics and effects", "appearance")
        addCategory(root, "Preferences", "Default feed order and browsing preferences", "preferences")
        addCategory(root, "Storage & cache", "Clear temporary image thumbnails", "storage")
        addCategory(root, "About Drishyam", "What's new, credits and licences", "about")
        addText(root, "Drishyam 2.4.3 · Designed and built by Savan", 12f, R.color.mute, 24, 0).gravity = Gravity.CENTER
    }

    private fun addCategory(root: LinearLayout, title: String, desc: String, section: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_row)
            isClickable = true; isFocusable = true; pressFx()
            setOnClickListener { startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", section)) }
        }
        addText(card, title, 18f, R.color.fg, 0, 5)
        addText(card, desc, 13f, R.color.mute, 0, 0)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp() })
    }

    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, top: Int, bottom: Int): TextView {
        val tv = TextView(this).apply {
            text = value; textSize = size
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@SettingsHubActivity, if (size >= 17f) R.font.montserrat_semibold else R.font.poppins_regular)
            val attr = when (color) {
                R.color.fg -> com.google.android.material.R.attr.colorOnSurface
                R.color.mute -> com.google.android.material.R.attr.colorOnSurfaceVariant
                R.color.accent -> com.google.android.material.R.attr.colorPrimary
                R.color.accent2 -> com.google.android.material.R.attr.colorSecondary
                else -> com.google.android.material.R.attr.colorOnSurface
            }
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, attr, ContextCompat.getColor(this@SettingsHubActivity, color)))
            setPadding(0, top.dp(), 0, bottom.dp())
        }
        parent.addView(tv)
        return tv
    }
}
