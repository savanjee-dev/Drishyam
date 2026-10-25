package com.savan.drishyam

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
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

class Source(val name: String, val unsplash: Boolean, val q: String, val searchOnly: Boolean = false) {
    val items = ArrayList<Photo>()
    val ids = HashSet<String>()
    var after = ""
    var page = 1
    var done = false
    var loading = false
    var err: String? = null
    var gen = 0          // bumps on reset so late results from an old sort are ignored
    var extra = false    // searched photo topics, removed when the next search runs

    fun reset() {
        items.clear(); ids.clear()
        after = ""; page = 1; done = false; err = null; loading = false
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
    private var lastTopPad = -1
    private var lastBottomPad = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lowFxAtCreate = lowFx(this)

        rv = findViewById(R.id.rv)
        status = findViewById(R.id.status)
        statusPill = findViewById(R.id.statusPill)
        chips = findViewById(R.id.chips)
        chipScroll = findViewById(R.id.chipScroll)
        sortBar = findViewById(R.id.sortBar)
        savedBar = findViewById(R.id.savedBar)
        swipe = findViewById(R.id.swipe)
        aboutScroll = findViewById(R.id.about)
        glassSource = findViewById(R.id.glassSource)
        glassTop = findViewById(R.id.glassTop)
        glassNav = findViewById(R.id.glassNav)
        overlay = findViewById(R.id.overlay)
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

        swipe.setColorSchemeColors(0xFF9D7BFF.toInt(), 0xFFFF7AB6.toInt())
        swipe.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface))
        swipe.setOnRefreshListener { refreshCurrent() }

        animateTitle()
        animateAbout()
        setupAbout()

        findViewById<View>(R.id.searchButton).apply { pressFx(); setOnClickListener { showSearch() } }
        findViewById<View>(R.id.settingsButton).apply {
            pressFx()
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsHubActivity::class.java)) }
        }

        for ((name, query) in PHOTO_CATEGORIES) sources.add(Source(name, true, query))
        cur = sources.first()

        rv.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        rv.edgeEffectFactory = SpringEdgeEffectFactory()
        installScrollBounce(aboutScroll)
        installScrollBounce(chipScroll)
        rv.adapter = adapter
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(8)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                pokeGlass()
                if (tab == 0 && dy > 3) setNavVisible(false)
                else if (dy < -1) setNavVisible(true)
                val lm = r.layoutManager as LinearLayoutManager
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.itemCount - 4) loadMore()
            }
        })

        status.setOnClickListener { if (tab == 0 && cur.err != null) { cur.err = null; loadMore() } }

        buildChips()

        listOf(R.id.n0 to 0, R.id.n1 to 1, R.id.n2 to 2).forEach { (id, n) ->
            findViewById<View>(id).apply { pressFx(); setOnClickListener { showTab(n) } }
        }
        segFav.apply { pressFx(); setOnClickListener { setSaved(0) } }
        segBook.apply { pressFx(); setOnClickListener { setSaved(1) } }
        sortHot.apply { pressFx(); setOnClickListener { setSort("hot") } }
        sortNew.apply { pressFx(); setOnClickListener { setSort("new") } }
        sortTop.apply { pressFx(); setOnClickListener { setSort("top") } }

        shownSort = currentSort()
        styleSort()
        showTab(0)
        glassSource.postDelayed({ UpdateManager.check(this, glassSource) }, 1800L)
        animatePageEntrance(findViewById(android.R.id.content))
    }

    override fun onResume() {
        super.onResume()
        if (lowFx(this) != lowFxAtCreate) { recreate(); return }
        // Settings may have changed the default sort while this screen was paused.
        val s = currentSort()
        if (s != shownSort) { shownSort = s; styleSort(); resetAll() }
        if (tab == 1) refreshSaved() else adapter.notifyDataSetChanged()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!PopupStack.dismiss()) super.onBackPressed()
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
        if (top == lastTopPad && bottom == lastBottomPad) return
        lastTopPad = top
        lastBottomPad = bottom
        rv.setPadding(14.dp(), top, 14.dp(), bottom)
        aboutScroll.setPadding(0, top, 0, bottom)
        swipe.setProgressViewOffset(false, top, top + 60.dp())
    }

    private var navShown = true
    private fun setNavVisible(visible: Boolean) {
        if (navShown == visible || tab != 0) return
        navShown = visible
        glassNav.animate().cancel()
        glassNav.animate()
            .translationY(if (visible) 0f else glassNav.height.toFloat() + 28.dp())
            .alpha(if (visible) 1f else 0f)
            .setDuration(if (visible) 210L else 180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
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
        findViewById<ImageView>(R.id.aboutPhoto).setImageResource(R.drawable.savan)
        findViewById<ImageView>(R.id.aboutPhoto2).setImageResource(R.drawable.akshat_photo_1)
        findViewById<ImageView>(R.id.aboutPhoto3).setImageResource(R.drawable.akshat_photo_2)
        findViewById<ImageView>(R.id.aboutPhoto4).setImageResource(R.drawable.akshat_photo_3)
        val blur = findViewById<ImageView>(R.id.aboutBlur)
        blur.setImageResource(R.drawable.savan_background)
        // Real background blur needs Android 12+; low-effects mode skips it.
        if (Build.VERSION.SDK_INT >= 31 && !lowFx(this)) {
            blur.setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
        }
        findViewById<View>(R.id.ig).apply {
            pressFx()
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/savanjee"))) }
        }
    }

    private fun animateAbout() {
        if (lowFx(this)) return
        val halo = findViewById<View>(R.id.aboutHalo)
        ValueAnimator.ofFloat(0.88f, 1.12f).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val s = a.animatedValue as Float
                halo.scaleX = s
                halo.scaleY = s
                halo.alpha = 0.5f + (s - 0.88f) * 1.2f
            }
            start()
        }
    }

    private fun animateTitle() {
        if (lowFx(this)) return
        val t = findViewById<TextView>(R.id.title)
        t.post {
            val w = t.paint.measureText(t.text.toString()).coerceAtLeast(100f) * 2
            val colors = intArrayOf(0xFFFF9966.toInt(), 0xFFFF5E62.toInt(), 0xFFC04BFF.toInt(), 0xFF5B8CFF.toInt(), 0xFFFF9966.toInt())
            val sh = LinearGradient(0f, 0f, w, 0f, colors, null, Shader.TileMode.REPEAT)
            t.paint.shader = sh
            val m = Matrix()
            ValueAnimator.ofFloat(0f, w).apply {
                duration = 5000
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
        glassNav.animate().cancel()
        glassNav.visibility = View.VISIBLE
        glassNav.translationY = 0f
        glassNav.alpha = 1f
        listOf(R.id.n0, R.id.n1, R.id.n2).forEachIndexed { i, id ->
            val v = findViewById<TextView>(id)
            val on = i == n
            v.background = if (on) ContextCompat.getDrawable(this, R.drawable.accent_pill) else null
            v.setTextColor(ContextCompat.getColor(this, if (on) android.R.color.white else R.color.mute))
        }
        findViewById<View>(R.id.home).visibility = if (n < 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.about).visibility = if (n == 2) View.VISIBLE else View.GONE
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
    }

    // ---------- Chips ----------

    private fun buildChips() {
        chips.removeAllViews()
        for (s in sources) chips.addView(chip(s.name, s) { select(s) })
        chips.addView(chip(getString(R.string.shuffle), "shuffle") { shuffleCurrent() })
        styleChips()
    }

    private fun chip(label: String, key: Any, onTap: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(15.dp(), 0, 15.dp(), 0)
        tag = key
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 36.dp()).apply { marginEnd = 8.dp() }
        pressFx()
        setOnClickListener { onTap() }
    }

    private fun styleChips() {
        for (i in 0 until chips.childCount) {
            val tv = chips.getChildAt(i) as TextView
            tv.styleSeg(tv.tag === cur)
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

    private fun resetAll() {
        sources.forEach { it.reset() }
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
            cur.err != null -> cur.err + " " + getString(R.string.retry_suffix)
            cur.done -> getString(R.string.end_of_feed)
            else -> ""
        }
        status.text = text
        statusPill.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun refreshCurrent() {
        if (tab != 0 || cur.loading) { swipe.isRefreshing = false; return }
        cur.reset()
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(0)
        loadMore()
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

        Thread {
            val added = ArrayList<Photo>()
            var nextAfter = after
            var finished = false
            var error: String? = null
            try {
                val (list, last) = Api.unsplash(s.q, page)
                finished = last
                added.addAll(list)
            } catch (e: Exception) {
                val m = e.message ?: ""
                error = when {
                    m.contains("429") -> "Unsplash is temporarily rate-limiting requests. Please wait and try again."
                    m.contains("403") || m.contains("Forbidden", true) -> "Photo service authorization failed (403). Check proxy settings."
                    m.contains("404") -> "This feed was not found."
                    m.contains("UnknownHost") || m.contains("timeout", true) || m.contains("Unable to resolve", true) -> "Network unavailable. Check your internet."
                    else -> "Couldn’t load photos from Unsplash ($m)."
                }
            }

            runOnUiThread {
                if (gen != s.gen) return@runOnUiThread   // sort or refresh happened meanwhile
                s.loading = false
                swipe.isRefreshing = false
                if (error != null) {
                    s.err = error
                } else {
                    s.after = nextAfter
                    s.page = page + 1
                    if (finished) s.done = true
                }
                val fresh = added.filter { s.ids.add(it.id) }
                val start = s.items.size
                s.items.addAll(fresh)
                if (s === cur && tab == 0) {
                    adapter.notifyItemRangeInserted(start, fresh.size)
                    updateStatus()
                    rv.post { if (!rv.canScrollVertically(1)) loadMore() }
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
            h.root.animate().cancel()
            h.root.alpha = 0.86f
            h.root.translationY = 12.dp().toFloat()
            h.root.animate().alpha(1f).translationY(0f).setDuration(320L)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
            // Software bitmaps only: the glass bars copy what is behind them, and cannot read hardware ones.
            Glide.with(h.img).load(p.thumb).disallowHardwareConfig().centerCrop().into(h.img)
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
