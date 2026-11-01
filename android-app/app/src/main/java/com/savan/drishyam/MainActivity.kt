package com.savan.drishyam

import android.animation.ValueAnimator
import androidx.core.content.res.ResourcesCompat
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.text.style.StyleSpan
import android.text.Spanned
import android.text.SpannableString
import android.graphics.Typeface
import android.animation.ObjectAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AnimationDrawable
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import android.content.res.ColorStateList

class Source(val name: String, val q: String, val searchOnly: Boolean = false, val sort: String = "hot") {
    val items = ArrayList<Photo>()
    val ids = HashSet<String>() // IDs currently present in this session's feed.
    val fetchedIds = HashSet<String>() // IDs received from the provider, whether displayed or not.
    val seenIds = HashSet<String>() // Only IDs the user has actually viewed.
    var refreshPage = 1
    // Discovery rotation is specific to this feed and survives ordinary resets.
    var discoveryIndex = 0
    var after = ""
    var page = 1
    var done = false
    var loading = false
    // True while a pull-to-refresh is discovering newer results; distinct from scroll prefetch.
    var refreshing = false
    var err: String? = null
    var gen = 0          // bumps on reset so late results from an old sort are ignored
    var extra = false    // searched photo topics, removed when the next search runs
    var forceFresh = false

    fun reset(clearHistory: Boolean = false) {
        items.clear(); ids.clear(); fetchedIds.clear()
        if (clearHistory) { seenIds.clear(); refreshPage = 1 }
        after = ""; page = 1; done = false; err = null; loading = false; refreshing = false; forceFresh = false
        gen++
    }
}

class MainActivity : AppCompatActivity() {

    companion object {
        // Curated photography categories only; no Reddit/photo topic sources are exposed in the UI.
        val PHOTO_CATEGORIES = listOf(
            "For You" to "cinematic nature landscape photography",
            "Nature" to "nature photography",
            "Forest" to "forest woodland trees",
            "Portrait" to "portrait photography",
            "Photography" to "photography",
            "Mountains" to "mountain landscape",
            "Clouds" to "clouds sky",
            "Landscape" to "landscape photography",
            "Beach" to "beach sea sand",
            "Earth" to "planet earth landscape",
            "Hiking" to "hiking trail outdoors",
            "Wildlife" to "wildlife animals",
            "Architecture" to "modern architecture",
            "Abstract" to "abstract photography",
            "Minimalism" to "minimalist photography",
            "Travel" to "travel photography",
            "Street" to "street photography",
            "Ocean" to "ocean sea waves",
            "Night Sky" to "night sky stars"
        )

        fun applyTheme(c: Context) {
            val mode = when (c.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getString("theme", "System")) {
                "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "Light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private lateinit var rv: RecyclerView
    private lateinit var status: TextView
    private lateinit var statusPill: GlassLayout
    private lateinit var savedBar: View
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var aboutScroll: ScrollView
    private lateinit var glassSource: View
    private lateinit var glassTop: GlassLayout
    private lateinit var glassNav: GlassLayout
    private lateinit var overlay: View
    private lateinit var navIndicator: View
    private lateinit var navItemsRow: LinearLayout
    private lateinit var edgeTop: View
    private lateinit var edgeBottom: View
    private lateinit var segFav: TextView
    private lateinit var segBook: TextView
    private lateinit var homeHeaderAdapter: HomeHeaderAdapter
    private val adapter = PhotoAdapter()
    private val sources = ArrayList<Source>() // Category definitions used by the chip row.
    private val feedSessions = HashMap<String, Source>() // Independent category + sort sessions.
    private lateinit var cur: Source
    private var tab = 0
    private var savedMode = 0      // 0 = likes, 1 = bookmarks
    private var shownSort = "hot"
    private var lowFxAtCreate = false
    private var visualFxAtCreate = true
    private var motionAtCreate = true
    private var lastTopPad = -1
    private var lastBottomPad = -1
    private var statusBarInsetTop = 0
    private var transientStatus: String? = null
    private var statusGeneration = 0
    private val splashAnimators = mutableListOf<ValueAnimator>()
    private var splashFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        super.onCreate(savedInstanceState)
        applyActivityTransitionPreference(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        // Let Android own predictive Back when this is the task's launcher/home instance.
        // That allows the real launcher (wallpaper and icons) to be previewed on exit.
        // A MainActivity opened from Our Story keeps the existing in-app back gesture.
        PortalTransition.enter(this, enableDirectionalBackGesture = intent.getBooleanExtra("open_about", false))
        // Draw the app background beneath the status bar; keep system icons theme-aware.
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        val systemUi = window.decorView.systemUiVisibility
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (night) {
            systemUi and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            systemUi or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        lowFxAtCreate = lowFx(this)
        visualFxAtCreate = visualEffectsEnabled(this)
        motionAtCreate = motionEnabled(this)

        rv = findViewById(R.id.rv)
        rv.isVerticalScrollBarEnabled = false
        status = findViewById(R.id.status)
        statusPill = findViewById(R.id.statusPill)
        savedBar = findViewById(R.id.savedBar)
        swipe = findViewById(R.id.swipe)
        aboutScroll = findViewById(R.id.about)
        aboutScroll.isVerticalScrollBarEnabled = false
        glassSource = findViewById(R.id.glassSource)
        glassTop = findViewById(R.id.glassTop)
        glassNav = findViewById(R.id.glassNav)
        overlay = findViewById(R.id.overlay)
        navIndicator = findViewById(R.id.navIndicator)
        navItemsRow = findViewById(R.id.navItemsRow)
        edgeTop = findViewById(R.id.edgeTop)
        edgeBottom = findViewById(R.id.edgeBottom)

        // Keep the glass header below status-bar icons while the animated page background
        // remains visible behind the transparent system bar.
        val root = findViewById<View>(R.id.root)
        // No foreground fade overlay: keep the portrait and story content unobscured.
        // The transparent status bar shows the real page background beneath system icons.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            statusBarInsetTop = bars.top
            applyInsets()
            (glassTop.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                val desired = bars.top + 8.dp()
                if (lp.topMargin != desired) { lp.topMargin = desired; glassTop.layoutParams = lp }
            }
            (glassNav.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                val desired = bars.bottom + 8.dp()
                if (lp.bottomMargin != desired) { lp.bottomMargin = desired; glassNav.layoutParams = lp }
            }
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        segFav = findViewById(R.id.segFav)
        segBook = findViewById(R.id.segBook)

        bindGlass(overlay, glassSource, 70)

        // The feed and About page scroll underneath the floating bars, so keep room for them.
        val relayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyInsets() }
        glassTop.addOnLayoutChangeListener(relayout)
        glassNav.addOnLayoutChangeListener(relayout)
        overlay.addOnLayoutChangeListener(relayout)
        aboutScroll.setOnScrollChangeListener { _, _, _, _, _ -> pokeGlass() }

        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent), ContextCompat.getColor(this, R.color.accent2))
        swipe.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface))
        swipe.setOnRefreshListener { refreshCurrent() }

        animateAbout()
        setupAbout()


        for ((name, query) in PHOTO_CATEGORIES) sources.add(Source(name, query))
        cur = sessionFor(sources.first(), currentSort())

        rv.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        installScrollBounce(aboutScroll)
        homeHeaderAdapter = HomeHeaderAdapter()
        rv.adapter = ConcatAdapter(homeHeaderAdapter, adapter)
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(8)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                pokeGlass()
                pulseEdgeFade()
                if (tab == 0 && dy > 3) {
                    setNavVisible(false)
                } else if (dy < -1) {
                    setNavVisible(true)
                }
                val lm = r.layoutManager as LinearLayoutManager
                if (dy > 0) {
                    val totalFeedRows = adapter.itemCount + if (tab == 0) 1 else 0
                    val remaining = totalFeedRows - lm.findLastVisibleItemPosition() - 1
                    if (remaining <= 7 || remaining <= backgroundPrefetchThreshold()) loadMore()
                }
            }
        })

        status.setOnClickListener { if (tab == 0 && cur.err != null) { cur.err = null; loadMore() } }


        findViewById<View>(R.id.navItem0).apply { pressFx(); setOnClickListener { showTab(0) } }
        findViewById<View>(R.id.navItem1).apply { pressFx(); setOnClickListener { showSearch() } }
        findViewById<View>(R.id.navItem2).apply { pressFx(); setOnClickListener { showTab(1) } }
        findViewById<View>(R.id.navItem3).apply {
            pressFx()
            // Capture the actual finger-down point; the portal must not default to
            // the navigation item's centre when the user taps near an edge.
            setOnTouchListener { v, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    PortalTransition.recordTouch(v, event.rawX, event.rawY)
                }
                false
            }
            setOnClickListener { PortalTransition.launch(this@MainActivity, this, Intent(this@MainActivity, SettingsHubActivity::class.java)) }
        }
        segFav.apply { pressFx(); setOnClickListener { setSaved(0) } }
        segBook.apply { pressFx(); setOnClickListener { setSaved(1) } }

        shownSort = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getString("sort_display", currentSort()) ?: currentSort()
        styleSort()
        showTab(0)
        if (intent.getBooleanExtra("open_about", false)) showTab(2)
        if (!PortalTransition.hasOpeningOrigin(this)) animatePageEntrance(findViewById(android.R.id.content))
        if (savedInstanceState == null) {
            startLaunchSplash()
        } else {
            findViewById<View>(R.id.splashRoot).visibility = View.GONE
            rv.post {
                homeHeaderAdapter.logoView?.visibility = View.VISIBLE
                finishSplashAndContinue()
            }
        }
    }

    /** Phase H2: seamless splash-to-home logo hand-off and a logo hand-off into the existing Home header. */
    private fun startLaunchSplash() {
        val splash = findViewById<View>(R.id.splashRoot)
        val logo = findViewById<ImageView>(R.id.splashLogo)
        val target = homeHeaderAdapter.logoView
        if (target == null) {
            rv.post { if (!isFinishing && !isDestroyed) startLaunchSplash() }
            return
        }
        if (!motionEnabled(this)) {
            splash.visibility = View.GONE
            target.visibility = View.VISIBLE
            finishSplashAndContinue()
            return
        }

        // Keep the destination logo hidden while the splash logo is in flight.
        // Revealing both at once creates a visible duplicate in the Home header.
        target.visibility = View.INVISIBLE
        val nameRow = findViewById<LinearLayout>(R.id.splashName)
        nameRow.removeAllViews()
        val name = "Drishyam"
        nameRow.contentDescription = name
        val nameColor = ContextCompat.getColor(this, R.color.fg)
        name.forEachIndexed { index, letter ->
            val tv = TextView(this).apply {
                text = letter.toString()
                textSize = 42f
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.ericaone)
                setTextColor(nameColor)
                alpha = 0f
                translationY = 20.dp().toFloat()
                includeFontPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            nameRow.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            tv.postDelayed({
                if (!isFinishing && splash.visibility == View.VISIBLE) {
                    tv.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(620L)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
            }, 1050L + index * 80L)
        }

        logo.scaleX = 0.55f
        logo.scaleY = 0.55f
        logo.rotation = -14f
        logo.alpha = 0f
        logo.rotation = 0f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(200L).setDuration(1100L).setInterpolator(OvershootInterpolator(1.15f)).start()
        ObjectAnimator.ofFloat(logo, View.ROTATION, 0f, 360f).apply {
            duration = 2600L
            startDelay = 200L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
            splashAnimators.add(this)
        }

        val byline = findViewById<TextView>(R.id.splashByline)
        val tagline = findViewById<TextView>(R.id.splashTagline)
        val caption = findViewById<TextView>(R.id.splashCaption)
        byline.alpha = 0f; byline.translationY = 8.dp().toFloat()
        byline.postDelayed({ if (splash.visibility == View.VISIBLE) byline.animate().alpha(1f).translationY(0f).setDuration(500L).setInterpolator(DecelerateInterpolator()).start() }, 2100L)
        val taglineText = SpannableString("See the world a little slower.")
        val slowerStart = taglineText.indexOf("slower.")
        if (slowerStart >= 0) taglineText.setSpan(StyleSpan(Typeface.BOLD), slowerStart, taglineText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tagline.text = taglineText
        tagline.alpha = 0f; tagline.translationY = 8.dp().toFloat()
        tagline.postDelayed({ if (splash.visibility == View.VISIBLE) tagline.animate().alpha(1f).translationY(0f).setDuration(500L).setInterpolator(DecelerateInterpolator()).start() }, 2700L)
        caption.alpha = 0f
        caption.postDelayed({ if (splash.visibility == View.VISIBLE) caption.animate().alpha(1f).setDuration(350L).start() }, 3000L)

        val fill = findViewById<View>(R.id.splashProgressFill)
        fill.scaleX = 0f
        fill.postDelayed({
            if (splash.visibility == View.VISIBLE) ObjectAnimator.ofFloat(fill, View.SCALE_X, 0f, 1f).apply {
                duration = 3500L
                interpolator = LinearInterpolator()
                start()
            }
        }, 350L)

        val glow = findViewById<View>(R.id.splashAmbientGlow)
        val glowPulse = ValueAnimator.ofFloat(0.72f, 1f, 0.78f).apply {
            duration = 4800L; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            addUpdateListener { glow.alpha = it.animatedValue as Float }
        }
        splashAnimators.add(glowPulse); glowPulse.start()

        val ring = findViewById<View>(R.id.splashRing)
        ring.postDelayed({
            if (splash.visibility == View.VISIBLE) {
                val ringPulse = ValueAnimator.ofFloat(0.55f, 1.65f).apply {
                    duration = 2600L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                    addUpdateListener {
                        val fraction = it.animatedFraction
                        ring.scaleX = it.animatedValue as Float
                        ring.scaleY = it.animatedValue as Float
                        ring.alpha = 1f - fraction
                    }
                }
                splashAnimators.add(ringPulse); ringPulse.start()
            }
        }, 1100L)

        splash.postDelayed({ handOffSplashLogo(splash, logo, target) }, 4500L)
    }

    private fun handOffSplashLogo(splash: View, logo: ImageView, target: ImageView) {
        if (splashFinished || isFinishing || isDestroyed) return
        if (!motionEnabled(this)) {
            splash.visibility = View.GONE
            target.visibility = View.VISIBLE
            finishSplashAndContinue()
            return
        }
        // A separate travelling copy lets the splash dissolve without fading the logo itself.
        val host = splash.parent as? ViewGroup ?: return
        val overlay = ImageView(this).apply {
            setImageDrawable(logo.drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Drishyam"
            rotation = logo.rotation
            elevation = 100.dp().toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val hostPos = IntArray(2)
        val logoPos = IntArray(2)
        val targetPos = IntArray(2)
        host.getLocationOnScreen(hostPos)
        logo.getLocationOnScreen(logoPos)
        target.getLocationOnScreen(targetPos)
        val startX = (logoPos[0] - hostPos[0]).toFloat()
        val startY = (logoPos[1] - hostPos[1]).toFloat()
        val targetWidth = (target.width - target.paddingLeft - target.paddingRight).coerceAtLeast(1)
        val targetHeight = (target.height - target.paddingTop - target.paddingBottom).coerceAtLeast(1)
        val scale = minOf(targetWidth.toFloat() / logo.width.coerceAtLeast(1),
            targetHeight.toFloat() / logo.height.coerceAtLeast(1))
        val endX = targetPos[0] - hostPos[0] + target.paddingLeft - (logo.width * scale - targetWidth) / 2f
        val endY = targetPos[1] - hostPos[1] + target.paddingTop - (logo.height * scale - targetHeight) / 2f
        overlay.layoutParams = FrameLayout.LayoutParams(logo.width, logo.height)
        host.addView(overlay)
        overlay.x = startX
        overlay.y = startY
        logo.alpha = 0f
        target.visibility = View.INVISIBLE

        overlay.animate()
            .x(endX).y(endY).scaleX(scale).scaleY(scale).rotation(360f)
            .setDuration(1050L)
            .setInterpolator(android.view.animation.PathInterpolator(0.7f, 0f, 0.2f, 1f))
            .withEndAction {
                target.visibility = View.VISIBLE
                host.removeView(overlay)
                splash.visibility = View.GONE
                splash.alpha = 1f
                splash.scaleX = 1f
                splash.scaleY = 1f
                finishSplashAndContinue()
            }.start()
        splash.animate().alpha(0f).scaleX(1.025f).scaleY(1.025f)
            .setStartDelay(120L).setDuration(820L)
            .setInterpolator(DecelerateInterpolator(1.3f)).start()
    }

    private fun finishSplashAndContinue() {
        if (splashFinished) return
        splashFinished = true
        splashAnimators.forEach { it.cancel() }
        splashAnimators.clear()
        val source = findViewById<View>(R.id.glassSource)
        source.postDelayed({
            if (!isFinishing && !isDestroyed) {
                if (!showPendingWhatsNewIfNeeded()) UpdateManager.check(this, source)
            }
        }, 250L)
    }

    override fun onDestroy() {
        splashAnimators.forEach { it.cancel() }
        splashAnimators.clear()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_about", false) && ::glassNav.isInitialized) showTab(2)
    }

    /**
     * Shows release notes after an update initiated inside Drishyam has actually installed.
     * If installation was cancelled, keep the pending version until a matching/newer version
     * is installed. The dialog is shown once and never replaces the saved-photo state.
     */
    /** Opens the full What's New page once an update installed from inside Drishyam is detected. */
    private fun showPendingWhatsNewIfNeeded(): Boolean {
        val prefs = getSharedPreferences("drishyam_update_state", Context.MODE_PRIVATE)
        val pendingVersion = prefs.getString("pending_whats_new_version", null) ?: return false
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: return false
        } catch (_: Exception) { return false }
        if (compareVersionNumbers(currentVersion, pendingVersion) < 0) return false
        prefs.edit().remove("pending_whats_new_version").apply()
        startActivity(Intent(this, WhatsNewActivity::class.java).putExtra("highlight_version", currentVersion))
        return true
    }

    private fun compareVersionNumbers(a: String, b: String): Int {
        val aa = Regex("""\d+""").findAll(a).mapNotNull { it.value.toIntOrNull() }.toList()
        val bb = Regex("""\d+""").findAll(b).mapNotNull { it.value.toIntOrNull() }.toList()
        for (i in 0 until maxOf(aa.size, bb.size)) {
            val x = aa.getOrElse(i) { 0 }
            val y = bb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private var lastBrowsingSourcesSignature: String? = null

    private fun browsingSourcesSignature(): String {
        val prefs = getSharedPreferences("drishyam_browsing", Context.MODE_PRIVATE)
        val sources = listOf("source_wikimedia", "source_nexwall")
            .joinToString("|") { key -> prefs.getBoolean(key, true).toString() }
        return sources + "|" + (prefs.getString("image_quality", "High") ?: "High")
    }

    override fun onResume() {
        super.onResume()
        if (lowFx(this) != lowFxAtCreate ||
            visualEffectsEnabled(this) != visualFxAtCreate ||
            motionEnabled(this) != motionAtCreate) { recreate(); return }
        // Rebuild the feed when the user changes enabled providers in Browsing.
        val sourcesSignature = browsingSourcesSignature()
        val previousSourcesSignature = lastBrowsingSourcesSignature
        lastBrowsingSourcesSignature = sourcesSignature
        var shouldResetFeed = previousSourcesSignature != null && sourcesSignature != previousSourcesSignature
        // Settings may have changed the default sort while this screen was paused.
        val s = currentSort()
        val displaySort = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getString("sort_display", s) ?: s
        if (displaySort != shownSort) {
            shownSort = displaySort
            styleSort()
            if (::homeHeaderAdapter.isInitialized) homeHeaderAdapter.placeSelectedIndicator(false)
            val category = sources.firstOrNull { it.name == cur.name } ?: cur
            cur = sessionFor(category, s)
            adapter.data = cur.items
            rv.scrollToPosition(0)
        }
        if (shouldResetFeed) resetAll()
        else if (tab == 0 && cur.items.isEmpty()) loadMore()
        if (tab == 1) refreshSaved() else adapter.notifyDataSetChanged()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (PopupStack.dismiss()) return
        if (tab == 2) { PortalTransition.close(this, null); return }
        super.onBackPressed()
    }

    // ---------- Floating glass bars ----------

    private fun moveNavIndicator(index: Int, animate: Boolean) {
        if (index !in 0 until navItemsRow.childCount) {
            navIndicator.animate().cancel()
            navIndicator.alpha = 0f
            return
        }
        val target = navItemsRow.getChildAt(index)
        val left = navItemsRow.left + target.left
        val width = target.width
        if (width <= 0) return
        navIndicator.alpha = 1f
        if (animate && navIndicator.width > 0) {
            SegmentStretch.animate(navIndicator, Pair(left.toFloat(), width.toFloat()),
                resources.displayMetrics.density, !motionEnabled(this))
        } else {
            SegmentStretch.place(navIndicator, left.toFloat(), width.toFloat())
        }
    }

    private fun pulseEdgeFade() {
        listOf(edgeTop, edgeBottom).forEach { edge ->
            edge.animate().cancel()
            edge.visibility = View.VISIBLE
            edge.alpha = 1f
            edge.animate().alpha(0f).setStartDelay(180L).setDuration(350L)
                .withEndAction { edge.visibility = View.GONE }
                .start()
        }
    }

    private fun pokeGlass() {
        glassTop.poke()
        glassNav.poke()
        if (statusPill.visibility == View.VISIBLE) statusPill.poke()
    }

    private fun applyInsets() {
        val top = if (tab == 0) statusBarInsetTop + 12.dp() else glassTop.bottom + 8.dp()
        val bottom = if (navShown && glassNav.visibility == View.VISIBLE) (overlay.height - glassNav.top) + 8.dp() else 20.dp()
        if (top != lastTopPad || bottom != lastBottomPad) {
            lastTopPad = top
            lastBottomPad = bottom
            rv.setPadding(16.dp(), top, 16.dp(), bottom)
            swipe.setProgressViewOffset(false, top, top + 60.dp())
        }
        // Our Story hides the floating header, so its top gap must come from the actual
        // system-bar inset rather than the stale position of that hidden header.
        val storyTop = statusBarInsetTop + 18.dp()
        if (aboutScroll.paddingTop != storyTop) {
            aboutScroll.setPadding(0, storyTop, 0, 12.dp())
        }
    }

    private var navShown = true
    private var topShown = true

    private fun setTopVisible(visible: Boolean) {
        if (topShown == visible || tab != 0) return
        topShown = visible
        glassTop.animate().cancel()
        if (visible) glassTop.visibility = View.VISIBLE
        glassTop.animate()
            .translationY(if (visible) 0f else -glassTop.height.toFloat() - 16.dp())
            .alpha(if (visible) 1f else 0f)
            .setDuration(if (visible) 260L else 210L)
            .setInterpolator(if (visible) AccelerateDecelerateInterpolator() else AccelerateDecelerateInterpolator())
            .withEndAction { glassTop.visibility = if (visible) View.VISIBLE else View.INVISIBLE }
            .start()
    }

    private fun setNavVisible(visible: Boolean) {
        if (navShown == visible || tab != 0) return
        navShown = visible
        glassNav.animate().cancel()
        glassNav.animate()
            .translationY(if (visible) 0f else glassNav.height.toFloat() + 28.dp())
            .alpha(if (visible) 1f else 0f)
            .setDuration(if (visible) 210L else 180L)
            .setInterpolator(if (visible) AccelerateDecelerateInterpolator() else AccelerateDecelerateInterpolator())
            .withEndAction {
                glassNav.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                lastBottomPad = -1
                applyInsets()
            }
            .start()
        if (visible) glassNav.visibility = View.VISIBLE
        lastBottomPad = -1
        applyInsets()
    }

    // ---------- About page ----------

    private fun setupAbout() {
        findViewById<TextView>(R.id.footer).text = getString(R.string.footer, BuildConfig.VERSION_NAME)
        findViewById<ImageView>(R.id.aboutPhoto).setImageResource(R.drawable.savan)
        findViewById<ImageView>(R.id.aboutAkshatPhoto).setImageResource(R.drawable.akshat_portrait)
        findViewById<ImageView>(R.id.aboutPhoto2).setImageResource(R.drawable.akshat_photo_1)
        findViewById<ImageView>(R.id.aboutPhoto3).setImageResource(R.drawable.akshat_photo_2)
        findViewById<ImageView>(R.id.aboutPhoto4).setImageResource(R.drawable.akshat_photo_3)
        val blur = findViewById<ImageView>(R.id.aboutBlur)
        blur.setImageResource(R.drawable.savan_background)
        // Real background blur needs Android 12+; low-effects mode skips it.
        if (Build.VERSION.SDK_INT >= 31 && visualEffectsEnabled(this)) {
            blur.setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
        }
        findViewById<View>(R.id.ig).apply {
            pressFx()
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/savanjee"))) }
        }
    }

    private fun animateAbout() {
        if (!visualEffectsEnabled(this) || !motionEnabled(this)) return
        val revealIds = listOf(
            R.id.aboutPhoto, R.id.aboutAkshatPhoto,
            R.id.aboutPhoto2, R.id.aboutPhoto3, R.id.aboutPhoto4, R.id.ig
        )
        revealIds.forEachIndexed { index, id ->
            val view = findViewById<View>(id)
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = 16.dp().toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 55L)
                .setDuration(420L)
                .start()
        }
        // Keep the Instagram pill subtly alive; reduced-motion mode skips this animation.
        (findViewById<View>(R.id.ig).background as? AnimationDrawable)?.start()
    }

    private fun animateTitle() {
        if (!visualEffectsEnabled(this) || !motionEnabled(this)) return
        val t = if (::homeHeaderAdapter.isInitialized) homeHeaderAdapter.titleView else null
        if (t == null) return
        t.post {
            val w = t.paint.measureText(t.text.toString()).coerceAtLeast(100f) * 2
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val colors = if (night) {
                intArrayOf(0xFFD0C79A.toInt(), 0xFF89945A.toInt(), 0xFFF3F1E7.toInt(), 0xFF89945A.toInt(), 0xFFD0C79A.toInt())
            } else {
                intArrayOf(0xFF414D2D.toInt(), 0xFF89945A.toInt(), 0xFFD0C79A.toInt(), 0xFF89945A.toInt(), 0xFF414D2D.toInt())
            }
            val sh = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.REPEAT)
            t.paint.shader = sh
            val m = Matrix()
            ValueAnimator.ofFloat(0f, w).apply {
                duration = 9000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { m.setTranslate(it.animatedValue as Float, 0f); sh.setLocalMatrix(m); t.invalidate() }
                start()
            }
        }
    }

    // ---------- Navigation ----------

    private fun showTab(n: Int) {
        tab = n
        navShown = true
        topShown = true
        glassTop.animate().cancel()
        glassTop.visibility = if (n == 1) View.VISIBLE else View.GONE
        glassTop.translationY = 0f
        glassTop.alpha = 1f
        homeHeaderAdapter.visible = n == 0
        homeHeaderAdapter.notifyDataSetChanged()
        glassNav.animate().cancel()
        glassNav.visibility = View.VISIBLE
        glassNav.translationY = 0f
        glassNav.alpha = 1f
        val selectedNav = when (n) { 0 -> 0; 1 -> 2; else -> -1 }
        listOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3).forEachIndexed { i, id ->
            val v = findViewById<ImageView>(id)
            val on = i == selectedNav
            v.background = null
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val iconColor = when {
                on && night -> 0xFF0A0D08.toInt()
                on -> 0xFFF3F1E7.toInt()
                night -> 0xFFD0C79A.toInt()
                else -> 0xFF414D2D.toInt()
            }
            v.setColorFilter(iconColor)
            v.animate().cancel()
            if (motionEnabled(this)) {
                v.animate().scaleX(if (on) 1.12f else 1f).scaleY(if (on) 1.12f else 1f)
                    .setDuration(220L).setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                v.scaleX = 1f
                v.scaleY = 1f
            }
            val itemId = when (i) {
                0 -> R.id.navItem0
                1 -> R.id.navItem1
                2 -> R.id.navItem2
                else -> R.id.navItem3
            }
            findViewById<View>(itemId).setBackgroundResource(android.R.color.transparent)
        }
        navItemsRow.post { moveNavIndicator(selectedNav, true) }
        findViewById<View>(R.id.home).visibility = if (n < 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.about).visibility = if (n == 2) View.VISIBLE else View.GONE
        glassNav.visibility = if (n == 2) View.GONE else View.VISIBLE
        if (n == 2) { glassNav.animate().cancel(); glassNav.translationY = glassNav.height.toFloat() + 28.dp(); glassNav.alpha = 0f }
        glassTop.visibility = if (n == 1) View.VISIBLE else View.GONE
        savedBar.visibility = if (n == 1) View.VISIBLE else View.GONE

        if (n == 1) {
            refreshSaved()
        } else {
            restoreCachedFeed(cur)
            adapter.data = cur.items
            adapter.notifyDataSetChanged()
            updateStatus()
            if (cur.items.isEmpty()) loadMore()
        }
        pokeGlass()
        if (n == 2) animateAbout()
    }

    // ---------- Chips ----------

    private fun feedCacheKey(source: Source): String {
        val prefs = getSharedPreferences("drishyam_browsing", Context.MODE_PRIVATE)
        val signature = listOf("source_wikimedia", "source_nexwall")
            .joinToString("_") { prefs.getBoolean(it, true).toString() }
        return "feed_cache_" + source.name.lowercase().replace(Regex("[^a-z0-9]+"), "_") + "_" + source.sort + "_" + signature
    }

    private fun seenCacheKey(source: Source) =
        "seen_" + source.name.lowercase().replace(Regex("[^a-z0-9]+"), "_") + "_" + source.sort

    private fun persistSeen(source: Source) {
        getSharedPreferences("drishyam_feed_seen", Context.MODE_PRIVATE).edit()
            .putStringSet(seenCacheKey(source), HashSet(source.seenIds)).apply()
    }

    private fun sessionFor(category: Source, sort: String): Source {
        val key = category.name + "|" + sort + "|" + category.q
        return feedSessions.getOrPut(key) {
            Source(category.name, category.q, category.searchOnly, sort).apply {
                seenIds.addAll(getSharedPreferences("drishyam_feed_seen", Context.MODE_PRIVATE)
                    .getStringSet(seenCacheKey(this), emptySet()) ?: emptySet())
            }
        }
    }

    private fun restoreCachedFeed(source: Source): Boolean {
        if (source.items.isNotEmpty()) return true
        return try {
            val raw = getSharedPreferences("drishyam_feed_cache", Context.MODE_PRIVATE)
                .getString(feedCacheKey(source), null) ?: return false
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val photo = Photo.from(array.getJSONObject(i))
                if (source.ids.add(photo.id)) {
                    source.items.add(photo)
                    source.fetchedIds.add(photo.id)
                }
            }
            source.items.isNotEmpty()
        } catch (_: Exception) { false }
    }

    private fun persistFeedCache(source: Source) {
        if (source.items.isEmpty()) return
        val array = org.json.JSONArray()
        source.items.take(60).forEach { array.put(it.toJson()) }
        getSharedPreferences("drishyam_feed_cache", Context.MODE_PRIVATE).edit()
            .putString(feedCacheKey(source), array.toString()).apply()
    }

    private fun select(s: Source) {
        cur = sessionFor(s, currentSort())
        restoreCachedFeed(cur)
        rv.scrollToPosition(0)
        showTab(0)
    }

    private fun shuffleCurrent() {
        if (cur.items.size < 2) return
        cur.items.shuffle()
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        toast(getString(R.string.shuffled))
    }

    // ---------- Sort (Hot / Latest / Top) ----------

    private fun currentSort(): String =
        getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getString("sort", "hot") ?: "hot"

    private fun setSort(s: String) {
        val previous = currentSort()
        shownSort = s
        getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit()
            .putString("sort", s).putString("sort_display", s).apply()
        if (s == previous) { refreshCurrent(); return }
        val category = sources.firstOrNull { it.name == cur.name } ?: cur
        cur = sessionFor(category, s)
        restoreCachedFeed(cur)
        adapter.data = cur.items
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        updateStatus()
        if (cur.items.isEmpty()) loadMore()
    }

    // India intentionally mirrors Hot: same feed and ordering, with its own selected segment.
    private fun setIndiaSort() {
        shownSort = "india"
        getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit()
            .putString("sort", "hot").putString("sort_display", "india").apply()
        val category = sources.firstOrNull { it.name == cur.name } ?: cur
        cur = sessionFor(category, "hot")
        restoreCachedFeed(cur)
        adapter.data = cur.items
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        updateStatus()
        if (cur.items.isEmpty()) loadMore()
    }

    private fun styleSort() {
        if (::homeHeaderAdapter.isInitialized) homeHeaderAdapter.styleSort()
    }

    private fun showSortPopup() {
        val options = listOf("India" to "india", "Latest" to "new", "Hot" to "hot", "Top" to "top")
        showGlassPopup(this, glassSource, topOffsetDp = 72) { box, close ->
            box.setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            box.addView(TextView(this).apply {
                text = "Explore photos"
                textSize = 17f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@MainActivity, R.font.montserrat_semibold)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.fg))
            })
            options.forEach { (label, key) ->
                box.addView(TextView(this).apply {
                    text = (if (currentSort() == key) "✓  " else "") + label
                    gravity = Gravity.CENTER_VERTICAL
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.fg))
                    background = ContextCompat.getDrawable(this@MainActivity, if (currentSort() == key) R.drawable.accent_pill else R.drawable.glass_pill)
                    setPadding(14.dp(), 0, 14.dp(), 0)
                    isClickable = true
                    isFocusable = true
                    pressFx()
                    setOnClickListener {
                        close()
                        setSort(key)
                        styleSort()
                        if (::homeHeaderAdapter.isInitialized) homeHeaderAdapter.placeSelectedIndicator(false)
                    }
                }, LinearLayout.LayoutParams(-1, 44.dp()).apply { topMargin = 5.dp() })
            }
        }
    }

    private fun resetAll() {
        feedSessions.values.forEach { it.reset(clearHistory = true) }
        getSharedPreferences("drishyam_feed_seen", Context.MODE_PRIVATE).edit().clear().apply()
        if (tab == 0) {
            adapter.data = cur.items
            adapter.notifyDataSetChanged()
            rv.scrollToPosition(0)
            updateStatus()
            loadMore()
        }
    }

    // ---------- Saved ----------

    private fun setSaved(mode: Int) {
        savedMode = mode
        segFav.styleSeg(mode == 0)
        segBook.styleSeg(mode == 1)
        refreshSaved()
    }

    private fun refreshSaved() {
        adapter.data = if (savedMode == 0) Store.favs(this) else Store.bookmarks(this)
        adapter.notifyDataSetChanged()
        updateStatus()
    }

    // ---------- Search ----------

    private fun showSearch() {
        startActivityForResult(Intent(this, SearchActivity::class.java), 721)
    }

    @Deprecated("Deprecated in Android, retained for SearchActivity category hand-off")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 721 && resultCode == RESULT_OK) {
            val category = data?.getStringExtra("category") ?: return
            sources.firstOrNull { it.name == category }?.let { select(it) }
        }
    }

    // ---------- Feed ----------

    private fun updateStatus() {
        val text = when {
            tab == 1 -> when {
                savedMode == 0 && Store.favs(this).isEmpty() -> getString(R.string.empty_likes)
                savedMode == 1 && Store.bookmarks(this).isEmpty() -> getString(R.string.empty_bookmarks)
                else -> ""
            }
            tab == 2 -> ""
            cur.loading -> if (cur.refreshing) "Finding newer photos…" else getString(R.string.loading_more)
            transientStatus != null -> transientStatus!!
            cur.err != null -> cur.err + " " + getString(R.string.retry_suffix)
            cur.done -> getString(R.string.end_of_feed)
            else -> ""
        }
        status.text = text
        statusPill.visibility = if (text.isBlank() || (cur.err != null && statusPill.getTag(R.id.statusPill) == "dismissed")) View.GONE else View.VISIBLE
    }

    private fun showTransientStatus(message: String) {
        transientStatus = message
        val generation = ++statusGeneration
        updateStatus()
        statusPill.animate().cancel()
        statusPill.alpha = 1f
        statusPill.postDelayed({
            if (generation == statusGeneration) {
                transientStatus = null
                updateStatus()
            }
        }, 3500L)
    }

    private fun refreshCurrent() {
        if (tab != 0) { swipe.isRefreshing = false; return }
        transientStatus = null
        statusGeneration++
        // A pull-to-refresh creates a fresh discovery session and replaces the
        // visible collection. Keep only the history of photos actually viewed.
        cur.items.clear()
        cur.ids.clear()
        cur.fetchedIds.clear()
        cur.discoveryIndex++ // Rotate discovery for every category, not only For You.
        cur.loading = false
        cur.refreshing = true
        cur.err = null
        cur.done = false
        cur.after = ""
        cur.page = maxOf(cur.refreshPage, cur.page, 1)
        cur.gen++
        cur.forceFresh = true
        getSharedPreferences("drishyam_feed_cache", Context.MODE_PRIVATE).edit()
            .remove(feedCacheKey(cur)).apply()
        adapter.data = cur.items
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        updateStatus()
        loadMore()
    }

    // Keep a bounded ready-ahead buffer. Wi-Fi can prepare farther ahead;
    // metered connections use a smaller buffer to conserve mobile data.
    private fun backgroundPrefetchThreshold(): Int {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return 0
        val network = manager.activeNetwork ?: return 0
        val caps = manager.getNetworkCapabilities(network) ?: return 0
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return 0
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) 40 else 12
    }

    private fun maybePrefetchFeed(source: Source) {
        if (tab != 0 || source !== cur || source.loading || source.done || source.err != null) return
        val threshold = backgroundPrefetchThreshold()
        if (threshold <= 0) return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = lm.findLastVisibleItemPosition()
        val totalFeedRows = adapter.itemCount + if (tab == 0) 1 else 0
        val remaining = totalFeedRows - lastVisible - 1
        if (remaining <= threshold) loadMore()
    }

    private fun loadMore() {
        val s = cur
        if (tab != 0 || s.loading || s.done || s.err != null) return
        s.loading = true
        updateStatus()
        val gen = s.gen
        val page = s.page
        val after = s.after
        val sort = s.sort
        val forceFresh = s.forceFresh
        s.forceFresh = false

        Thread {
            val added = ArrayList<Photo>()
            var nextAfter = after
            var nextCommonsPage = page
            var finished = false
            var error: String? = null
            val providerErrors = ArrayList<String>()
            val query = s.q
            val fallbackQuery = s.q
            val browsingPrefs = getSharedPreferences("drishyam_browsing", Context.MODE_PRIVATE)
            val useWikimedia = browsingPrefs.getBoolean("source_wikimedia", true)
            val useNexwall = browsingPrefs.getBoolean("source_nexwall", true)
            val imageQuality = browsingPrefs.getString("image_quality", "High") ?: "High"
            val preferredOrientation = browsingPrefs.getString("orientation", "Portrait") ?: "Portrait"
            var usedNexwall = false
            var nextNexwallPage = page
            if (!useWikimedia && !useNexwall) {
                error = "No photo sources are enabled. Enable Wikimedia Commons or NexWall in Settings → Browsing."
            } else {
                if (useWikimedia && added.isEmpty()) {
                    try {
                        val forYouThemes = listOf(
                            "mountain landscape photography", "wildlife nature photography",
                            "ocean coast photography", "forest nature photography",
                            "aerial landscape photography", "macro nature photography",
                            "desert landscape photography", "night sky astrophotography",
                            "street travel photography", "flowers botanical photography",
                            "waterfall river photography", "architecture city photography"
                        )
                        val categoryVariants = mapOf(
                            "Forest" to listOf("forest woodland photograph", "misty forest trees photography", "ancient forest canopy photograph", "pine forest landscape photo"),
                            "Nature" to listOf("nature wildlife macro photography", "botanical nature landscape photo", "river waterfall nature photograph", "flowers plants closeup photography"),
                            "Earth" to listOf("earth aerial landscape photograph", "desert geological landscape photo", "coastal earth landscape photography"),
                            "Hiking" to listOf("hiking trail mountain photograph", "wilderness trekking landscape photo", "forest hiking path photography"),
                            "Mountains" to listOf("mountain peaks landscape photography", "snowy alpine mountain photo", "mountain valley sunrise photograph"),
                            "Wildlife" to listOf("wildlife animal portrait photography", "birds in nature photograph", "wild animals habitat photo"),
                            "Architecture" to listOf("contemporary architecture photography", "historic building architecture photo", "minimal architecture details photograph"),
                            "Abstract" to listOf("abstract texture photography", "abstract light shadow photograph", "geometric abstract photo"),
                            "Minimalism" to listOf("minimalist nature photography", "minimal architecture photograph", "minimal still life photo"),
                            "Travel" to listOf("travel destination landscape photograph", "travel street photography", "remote places travel photo"),
                            "Street" to listOf("street candid photography", "urban street scene photograph", "city life documentary photo"),
                            "Ocean" to listOf("ocean waves coastal photography", "underwater marine life photograph", "sea cliffs landscape photo"),
                            "Night Sky" to listOf("night sky astrophotography", "milky way landscape photograph", "stars moon long exposure photo")
                        )
                        val categoryQuery = if (s.name == "For You") forYouThemes[s.discoveryIndex % forYouThemes.size]
                            else categoryVariants[s.name]?.let { it[s.discoveryIndex % it.size] } ?: fallbackQuery
                        val candidates = ArrayList<Photo>()
                        val candidateIds = HashSet<String>()
                        var attempts = 0
                        fun orientationRank(photo: Photo): Int {
                            if (photo.width <= 0 || photo.height <= 0) return 1
                            if (preferredOrientation == "Random") return 0
                            val ratio = photo.width.toFloat() / photo.height.toFloat()
                            val matches = when (preferredOrientation) {
                                "Landscape" -> ratio > 1.12f
                                "Square" -> kotlin.math.abs(ratio - 1f) <= 0.12f
                                else -> ratio < (1f / 1.12f)
                            }
                            return if (matches) 0 else 2
                        }
                        fun publishRanked(limit: Int) {
                            val batch = candidates.withIndex()
                                .sortedWith(if (sort == "new" || sort == "india") {
                                    compareByDescending<IndexedValue<Photo>> { it.value.publishedAt }
                                        .thenBy { orientationRank(it.value) }.thenBy { it.index }
                                } else compareBy<IndexedValue<Photo>> { it.index })
                                .take(limit).map { it.value }
                            if (batch.isEmpty()) return
                            runOnUiThread {
                                if (gen != s.gen) return@runOnUiThread
                                val fresh = batch.filter { photo -> s.ids.add(photo.id) && photo.id !in s.seenIds }
                                if (fresh.isNotEmpty()) {
                                    s.items.addAll(fresh)
                                    if (s.refreshing && s === cur) { swipe.isRefreshing = false; updateStatus() }
                                    val reordered = s.items.withIndex().let { indexed ->
                                        if (sort == "new" || sort == "india") indexed.sortedWith(
                                            compareByDescending<IndexedValue<Photo>> { it.value.publishedAt }
                                                .thenBy { orientationRank(it.value) }.thenBy { it.index })
                                        else indexed.toList()
                                    }.map { it.value }
                                    s.items.clear(); s.items.addAll(reordered); persistFeedCache(s)
                                    if (s === cur && tab == 0) {
                                        adapter.data = s.items
                                        adapter.notifyDataSetChanged()
                                    }
                                }
                            }
                        }
                        while (attempts < 6 && !finished && candidates.size < 25) {
                            val (photos, last) = Api.commons(categoryQuery, nextCommonsPage, imageQuality, sort)
                            nextCommonsPage++; attempts++; finished = last
                            for (photo in photos) {
                                s.fetchedIds.add(photo.id)
                                val inIndia = photo.latitude != null && photo.longitude != null &&
                                    photo.latitude in 6.5..35.7 && photo.longitude in 68.0..97.5
                                if (photo.id !in s.seenIds && photo.id !in s.ids &&
                                    (sort != "india" || inIndia) && candidateIds.add(photo.id)) candidates.add(photo)
                            }
                            if (attempts == 1 && candidates.isNotEmpty()) publishRanked(8)
                            else if (attempts > 1 && candidates.isNotEmpty()) publishRanked(25)
                        }
                        val ordered = candidates.withIndex().let { indexed ->
                            if (sort == "new" || sort == "india") indexed.sortedWith(
                                compareByDescending<IndexedValue<Photo>> { it.value.publishedAt }
                                    .thenBy { orientationRank(it.value) }.thenBy { it.index })
                            else indexed.toList()
                        }.map { it.value }
                        for (photo in ordered) { if (added.size >= 25) break; if (added.none { it.id == photo.id }) added.add(photo) }
                    } catch (e: Exception) {
                        providerErrors.add("Wikimedia: " + (e.message ?: "request failed").take(120))
                    }
                }
                val nexwallCooldownUntil = browsingPrefs.getLong("nexwall_cooldown_until", 0L)
                if (useNexwall && added.isEmpty() && System.currentTimeMillis() >= nexwallCooldownUntil) {
                    try {
                        val nexSort = if (sort == "hot" || sort == "top") "popular" else "newest"
                        val (photos, last) = Api.nexwall(fallbackQuery, nextNexwallPage, nexSort)
                        val unseen = photos.filter { it.id !in s.seenIds && s.ids.add(it.id) }
                        s.fetchedIds.addAll(photos.map { it.id })
                        if (unseen.isNotEmpty()) added.addAll(unseen)
                        finished = last
                        usedNexwall = true
                        nextNexwallPage++
                    } catch (e: Exception) {
                        val detail = e.message ?: "request failed"
                        if (detail.contains("HTTP 429")) {
                            browsingPrefs.edit().putLong("nexwall_cooldown_until", System.currentTimeMillis() + 30L * 60L * 1000L).apply()
                            providerErrors.add("NexWall is rate-limited; retrying after a cooldown")
                        } else providerErrors.add("NexWall: " + detail.take(120))
                    }
                }
                if (added.isEmpty() && s.items.isEmpty()) {
                    error = if (providerErrors.isNotEmpty()) {
                        "Home feed couldn't load. " + providerErrors.joinToString(" · ")
                    } else if (useWikimedia && !useNexwall) {
                        "Wikimedia Commons has no more unseen photos for this category. Try another category or refresh later."
                    } else {
                        "Enabled photo sources returned no matching photos. Try another category or refresh."
                    }
                }
            }
            runOnUiThread {
                if (gen != s.gen) return@runOnUiThread   // sort or refresh happened meanwhile
                s.loading = false
                s.refreshing = false
                swipe.isRefreshing = false
                s.page = if (usedNexwall) nextNexwallPage else if (useWikimedia) nextCommonsPage else page + 1
                s.refreshPage = maxOf(s.refreshPage, s.page)
                if (error != null) {
                    s.err = error
                    statusPill.setTag(R.id.statusPill, null)
                } else {
                    s.after = nextAfter
                    if (finished) s.done = true
                }
                val fresh = added.filter { photo ->
                    s.fetchedIds.add(photo.id)
                    s.ids.add(photo.id) && photo.id !in s.seenIds
                }
                s.items.addAll(fresh)
                if (sort == "new" || sort == "india") {
                    s.items.sortWith(compareByDescending<Photo> { it.publishedAt })
                }
                persistFeedCache(s)
                if (s === cur && tab == 0) {
                    adapter.data = s.items
                    adapter.notifyDataSetChanged()
                    if (error != null) {
                        showTransientStatus(error + " " + getString(R.string.retry_suffix))
                    } else {
                        val message = when {
                            fresh.isNotEmpty() -> "Loaded ${fresh.size} new photos"
                            s.items.isNotEmpty() -> "You're all caught up. Keeping your photos here."
                            s.done -> getString(R.string.end_of_feed)
                            else -> "No new photos returned"
                        }
                        showTransientStatus(message)
                    }
                    // Prepare a bounded buffer ahead without requiring the user to scroll.
                    rv.post { maybePrefetchFeed(s) }
                }
            }
        }.start()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // ---------- Home header + photo feed adapters ----------

    inner class HomeHeaderAdapter : RecyclerView.Adapter<HomeHeaderAdapter.VH>() {
        var visible = true
        var logoView: ImageView? = null
            private set
        var titleView: TextView? = null
            private set
        private var sortContainer: FrameLayout? = null
        private var sortButtons: LinearLayout? = null
        private var indicator: View? = null
        private var hot: TextView? = null
        private var latest: TextView? = null
        private var top: TextView? = null
        private var india: TextView? = null

        inner class VH(val root: View) : RecyclerView.ViewHolder(root)

        override fun getItemCount() = if (visible) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_home_header, parent, false)
            logoView = v.findViewById(R.id.headerLogo)
            titleView = v.findViewById(R.id.title)
            titleView?.post { animateTitle() }
            sortContainer = v.findViewById(R.id.sortBar)
            sortButtons = v.findViewById(R.id.sortButtons)
            indicator = v.findViewById(R.id.sortIndicator)
            hot = v.findViewById(R.id.sortHot)
            latest = v.findViewById(R.id.sortNew)
            top = v.findViewById(R.id.sortTop)
            india = v.findViewById(R.id.sortIndia)

            v.findViewById<View>(R.id.searchBar).apply {
                pressFx()
                setOnClickListener { showSearch() }
            }
            v.findViewById<View>(R.id.settingsButton).apply {
                pressFx()
                setOnClickListener {
                    PortalTransition.launch(this@MainActivity, this, Intent(this@MainActivity, SettingsHubActivity::class.java))
                }
            }
            listOf(
                (hot to "hot"), (latest to "new"), (top to "top"), (india to "india")
            ).forEach { (view, key) ->
                view?.apply {
                    pressFx()
                    setOnClickListener {
                        if (key == "india") setIndiaSort() else setSort(key)
                        this@MainActivity.styleSort()
                        moveIndicator(true)
                    }
                }
            }
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            styleSort()
            holder.root.post { moveIndicator(false) }
        }

        fun styleSort() {
            val selected = when (shownSort) {
                "new" -> latest
                "top" -> top
                "india" -> india
                else -> hot
            }
            listOf(hot, latest, top, india).forEach { button ->
                button?.setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (button === selected) R.color.bg else R.color.mute))
                button?.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            }
        }

        fun placeSelectedIndicator(animate: Boolean) = moveIndicator(animate)

        private fun moveIndicator(animate: Boolean) {
            val frame = sortContainer ?: return
            val row = sortButtons ?: return
            val pill = indicator ?: return
            val selected = when (shownSort) {
                "new" -> latest
                "top" -> top
                "india" -> india
                else -> hot
            } ?: return
            frame.post {
                if (selected.width <= 0) return@post
                val target = Pair((row.left + selected.left).toFloat(), selected.width.toFloat())
                if (animate && pill.width > 0) {
                    SegmentStretch.animate(pill, target, resources.displayMetrics.density, !motionEnabled(this@MainActivity))
                } else {
                    SegmentStretch.place(pill, target.first, target.second)
                }
            }
        }
    }

    inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.VH>() {
        var data: List<Photo> = ArrayList()
        private val animatedPhotoIds = HashSet<String>()

        private val TYPE_PHOTO = 0
        private val TYPE_FEATURED = 1

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val img: ImageView = root.findViewById(R.id.img)
            val lbl: TextView = root.findViewById(R.id.lbl)
            val tag: TextView = root.findViewById(R.id.tag)
            val heart: ImageView = root.findViewById(R.id.heart)
            init { heart.pressFx() }
        }

        private fun hasFeatured() = tab == 0 && data.isNotEmpty()

        override fun getItemCount() = data.size

        override fun getItemViewType(position: Int) =
            if (hasFeatured() && position == 0) TYPE_FEATURED else TYPE_PHOTO

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val layout = if (viewType == TYPE_FEATURED) R.layout.item_featured_photo else R.layout.item_photo
            val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, if (viewType == TYPE_FEATURED) 18.dp() else 18.dp())
            }
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val featured = getItemViewType(position) == TYPE_FEATURED
            val p = data[position]
            val boundSession = cur
            h.root.postDelayed({
                val rect = android.graphics.Rect()
                val adapterPosition = h.bindingAdapterPosition
                val threshold = minOf(h.root.height * 0.5f, resources.displayMetrics.heightPixels * 0.35f)
                if (adapterPosition != RecyclerView.NO_POSITION &&
                    data.getOrNull(adapterPosition)?.id == p.id &&
                    h.root.height > 0 && h.root.getGlobalVisibleRect(rect) &&
                    rect.height() >= threshold) {
                    if (boundSession.seenIds.add(p.id)) persistSeen(boundSession)
                }
            }, 900L)

            h.tag.text = if (featured) "FEATURED" else p.title
            h.lbl.text = if (featured) p.title + "\n" + p.credit.ifBlank { getString(R.string.unknown_photographer) }
                else "Photo by " + p.credit.ifBlank { getString(R.string.unknown_photographer) }
            h.heart.setHeart(Store.isFav(this@MainActivity, p.id))
            h.heart.contentDescription = getString(R.string.like_desc)

            if (featured) {
                h.img.layoutParams.height = 236.dp()
            } else {
                val screenWidth = resources.displayMetrics.widthPixels - 32.dp()
                val ratio = if (p.width > 0 && p.height > 0) p.height.toFloat() / p.width.toFloat() else 1.18f
                val screenHeight = resources.displayMetrics.heightPixels
                // Prototype v10 cards occupy roughly 74–86% of the locked screen height.
                val immersiveHeight = (screenHeight * 0.82f).toInt()
                h.img.layoutParams.height = maxOf(immersiveHeight, (screenWidth * ratio).toInt()).coerceIn(430.dp(), 720.dp())
            }
            h.img.setBackgroundColor(p.color)

            if (visualEffectsEnabled(this@MainActivity) && motionEnabled(this@MainActivity) && animatedPhotoIds.add(p.id)) {
                h.root.animate().cancel()
                h.root.alpha = 0.78f
                h.root.translationY = 18.dp().toFloat()
                h.root.scaleX = 0.985f
                h.root.scaleY = 0.985f
                h.root.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(420L).setInterpolator(DecelerateInterpolator(1.5f)).start()
            } else {
                h.root.animate().cancel()
                h.root.alpha = 1f; h.root.translationY = 0f; h.root.scaleX = 1f; h.root.scaleY = 1f
            }

            Glide.with(h.img).load(p.thumb).disallowHardwareConfig().centerCrop()
                .error(Glide.with(h.img).load(p.full).disallowHardwareConfig().centerCrop()).into(h.img)

            h.heart.setOnClickListener {
                Store.toggle(this@MainActivity, p)
                if (tab == 1) refreshSaved() else h.heart.setHeart(Store.isFav(this@MainActivity, p.id))
            }
            h.root.setOnClickListener {
                if (boundSession.seenIds.add(p.id)) persistSeen(boundSession)
                startActivity(Intent(this@MainActivity, ViewerActivity::class.java).putExtra("p", p.toJson().toString()))
            }
        }
    }
}
