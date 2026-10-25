package com.savan.drishyam

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * S1 Settings hub. Appearance remains a link to the existing S2 entry point;
 * browsing/storage/about are intentionally prototype toasts until their phases.
 */
class SettingsHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 150.dp())
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
            textSize = 30f
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
        val heading = addText(root, "Settings", 38f, R.color.fg, 6, 4)
        heading.letterSpacing = -0.02f
        heading.typeface = ResourcesCompat.getFont(this, R.font.poppins_bold)
        addText(root, "Make Drishyam feel like yours.", 14f, R.color.mute, 0, 6)

        val hero = MaterialCardView(this).apply {
            radius = 30.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke)
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer,
                ContextCompat.getColor(this@SettingsHubActivity, R.color.surface)))
            setContentPadding(22.dp(), 22.dp(), 22.dp(), 22.dp())
        }
        val heroBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = addText(heroBody, "DRISHYAM  /  MOSS & OLIVE", 11f, R.color.accent, 0, 10)
        brand.letterSpacing = 0.16f
        val heroTitle = addText(heroBody, "A more expressive way to see.", 24f, R.color.fg, 0, 8)
        heroTitle.typeface = ResourcesCompat.getFont(this, R.font.montserrat_bold)
        addText(heroBody, "Shape your space, tune the details, keep the focus on photography.", 13f, R.color.mute, 0, 14)
        val version = addText(heroBody, "VERSION 2.7.16  ·  BY SAVAN", 10f, R.color.mute, 0, 0)
        version.letterSpacing = 0.06f
        hero.addView(heroBody)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18.dp() })

        val section = addText(root, "PREFERENCES", 11f, R.color.accent, 26, 8)
        section.letterSpacing = 0.14f
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(tile("◐", "Appearance", "System", true) {
            startActivity(Intent(this@SettingsHubActivity, SettingsActivity::class.java).putExtra("section", "appearance"))
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = 6.dp() })
        row1.addView(tile("↕", "Browsing", "Hot · India on", false) {
            Toast.makeText(this@SettingsHubActivity, "Browsing page: next prototype step", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = 6.dp() })
        tiles.addView(row1, LinearLayout.LayoutParams(-1, 176.dp()))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2.addView(tile("⌁", "Storage", "42 MB cached", false) {
            Toast.makeText(this@SettingsHubActivity, "Storage page: next prototype step", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = 6.dp() })
        row2.addView(tile("✦", "About", "Version 2.7.16", false) {
            Toast.makeText(this@SettingsHubActivity, "About page: next prototype step", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = 6.dp() })
        tiles.addView(row2, LinearLayout.LayoutParams(-1, 176.dp()).apply { topMargin = 12.dp() })
        root.addView(tiles)

        val quick = addText(root, "QUICK ACTIONS", 11f, R.color.accent, 26, 8)
        quick.letterSpacing = 0.14f
        addAction(root, "↻", "Check for updates", "Find the latest verified release") {
            startActivity(Intent(this, UpdatesActivity::class.java))
        }
        addAction(root, "✧", "What’s new", "Release notes, release by release") {
            startActivity(Intent(this, WhatsNewActivity::class.java))
        }

        val more = addText(root, "MORE", 11f, R.color.accent, 26, 8)
        more.letterSpacing = 0.14f
        addOurStory(root)
        addText(root, "Made with curiosity. Built for photography.", 12f, R.color.mute, 24, 0).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun tile(glyph: String, title: String, value: String, emphasize: Boolean, action: () -> Unit): View {
        val card = MaterialCardView(this).apply {
            radius = 26.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = ContextCompat.getColor(this@SettingsHubActivity, R.color.glassStroke)
            setCardBackgroundColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.surface))
            isClickable = true
            isFocusable = true
            pressFx()
            setOnClickListener { action() }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(18.dp(), 18.dp(), 12.dp(), 16.dp())
        }
        body.addView(TextView(this).apply {
            text = glyph
            textSize = 23f
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.accent))
        })
        body.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_semibold)
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.fg))
            setPadding(0, 5.dp(), 0, 4.dp())
        })
        body.addView(TextView(this).apply {
            text = value
            textSize = 11f
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.mute))
            maxLines = 1
        })
        card.addView(body)
        return card
    }

    private fun addAction(root: LinearLayout, glyph: String, title: String, desc: String, action: () -> Unit) {
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
        card.addView(TextView(this).apply {
            text = glyph
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.accent))
            background = ContextCompat.getDrawable(this@SettingsHubActivity, R.drawable.glass_pill)
            layoutParams = LinearLayout.LayoutParams(46.dp(), 46.dp())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14.dp(), 0, 0, 0) }
        copy.addView(TextView(this).apply {
            text = title; textSize = 15f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_semibold)
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.fg))
        })
        copy.addView(TextView(this).apply {
            text = desc; textSize = 12f
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.mute))
            setPadding(0, 4.dp(), 0, 0)
        })
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply { text = "›"; textSize = 28f; setTextColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.mute)) })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp() })
    }

    private fun addOurStory(root: LinearLayout) {
        val card = MaterialCardView(this).apply {
            radius = 34.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 0
            minimumHeight = 214.dp()
            setCardBackgroundColor(android.graphics.Color.rgb(31, 42, 22))
            isClickable = true
            isFocusable = true
            pressFx()
            setOnClickListener { startActivity(Intent(this@SettingsHubActivity, MainActivity::class.java).putExtra("open_about", true)) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 22.dp(), 20.dp(), 22.dp())
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply {
            text = "OUR STORY"; textSize = 10f; letterSpacing = 0.14f
            setTextColor(0xCCFFFFFF.toInt())
        })
        copy.addView(TextView(this).apply {
            text = "The people & ideas behind Drishyam"; textSize = 24f
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity, R.font.montserrat_bold)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 7.dp(), 0, 6.dp())
        })
        copy.addView(TextView(this).apply {
            text = "Started by one curious photographer, one frame at a time."; textSize = 13f
            setTextColor(0xE6FFFFFF.toInt())
        })
        body.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val photo = ImageView(this).apply {
            setImageResource(R.drawable.savan)
            contentDescription = "Savan, lead developer"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(ContextCompat.getColor(this@SettingsHubActivity, R.color.surface)) }
            clipToOutline = true
        }
        body.addView(photo, LinearLayout.LayoutParams(132.dp(), 132.dp()).apply { leftMargin = 8.dp() })
        card.addView(body)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp() })
    }

    private fun addText(parent: LinearLayout, value: String, size: Float, color: Int, top: Int, bottom: Int): TextView {
        val tv = TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@SettingsHubActivity,
                if (size >= 17f) R.font.montserrat_semibold else R.font.poppins_regular)
            setTextColor(ContextCompat.getColor(this@SettingsHubActivity, color))
            setPadding(0, top.dp(), 0, bottom.dp())
        }
        parent.addView(tv)
        return tv
    }
}
