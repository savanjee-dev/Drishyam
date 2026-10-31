package com.savan.drishyam

import android.animation.ValueAnimator
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import android.content.res.ColorStateList

class Source(val name: String, val unsplash: Boolean, val q: String, val searchOnly: Boolean = false) {
    val items = ArrayList<Photo>()
    val ids = HashSet<String>()
    // Keep recently displayed IDs across pull-to-refresh so repeated refreshes
    // can skip overlapping provider results instead of showing the same photos.
    val seenIds = HashSet<String>()
    var refreshPage = 1
    // Discovery rotation is specific to this feed and survives ordinary resets.
    var discoveryIndex = 0
    var after = ""
    var page = 1
    var done = false
    var loading = false
    var err: String? = null
    var gen = 0          // bumps on reset so late results from an old sort are ignored
    var extra = false    // searched photo topics, removed when the next search runs
    var forceFresh = false

    fun reset(clearHistory: Boolean = false) {
        items.clear(); ids.clear()
        if (clearHistory) { seenIds.clear(); refreshPage = 1 }
        after = ""; page = 1; done = false; err = null; loading = false; forceFresh = false
        gen++
    }
}

class MainActivity : AppCompatActivity() {

    companion object {
        // Curated Unsplash categories only; no Reddit/photo topic sources are exposed in the UI.
        val PHOTO_CATEGORIES = listOf(
            "For You" to "cinematic nature landscape photography",
            "Forest" to "forest woodland trees",
            "Nature" to "nature photography",
            "Earth" to "planet earth landscape",
            "Hiking" to "hiking trail outdoors",
            "Mountains" to "mountain landscape",
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
    private lateinit var chips: LinearLayout
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var sortBar: View
    private lateinit var savedBar: View
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var aboutScroll: ScrollView
    private lateinit var glassSource: View
    private lateinit var glassTop: GlassLayout
    private lateinit var glassNav: GlassLayout
    private lateinit var overlay: View
    private lateinit var segFav: TextView
    private lateinit var segBook: TextView
    private lateinit var sortHot: TextView
    private lateinit var sortNew: TextView
    private lateinit var sortTop: TextView
    private val adapter = PhotoAdapter()
    private val sources = ArrayList<Source>()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        super.onCreate(savedInstanceState)
        applyActivityTransitionPreference(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) SplashOverlay.show(this)
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
        chips = findViewById(R.id.chips)
        chipScroll = findViewById(R.id.chipScroll)
        chipScroll.isHorizontalFadingEdgeEnabled = true
        chipScroll.setFadingEdgeLength(24.dp())
        chipScroll.clipChildren = true
        chipScroll.clipToPadding = true
        sortBar = findViewById(R.id.sortBar)
        savedBar = findViewById(R.id.savedBar)
        swipe = findViewById(R.id.swipe)
        aboutScroll = findViewById(R.id.about)
        aboutScroll.isVerticalScrollBarEnabled = false
        glassSource = findViewById(R.id.glassSource)
        glassTop = findViewById(R.id.glassTop)
        glassNav = findViewById(R.id.glassNav)
        overlay = findViewById(R.id.overlay)

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
        sortHot = findViewById(R.id.sortHot)
        sortNew = findViewById(R.id.sortNew)
        sortTop = findViewById(R.id.sortTop)

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

        animateTitle()
        animateAbout()
        setupAbout()

        findViewById<ImageView>(R.id.sortButton).apply {
            imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.fg))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.glass_pill)
            pressFx()
            contentDescription = "Sort photos"
            setOnClickListener { showSortPopup() }
        }

        for ((name, query) in PHOTO_CATEGORIES) sources.add(Source(name, true, query))
        cur = sources.first()

        rv.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        installScrollBounce(aboutScroll)
        installScrollBounce(chipScroll)
        rv.adapter = adapter
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(8)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                pokeGlass()
                if (tab == 0 && dy > 3) {
                    setNavVisible(false)
                    setTopVisible(true)
                } else if (dy < -1) {
                    setNavVisible(true)
                    setTopVisible(true)
                }
                val lm = r.layoutManager as LinearLayoutManager
                if (dy > 0) {
                    val remaining = adapter.itemCount - lm.findLastVisibleItemPosition() - 1
                    if (remaining <= 7 || remaining <= backgroundPrefetchThreshold()) loadMore()
                }
            }
        })

        status.setOnClickListener { if (tab == 0 && cur.err != null) { cur.err = null; loadMore() } }

        buildChips()

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
        sortHot.apply { pressFx(); setOnClickListener { setSort("hot") } }
        sortNew.apply { pressFx(); setOnClickListener { setSort("new") } }
        sortTop.apply { pressFx(); setOnClickListener { setSort("top") } }

        shownSort = currentSort()
        styleSort()
        showTab(0)
        if (intent.getBooleanExtra("open_about", false)) showTab(2)
        glassSource.postDelayed({ if (!showPendingWhatsNewIfNeeded()) UpdateManager.check(this, glassSource) }, 1800L)
        if (!PortalTransition.hasOpeningOrigin(this)) animatePageEntrance(findViewById(android.R.id.content))
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
        val sources = listOf("source_wikimedia", "source_openverse", "source_unsplash")
            .joinToString("|") { key -> prefs.getBoolean(key, true).toString() }
        return sources + "|" +
            (prefs.getString("image_quality", "High") ?: "High") + "|" +
            (prefs.getString("orientation", "Portrait") ?: "Portrait")
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
        if (s != shownSort) { shownSort = s; styleSort(); shouldResetFeed = true }
        if (shouldResetFeed) resetAll()
        if (tab == 1) refreshSaved() else adapter.notifyDataSetChanged()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (PopupStack.dismiss()) return
        if (tab == 2) { PortalTransition.close(this, null); return }
        super.onBackPressed()
    }

    // ---------- Floating glass bars ----------

    private fun pokeGlass() {
        glassTop.poke()
        glassNav.poke()
        if (statusPill.visibility == View.VISIBLE) statusPill.poke()
    }

    private fun applyInsets() {
        val top = glassTop.bottom + 8.dp()
        val bottom = if (navShown && glassNav.visibility == View.VISIBLE) (overlay.height - glassNav.top) + 8.dp() else 20.dp()
        if (top != lastTopPad || bottom != lastBottomPad) {
            lastTopPad = top
            lastBottomPad = bottom
            rv.setPadding(14.dp(), top, 14.dp(), bottom)
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
        val t = findViewById<TextView>(R.id.title)
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
        glassTop.visibility = View.VISIBLE
        glassTop.translationY = 0f
        glassTop.alpha = 1f
        glassNav.animate().cancel()
        glassNav.visibility = View.VISIBLE
        glassNav.translationY = 0f
        glassNav.alpha = 1f
        val selectedNav = when (n) { 0 -> 0; 1 -> 2; else -> -1 }
        listOf(R.id.n0, R.id.n1, R.id.n2, R.id.n3).forEachIndexed { i, id ->
            val v = findViewById<ImageView>(id)
            val on = i == selectedNav
            v.background = if (on) ContextCompat.getDrawable(this, R.drawable.accent_pill) else null
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
            findViewById<View>(itemId).setBackgroundResource(
                if (on) R.drawable.glass_pill else android.R.color.transparent
            )
        }
        findViewById<View>(R.id.home).visibility = if (n < 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.about).visibility = if (n == 2) View.VISIBLE else View.GONE
        glassNav.visibility = if (n == 2) View.GONE else View.VISIBLE
        if (n == 2) { glassNav.animate().cancel(); glassNav.translationY = glassNav.height.toFloat() + 28.dp(); glassNav.alpha = 0f }
        glassTop.visibility = if (n == 2) View.GONE else View.VISIBLE
        chipScroll.visibility = if (n == 0) View.VISIBLE else View.GONE
        sortBar.visibility = View.GONE
        savedBar.visibility = if (n == 1) View.VISIBLE else View.GONE

        if (n == 1) {
            refreshSaved()
        } else {
            adapter.data = cur.items
            adapter.notifyDataSetChanged()
            updateStatus()
            if (cur.items.isEmpty()) loadMore()
        }
        pokeGlass()
        if (n == 2) animateAbout()
    }

    // ---------- Chips ----------

    private fun buildChips() {
        chips.removeAllViews()
        for (s in sources) chips.addView(chip(s.name, s) { select(s) })
        chips.addView(chip(getString(R.string.shuffle), "shuffle") { shuffleCurrent() })
        styleChips()
    }

    private fun chip(label: String, key: Any, onTap: () -> Unit): Chip = Chip(this).apply {
        text = label
        tag = key
        isCheckable = false
        isClickable = true
        isFocusable = true
        chipMinHeight = 42.dp().toFloat()
        chipCornerRadius = 21.dp().toFloat()
        textSize = 13f
        setEnsureMinTouchTargetSize(false)
        setPadding(4.dp(), 0, 4.dp(), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42.dp()).apply {
            marginEnd = 8.dp()
        }
        pressFx()
        setOnClickListener { onTap() }
    }

    private fun styleChips() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val selected = if (night) 0xFFD0C79A.toInt() else 0xFF414D2D.toInt()
        val onSelected = if (night) 0xFF0A0D08.toInt() else 0xFFF3F1E7.toInt()
        val surface = if (night) 0xFF293022.toInt() else 0xFFE8E6DA.toInt()
        val onSurface = if (night) 0xFFE5E7D8.toInt() else 0xFF41483A.toInt()
        for (i in 0 until chips.childCount) {
            val item = chips.getChildAt(i) as? Chip ?: continue
            val active = item.tag === cur
            item.chipBackgroundColor = ColorStateList.valueOf(if (active) selected else surface)
            item.setTextColor(if (active) onSelected else onSurface)
            item.chipStrokeWidth = if (active) 0f else 1.dp().toFloat()
            item.chipStrokeColor = ColorStateList.valueOf(if (night) 0xFF59634A.toInt() else 0xFFC8CCB9.toInt())
            item.elevation = if (active) 2.dp().toFloat() else 0f
            item.animate().cancel()
            if (motionEnabled(this)) {
                item.animate().scaleX(if (active) 1.02f else 1f).scaleY(if (active) 1.02f else 1f)
                    .setDuration(180L).setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else {
                item.scaleX = 1f
                item.scaleY = 1f
            }
        }
    }

    private fun select(s: Source) {
        cur = s
        styleChips()
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
        if (s == currentSort()) {
            // Tapping the already-selected sort is also a useful manual refresh.
            refreshCurrent()
            return
        }
        getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().putString("sort", s).apply()
        shownSort = s
        styleSort()
        resetAll()
    }

    private fun styleSort() {
        sortHot.styleSeg(shownSort == "hot")
        sortNew.styleSeg(shownSort == "new")
        sortTop.styleSeg(shownSort == "top")
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
                    setOnClickListener { close(); setSort(key) }
                }, LinearLayout.LayoutParams(-1, 44.dp()).apply { topMargin = 5.dp() })
            }
        }
    }

    private fun resetAll() {
        sources.forEach { it.reset(clearHistory = true) }
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
        showGlassPopup(this, glassSource, topOffsetDp = 110) { box, close ->
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_search, box, false)
            box.addView(view)
            val input = view.findViewById<EditText>(R.id.searchInput)
            val go = view.findViewById<View>(R.id.searchGo)
            view.findViewById<View>(R.id.searchCancel).apply { pressFx(); setOnClickListener { close() } }
            go.apply {
                pressFx()
                setOnClickListener {
                    val q = input.text.toString().trim()
                    close()
                    if (q.isBlank()) toast(getString(R.string.search_empty)) else runSearch(q)
                }
            }
            input.setOnEditorActionListener { _, _, _ -> go.performClick(); true }
            input.postDelayed({
                input.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 250)
        }
    }

    private fun runSearch(q: String) {
        // Free-text search always uses Unsplash; photo topic names are no longer interpreted.
        sources.removeAll { it.extra || it.searchOnly || it.name == "Search" }
        val src = Source("Search", true, q, false).apply { extra = true }
        sources.add(0, src)
        buildChips()
        select(src)
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
            cur.loading -> getString(R.string.loading_more)
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
        if (tab != 0 || cur.loading) { swipe.isRefreshing = false; return }
        transientStatus = null
        statusGeneration++
        // Rotate For You's discovery theme on every manual refresh. Start the
        // new query at page one; seenIds prevents previously shown photos returning.
        if (cur.name == "For You") cur.discoveryIndex++
        cur.refreshPage = maxOf(cur.refreshPage + 1, cur.page)
        cur.reset()
        cur.page = if (cur.name == "For You") 1 else cur.refreshPage
        cur.forceFresh = true
        adapter.data = cur.items
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
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
        val remaining = adapter.itemCount - lastVisible - 1
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
        val sort = currentSort()
        val forceFresh = s.forceFresh
        s.forceFresh = false

        Thread {
            val added = ArrayList<Photo>()
            var nextAfter = after
            var nextCommonsPage = page
            var finished = false
            var error: String? = null
            val providerErrors = ArrayList<String>()
            val query = if (sort == "india" && s.name != "Search") "India photography" else s.q
            val fallbackQuery = if (sort == "india" && s.name != "Search") "India landscape monument architecture" else s.q
            val browsingPrefs = getSharedPreferences("drishyam_browsing", Context.MODE_PRIVATE)
            val useWikimedia = browsingPrefs.getBoolean("source_wikimedia", true)
            val useOpenverse = browsingPrefs.getBoolean("source_openverse", true)
            val useUnsplash = browsingPrefs.getBoolean("source_unsplash", true)
            val imageQuality = browsingPrefs.getString("image_quality", "High") ?: "High"
            val preferredOrientation = browsingPrefs.getString("orientation", "Portrait") ?: "Portrait"

            if (!useWikimedia && !useOpenverse && !useUnsplash) {
                error = "No photo sources are enabled. Enable at least one source in Settings → Browsing."
            } else {
                // MilkyWay provider order: Wikimedia Commons → Openverse → Unsplash.
                // A disabled provider is never queried, including as a fallback.
                if (useWikimedia && added.isEmpty()) {
                    try {
                        val forYouThemes = listOf(
                            "mountain landscape photography",
                            "wildlife nature photography",
                            "ocean coast photography",
                            "forest nature photography",
                            "aerial landscape photography",
                            "macro nature photography",
                            "desert landscape photography",
                            "night sky astrophotography",
                            "street travel photography",
                            "flowers botanical photography",
                            "waterfall river photography",
                            "architecture city photography"
                        )
                        val commonsQuery = when (s.name) {
                            "For You" -> forYouThemes[s.discoveryIndex % forYouThemes.size]
                            "Forest" -> "forest woodland photograph"
                            "Nature" -> "nature photograph"
                            "Earth" -> "earth landscape photograph"
                            "Hiking" -> "hiking trail photograph"
                            "Mountains" -> "mountain landscape photograph"
                            "Wildlife" -> "wildlife animal photograph"
                            "Architecture" -> "architecture photograph"
                            "Abstract" -> "abstract photography"
                            "Minimalism" -> "minimalist photograph"
                            "Travel" -> "travel landscape photograph"
                            "Street" -> "street photography"
                            "Ocean" -> "ocean sea photograph"
                            "Night Sky" -> "night sky photograph"
                            else -> fallbackQuery
                        }
                        // Prefer the selected orientation while retaining a small,
                        // Resplash-style mix of other shapes. Unknown dimensions remain
                        // eligible as secondary candidates instead of emptying the feed.
                        val primaryPhotos = ArrayList<Photo>()
                        val secondaryPhotos = ArrayList<Photo>()
                        var attempts = 0
                        while (primaryPhotos.size < 21 && attempts < 3 && !finished) {
                            val (photos, last) = Api.commons(commonsQuery, nextCommonsPage, imageQuality)
                            nextCommonsPage++
                            attempts++
                            finished = last
                            val orientationTarget = when (preferredOrientation) {
                                "Portrait" -> "Portrait"
                                "Landscape" -> "Landscape"
                                "Square" -> "Square"
                                "Random" -> if ((s.discoveryIndex + attempts) % 2 == 0) "Portrait" else "Landscape"
                                else -> "Portrait"
                            }
                            for (photo in photos) {
                                if (photo.id in s.seenIds ||
                                    primaryPhotos.any { it.id == photo.id } ||
                                    secondaryPhotos.any { it.id == photo.id }) continue
                                val isPrimary = when (orientationTarget) {
                                    "Portrait" -> photo.height > photo.width && photo.width > 0
                                    "Landscape" -> photo.width > photo.height && photo.height > 0
                                    "Square" -> photo.width > 0 && photo.height > 0 &&
                                        kotlin.math.abs(photo.width - photo.height).toFloat() /
                                        maxOf(photo.width, photo.height) <= 0.12f
                                    else -> photo.height > photo.width && photo.width > 0
                                }
                                if (isPrimary) primaryPhotos.add(photo) else secondaryPhotos.add(photo)
                            }
                            if (primaryPhotos.size >= 21 && secondaryPhotos.size >= 4) break
                        }

                        // Fill the preferred orientation first, then allow up to three
                        // alternatives in a 20-photo batch. If Commons has too few
                        // preferred results, use alternatives rather than showing an empty feed.
                        val orderedCandidates = primaryPhotos.take(21) +
                            secondaryPhotos.take(4) +
                            primaryPhotos.drop(21) +
                            secondaryPhotos.drop(4)
                        val photographerCounts = HashMap<String, Int>()
                        for (photo in orderedCandidates) {
                            if (added.size >= 25) break
                            val author = photo.credit.trim().lowercase()
                            val hasAuthor = author.isNotBlank() && author != "wikimedia commons"
                            if (hasAuthor && (photographerCounts[author] ?: 0) >= 2) continue
                            added.add(photo)
                            if (hasAuthor) photographerCounts[author] = (photographerCounts[author] ?: 0) + 1
                        }
                    } catch (e: Exception) {
                        providerErrors.add("Wikimedia: " + (e.message ?: "request failed").take(120))
                    }
                }

                // Openverse anonymous pagination is capped at five pages. Keep its
                // cursor independent from Wikimedia's longer-running search offset.
                val openversePage = ((page - 1) % 5) + 1
                if (useOpenverse && added.isEmpty()) {
                    try {
                        val (photos, last) = Api.openverse(fallbackQuery, openversePage)
                        val unseen = photos.filter { it.id !in s.seenIds }
                        if (unseen.isNotEmpty()) {
                            added.addAll(unseen)
                            finished = last
                        }
                    } catch (e: Exception) {
                        providerErrors.add("Openverse: " + (e.message ?: "request failed").take(120))
                    }
                }

                if (useUnsplash && added.isEmpty()) {
                    try {
                        val (photos, last) = Api.unsplash(
                            query, page,
                            orderBy = if (sort == "new") "latest" else "relevant",
                            forceFresh = forceFresh,
                            indiaOnly = sort == "india" && s.name != "Search",
                            popular = sort == "hot"
                        )
                        val unseen = photos.filter { it.id !in s.seenIds }
                        if (unseen.isNotEmpty()) {
                            finished = last
                            added.addAll(if (sort == "top") unseen.sortedByDescending { it.likes } else unseen)
                        }
                    } catch (e: Exception) {
                        providerErrors.add("Unsplash: " + (e.message ?: "request failed").take(120))
                    }
                }

                if (added.isEmpty()) {
                    error = if (providerErrors.isNotEmpty()) {
                        "Home feed couldn't load. " + providerErrors.joinToString(" · ")
                    } else if (useWikimedia && !useOpenverse && !useUnsplash) {
                        "Wikimedia Commons has no more unseen photos for this category. Try another category or refresh later."
                    } else {
                        "Enabled photo sources returned no matching photos. Try another category or refresh."
                    }
                }
            }

            runOnUiThread {
                if (gen != s.gen) return@runOnUiThread   // sort or refresh happened meanwhile
                s.loading = false
                swipe.isRefreshing = false
                s.page = if (useWikimedia) nextCommonsPage else page + 1
                if (error != null) {
                    s.err = error
                    statusPill.setTag(R.id.statusPill, null)
                } else {
                    s.after = nextAfter
                    if (finished) s.done = true
                }
                val fresh = added.filter { s.ids.add(it.id) && s.seenIds.add(it.id) }
                val start = s.items.size
                s.items.addAll(fresh)
                if (s === cur && tab == 0) {
                    adapter.notifyItemRangeInserted(start, fresh.size)
                    if (error != null) {
                        showTransientStatus(error + " " + getString(R.string.retry_suffix))
                    } else {
                        val message = when {
                            fresh.isNotEmpty() -> "Loaded ${fresh.size} photos"
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

    // ---------- Adapter ----------

    inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.VH>() {
        var data: List<Photo> = ArrayList()

        inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
            val img: ImageView = root.findViewById(R.id.img)
            val lbl: TextView = root.findViewById(R.id.lbl)
            val heart: ImageView = root.findViewById(R.id.heart)
            init { heart.pressFx() }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 14.dp())
            }
            return VH(v)
        }

        private val animatedPhotoIds = HashSet<String>()

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val p = data[pos]
            h.lbl.text = "Photo by " + p.credit.ifBlank { getString(R.string.unknown_photographer) }
            h.heart.setHeart(Store.isFav(this@MainActivity, p.id))
            h.heart.contentDescription = getString(R.string.like_desc)
            val screenWidth = resources.displayMetrics.widthPixels - 28.dp()
            val ratio = if (p.width > 0 && p.height > 0) p.height.toFloat() / p.width.toFloat() else 1.18f
            h.img.layoutParams.height = (screenWidth * ratio).toInt().coerceIn(200.dp(), 650.dp())
            h.img.setBackgroundColor(p.color)
            // Animate each photo only on its first bind. RecyclerView rebinds happen during
            // scrolling and state updates; replaying the entrance there causes distracting flicker.
            if (visualEffectsEnabled(this@MainActivity) && motionEnabled(this@MainActivity) && animatedPhotoIds.add(p.id)) {
                h.root.animate().cancel()
                h.root.alpha = 0.78f
                h.root.translationY = 18.dp().toFloat()
                h.root.scaleX = 0.985f
                h.root.scaleY = 0.985f
                h.root.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(420L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
            } else {
                h.root.animate().cancel()
                h.root.alpha = 1f
                h.root.translationY = 0f
                h.root.scaleX = 1f
                h.root.scaleY = 1f
            }
            // Software bitmaps only: the glass bars copy what is behind them, and cannot read hardware ones.
            // Some Unsplash thumbnail URLs can fail independently of the photo itself.
            // Retry with the full image URL so a transient thumbnail failure does not leave a blank tile.
            Glide.with(h.img)
                .load(p.thumb)
                .disallowHardwareConfig()
                .centerCrop()
                .error(Glide.with(h.img).load(p.full).disallowHardwareConfig().centerCrop())
                .into(h.img)
            h.heart.setOnClickListener {
                Store.toggle(this@MainActivity, p)
                if (tab == 1) refreshSaved() else h.heart.setHeart(Store.isFav(this@MainActivity, p.id))
            }
            h.root.setOnClickListener {
                startActivity(Intent(this@MainActivity, ViewerActivity::class.java).putExtra("p", p.toJson().toString()))
            }
        }
    }
}
