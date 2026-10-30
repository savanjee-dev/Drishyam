package com.savan.drishyam

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * Launch splash (Phase H1). Built to match Drishyam_Home_Prototype_v10_Fade.html (section "Launch splash").
 * Shown once per cold start, holds for 3.5 s (SPLASH_MS), then dissolves into Home.
 * If the Home header logo exists (id "headerLogo"), the logo glides into its position first.
 */
class SplashOverlay private constructor(private val host: Activity) : FrameLayout(host) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Number): Int = (v.toFloat() * density).toInt()
    private fun dpf(v: Number): Float = v.toFloat() * density
    private fun color(id: Int): Int = ContextCompat.getColor(context, id)
    private val handler = Handler(Looper.getMainLooper())
    private val animators = mutableListOf<ValueAnimator>()
    private var finished = false
    private val reduced: Boolean = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        .getString("motion", "standard") == "reduced"

    private lateinit var centre: LinearLayout
    private lateinit var logo: ImageView
    private lateinit var ring: View
    private lateinit var glow: View
    private lateinit var fill: View
    private lateinit var bar: FrameLayout
    private val letters = mutableListOf<TextView>()
    private lateinit var sig: TextView
    private lateinit var tag: TextView
    private lateinit var foot: TextView

    init {
        isClickable = true
        isFocusable = true
        background = RadialDrawable(
            intArrayOf(color(R.color.surface), color(R.color.bg)), floatArrayOf(0f, 0.62f),
            cxFrac = 0.5f, cyFrac = 0.32f, rxFrac = 1.2f, ryFrac = 0.8f, ellipse = true
        )
        buildContent()
        startAnimations()
    }

    private fun buildContent() {
        glow = View(context).apply {
            background = RadialDrawable(
                intArrayOf(0x6B89945A, 0x0089945A), floatArrayOf(0f, 0.66f),
                cxFrac = 0.5f, cyFrac = 0.5f, rxFrac = 0.5f, ryFrac = 0.5f, ellipse = false
            )
        }
        addView(glow, LayoutParams(dp(460), dp(460), Gravity.CENTER))

        centre = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), 0)
        }
        addView(centre, LayoutParams(-2, -2, Gravity.CENTER))

        val wrap = FrameLayout(context)
        ring = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpf(44)
                setStroke(dp(2), color(R.color.accent2))
                setColor(Color.TRANSPARENT)
            }
            alpha = 0f
        }
        wrap.addView(ring, FrameLayout.LayoutParams(dp(138), dp(138), Gravity.CENTER))
        logo = ImageView(context).apply {
            setImageResource(R.drawable.drishyam_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Drishyam"
            elevation = dpf(14)
            alpha = 0f
            scaleX = 0.55f
            scaleY = 0.55f
            rotation = -14f
        }
        wrap.addView(logo, FrameLayout.LayoutParams(dp(118), dp(118), Gravity.CENTER))
        centre.addView(wrap, LinearLayout.LayoutParams(dp(138), dp(138)))

        val name = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for (c in "Drishyam") {
            val t = TextView(context).apply {
                text = c.toString()
                textSize = 42f
                setTextColor(color(R.color.fg))
                typeface = ResourcesCompat.getFont(context, R.font.ericaone)
                letterSpacing = 0.01f
                alpha = 0f
                translationY = dpf(20)
            }
            letters.add(t)
            name.addView(t)
        }
        centre.addView(name, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22) })

        sig = TextView(context).apply {
            text = "by Savan"
            textSize = 17f
            setTextColor(color(R.color.accent))
            typeface = ResourcesCompat.getFont(context, R.font.pacifico_regular)
            alpha = 0f
        }
        centre.addView(sig, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })

        tag = TextView(context).apply {
            val full = "See the world a little slower."
            val span = android.text.SpannableString(full)
            val start = full.indexOf("slower.")
            span.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, full.length, 0)
            span.setSpan(android.text.style.ForegroundColorSpan(color(R.color.fg)), start, full.length, 0)
            text = span
            textSize = 14f
            letterSpacing = 0.04f
            setTextColor(color(R.color.mute))
            typeface = ResourcesCompat.getFont(context, R.font.poppins_regular)
            alpha = 0f
        }
        centre.addView(tag, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) })

        bar = FrameLayout(context).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(999); setColor(withAlpha(color(R.color.accent2), 0.22f)) }
            clipToOutline = true
        }
        fill = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(color(R.color.accent2), color(R.color.accent))).apply { cornerRadius = dpf(999) }
            elevation = dpf(2)
        }
        bar.addView(fill, FrameLayout.LayoutParams(0, -1))
        centre.addView(bar, LinearLayout.LayoutParams(dp(210), dp(12)).apply { topMargin = dp(36) })

        foot = TextView(context).apply {
            text = "LOADING YOUR FRAMES"
            textSize = 11f
            letterSpacing = 0.14f
            setTextColor(color(R.color.mute))
            typeface = ResourcesCompat.getFont(context, R.font.poppins_regular)
            alpha = 0f
        }
        centre.addView(foot, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
    }

    private fun startAnimations() {
        if (reduced) {
            logo.alpha = 1f; logo.scaleX = 1f; logo.scaleY = 1f; logo.rotation = 0f
            letters.forEach { it.alpha = 1f; it.translationY = 0f }
            sig.alpha = 1f; tag.alpha = 1f; foot.alpha = 1f
            fill.layoutParams = FrameLayout.LayoutParams(dp(210), -1)
            return
        }
        loop(glow, 2400L, 0L) {
            scaleX = 0.9f + 0.18f * it; scaleY = 0.9f + 0.18f * it
            alpha = 0.6f + 0.4f * it
        }
        ringLoop()
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
            .setStartDelay(200L).setDuration(1100L).setInterpolator(SPRING).start()
        letters.forEachIndexed { i, t ->
            t.animate().alpha(1f).translationY(0f).setStartDelay(1050L + i * 80L).setDuration(620L)
                .setInterpolator(SPRING).start()
        }
        sig.animate().alpha(1f).translationY(0f).setStartDelay(1900L).setDuration(700L).setInterpolator(EASE).start()
        tag.animate().alpha(1f).translationY(0f).setStartDelay(2350L).setDuration(700L).setInterpolator(EASE).start()
        bar.post {
            ValueAnimator.ofInt(0, dp(210)).apply {
                startDelay = 350L
                duration = 2800L
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener { a ->
                    fill.layoutParams = FrameLayout.LayoutParams(a.animatedValue as Int, -1)
                    fill.requestLayout()
                }
                start()
                animators.add(this)
            }
        }
        foot.animate().alpha(1f).translationY(0f).setStartDelay(2550L).setDuration(650L).setInterpolator(EASE).start()

        if (Build.VERSION.SDK_INT >= 31) {
            letters.forEachIndexed { i, t ->
                val b = ValueAnimator.ofFloat(dpf(6), 0f).apply {
                    startDelay = 1050L + i * 80L
                    duration = 620L
                    interpolator = SPRING
                    addUpdateListener { a -> t.setRenderEffect(RenderEffect.createBlurEffect(a.animatedValue as Float, a.animatedValue as Float, Shader.TileMode.CLAMP)) }
                    start()
                }
                animators.add(b)
            }
        }
    }

    private fun ringLoop() {
        val a = ObjectAnimator.ofPropertyValuesHolder(ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1.22f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1.22f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f)).apply {
            duration = 2600L
            startDelay = 1100L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
            start()
        }
        animators.add(a)
    }

    private fun loop(v: View, half: Long, delay: Long, update: View.(Float) -> Unit) {
        val a = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = half
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = EASE_IN_OUT
            addUpdateListener { v.update(it.animatedValue as Float) }
            start()
        }
        animators.add(a)
    }

    fun scheduleHandoff() {
        handler.postDelayed({ handoff() }, SPLASH_MS)
    }

    private fun handoff() {
        if (finished) return
        finished = true
        if (reduced) { removeFromHost(); return }
        val target = host.resources.getIdentifier("headerLogo", "id", host.packageName)
            .let { if (it != 0) host.findViewById<View>(it) else null }
        if (target != null && target.width > 0) {
            val from = IntArray(2); logo.getLocationOnScreen(from)
            val to = IntArray(2); target.getLocationOnScreen(to)
            val k = target.width.toFloat() / logo.width.toFloat()
            val dx = (to[0] + target.width / 2f) - (from[0] + logo.width / 2f)
            val dy = (to[1] + target.height / 2f) - (from[1] + logo.height / 2f)
            target.visibility = View.INVISIBLE
            logo.animate().translationX(dx).translationY(dy).scaleX(k).scaleY(k)
                .setDuration(900L).setInterpolator(PathInterpolator(0.7f, 0f, 0.2f, 1f)).start()
            centre.animate().alpha(0f).translationY(-dpf(14)).setDuration(520L)
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f)).start()
            handler.postDelayed({ target.visibility = View.VISIBLE }, 880L)
            animate().alpha(0f).setStartDelay(260L).setDuration(820L)
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f)).start()
            handler.postDelayed({ removeFromHost() }, 1120L)
        } else {
            animate().alpha(0f).setStartDelay(260L).setDuration(820L)
                .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f)).start()
            handler.postDelayed({ removeFromHost() }, 1120L)
        }
    }

    private fun removeFromHost() {
        animators.forEach { it.cancel() }
        animators.clear()
        handler.removeCallbacksAndMessages(null)
        (parent as? android.view.ViewGroup)?.removeView(this)
        onFinished?.invoke()
    }

    var onFinished: (() -> Unit)? = null

    private fun withAlpha(c: Int, a: Float): Int = Color.argb((255 * a).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    companion object {
        const val SPLASH_MS = 3500L
        private var shownThisProcess = false
        private val SPRING = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        private val EASE_IN_OUT = PathInterpolator(0.42f, 0f, 0.58f, 1f)

        fun show(activity: Activity): SplashOverlay? {
            if (shownThisProcess) return null
            val root = activity.findViewById<FrameLayout>(android.R.id.content) ?: return null
            val overlay = SplashOverlay(activity)
            shownThisProcess = true
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.scheduleHandoff()
            return overlay
        }
    }
}

/** Radial gradient with stops, optionally stretched to an ellipse (CSS radial-gradient). */
private class RadialDrawable(
    private val colors: IntArray,
    private val stops: FloatArray,
    private val cxFrac: Float,
    private val cyFrac: Float,
    private val rxFrac: Float,
    private val ryFrac: Float,
    private val ellipse: Boolean
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = b.left + cxFrac * w
        val cy = b.top + cyFrac * h
        val shader = RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)
        matrix.reset()
        if (ellipse) {
            matrix.setScale(rxFrac * w, ryFrac * h)
        } else {
            val r = rxFrac * w
            matrix.setScale(r, r)
        }
        matrix.postTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawRect(b, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}