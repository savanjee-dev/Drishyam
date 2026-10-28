package com.savan.drishyam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors

/** Offline-first release history. Cached notes render immediately; network refresh runs in background. */
class WhatsNewActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var loading: TextView
    private var restoreScrollY = 0
    private var selectedVersion = BuildConfig.VERSION_NAME

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        applyActivityTransitionPreference(this)
        DynamicColors.applyToActivityIfAvailable(this)
        restoreScrollY = savedInstanceState?.getInt("release_history_scroll") ?: 0

        val surface = color(com.google.android.material.R.attr.colorSurface, R.color.bg)
        val onSurface = color(com.google.android.material.R.attr.colorOnSurface, R.color.fg)
        val variant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.mute)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = surface
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !night()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !night()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 8.dp(), 16.dp(), 12.dp())
        }
        header.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "‹"
            textSize = 30f
            minWidth = 48.dp()
            insetTop = 0
            insetBottom = 0
            contentDescription = "Back"
            setOnClickListener { PortalTransition.close(this@WhatsNewActivity, this) }
        }, LinearLayout.LayoutParams(52.dp(), 52.dp()))
        header.addView(TextView(this).apply {
            text = "Release notes"
            textSize = 11f
            letterSpacing = .14f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@WhatsNewActivity, R.font.montserrat_semibold)
            setTextColor(variant)
            setPadding(2.dp(), 18.dp(), 0, 4.dp())
        })
        header.addView(TextView(this).apply {
            text = "What's new"
            textSize = 38f
            typeface = androidx.core.content.res.ResourcesCompat.getFont(this@WhatsNewActivity, R.font.poppins_extrabold)
            setTextColor(onSurface)
            letterSpacing = -.02f
            setPadding(0, 0, 0, 4.dp())
        })
        header.addView(TextView(this).apply {
            text = "Every change, release by release."
            textSize = 14f
            setTextColor(variant)
            setPadding(0, 0, 0, 4.dp())
        })
        page.addView(header)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(16.dp(), 4.dp(), 16.dp(), 24.dp())
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        loading = TextView(this).apply {
            text = "Release history is cached on this device and can be viewed offline."
            textSize = 13f
            setTextColor(variant)
            setPadding(20.dp(), 0, 20.dp(), 10.dp())
        }
        page.addView(loading)
        val screen = android.widget.FrameLayout(this).apply {
            setBackgroundColor(surface)
            addView(UniversalBackgroundView(this@WhatsNewActivity), android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(screen)
        PortalTransition.enter(this)
        if (!getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("motion_reduced", false)) animatePageEntrance(page)

        // Render local data immediately, without waiting for GitHub.
        val cached = ReleaseNotesCache.cachedHistory(this)
        if (cached.isNotEmpty()) render(cached)
        else {
            content.addView(TextView(this).apply {
                text = "Your release history will appear here after the first successful connection."
                textSize = 15f
                setTextColor(onSurface)
                setPadding(16.dp(), 18.dp(), 16.dp(), 18.dp())
            })
        }

        // Keep navigation instant: don't hit the network again while the saved history is fresh.
        if (cached.isNotEmpty() && !ReleaseNotesCache.shouldRefreshHistory(this, BuildConfig.VERSION_NAME)) {
            loading.text = "Saved release history · available offline"
        } else {
            Thread {
                val fresh = ReleaseNotesCache.fetchHistory(this)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (fresh != null) {
                        val previousY = scroll.scrollY
                        render(fresh)
                        scroll.post { scroll.scrollTo(0, previousY.coerceAtMost((content.height - scroll.height).coerceAtLeast(0))) }
                        loading.text = "Release history updated · available offline"
                    } else {
                        loading.text = if (cached.isNotEmpty()) "Showing saved release notes · offline or connection unavailable"
                        else "Couldn't load release history. Connect to the internet and reopen this page."
                    }
                }
            }.start()
        }
    }

    private fun render(releases: List<ReleaseNotesCache.Release>) {
        content.removeAllViews()
        val primaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer, R.color.surface)
        val onPrimaryContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer, R.color.fg)
        val outline = color(com.google.android.material.R.attr.colorOutlineVariant, R.color.glassStroke)
        val onSurface = color(com.google.android.material.R.attr.colorOnSurface, R.color.fg)
        val variant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.mute)
        val versions = (listOf(BuildConfig.VERSION_NAME) + releases.map { it.version }).distinct().take(3)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        versions.forEachIndexed { index, version ->
            val tab = TextView(this).apply {
                text = version
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(8.dp(), 10.dp(), 8.dp(), 10.dp())
                styleSeg(version == selectedVersion)
                isClickable = true; isFocusable = true
                setOnClickListener { selectedVersion = version; render(releases) }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = 6.dp() })
        }
        content.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16.dp() })
        val visibleReleases = releases.filter { it.version == selectedVersion }
        if (visibleReleases.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Release notes for v$selectedVersion aren't available in the saved release history yet. Check for updates when you're online to refresh the notes."
                textSize = 14f
                setLineSpacing(4.dp().toFloat(), 1f)
                setTextColor(onSurface)
                setPadding(16.dp(), 18.dp(), 16.dp(), 18.dp())
            })
            return
        }

        val latest = visibleReleases.firstOrNull()
        if (latest != null) {
            val hero = MaterialCardView(this).apply {
                radius = 32.dp().toFloat()
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(primaryContainer)
                setContentPadding(22.dp(), 22.dp(), 22.dp(), 22.dp())
                isClickable = true
                isFocusable = true
                setOnClickListener { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.url))) } }
            }
            val heroBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            heroBody.addView(TextView(this).apply {
                text = if (releases.firstOrNull()?.version == latest.version) "LATEST" else "RELEASE"
                textSize = 11f
                letterSpacing = 0.16f
                setTextColor(onPrimaryContainer)
            })
            heroBody.addView(TextView(this).apply {
                text = latest.version
                textSize = 46f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@WhatsNewActivity, R.font.montserrat_bold)
                setTextColor(onPrimaryContainer)
                setPadding(0, 8.dp(), 0, 6.dp())
            })
            heroBody.addView(TextView(this).apply {
                text = latest.title.ifBlank { "Drishyam ${latest.version}" }
                textSize = 14f
                setTextColor(onPrimaryContainer)
            })
            hero.addView(heroBody)
            content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18.dp() })
            if (motionEnabled(this) && !getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("motion_reduced", false)) {
                hero.alpha = 0f
                hero.translationY = 14.dp().toFloat()
                hero.scaleX = 0.985f
                hero.scaleY = 0.985f
                hero.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay(40L).setDuration(280L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }

        visibleReleases.forEachIndexed { index, release ->
            val card = MaterialCardView(this).apply {
                radius = 28.dp().toFloat()
                cardElevation = 0f
                strokeWidth = 1.dp()
                strokeColor = outline
                setCardBackgroundColor(if (index == 0) primaryContainer else color(com.google.android.material.R.attr.colorSurfaceContainer, R.color.glassFill))
                setContentPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) }
                }
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            body.addView(TextView(this).apply {
                text = if (index == 0 && releases.firstOrNull()?.version == release.version) "LATEST RELEASE" else "RELEASE NOTES"
                textSize = 10f
                letterSpacing = 0.12f
                setTextColor(if (index == 0) onPrimaryContainer else variant)
            })
            body.addView(TextView(this).apply {
                text = release.title.ifBlank { "Drishyam ${release.version}" }
                textSize = 22f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@WhatsNewActivity, R.font.montserrat_semibold)
                setTextColor(if (index == 0) onPrimaryContainer else onSurface)
                setPadding(0, 5.dp(), 0, 0)
            })
            if (release.date.isNotBlank()) body.addView(TextView(this).apply {
                text = release.date
                textSize = 12f
                setTextColor(if (index == 0) onPrimaryContainer else variant)
                setPadding(0, 3.dp(), 0, 12.dp())
            })
            body.addView(TextView(this).apply {
                text = release.notes.ifBlank { "Release notes are not available offline yet." }
                textSize = 14f
                setLineSpacing(4.dp().toFloat(), 1f)
                setTextColor(if (index == 0) onPrimaryContainer else onSurface)
            })
            card.addView(body)
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 12.dp()
            })
            if (motionEnabled(this@WhatsNewActivity) && !getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean("motion_reduced", false)) {
                card.alpha = 0f
                card.translationY = 14.dp().toFloat()
                card.scaleX = 0.985f
                card.scaleY = 0.985f
                card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setStartDelay((70L + index.coerceAtMost(8) * 38L))
                    .setDuration(280L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }
        scroll.post { scroll.scrollTo(0, restoreScrollY.coerceAtMost((content.height - scroll.height).coerceAtLeast(0))) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("release_history_scroll", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    private fun color(attr: Int, fallback: Int) = MaterialColors.getColor(this, attr, ContextCompat.getColor(this, fallback))
    private fun night() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
}
