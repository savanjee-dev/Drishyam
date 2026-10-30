package com.savan.drishyam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.Choreographer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared Drishyam atmosphere. The animation phase is derived from monotonic uptime,
 * so a detached/recreated view resumes the same timeline instead of starting over.
 * Screens may layer their own foreground-specific reflections above this backdrop.
 */
class UniversalBackgroundView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), Choreographer.FrameCallback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var attached = false
    private var lastFrame = 0L

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    /** Re-evaluate the shared Glass Effects preference immediately after it changes. */
    fun refreshGlassLevel() {
        if (!attached) { invalidate(); return }
        Choreographer.getInstance().removeFrameCallback(this)
        if (motionEnabled(context) && levelFactor() > 0f) {
            Choreographer.getInstance().postFrameCallback(this)
        }
        invalidate()
    }

    private fun levelFactor(): Float = when (glassLevel(context)) {
        "off" -> 0f
        "subtle" -> 0.42f
        else -> 1f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (motionEnabled(context) && levelFactor() > 0f) {
            Choreographer.getInstance().removeFrameCallback(this)
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!attached) return
        val now = SystemClock.uptimeMillis()
        if (now != lastFrame) {
            lastFrame = now
            invalidate()
        }
        if (motionEnabled(context) && levelFactor() > 0f) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val factor = levelFactor()
        if (factor <= 0f || width <= 0 || height <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val elapsed = SystemClock.uptimeMillis().toDouble() / 1000.0
        val phase = if (motionEnabled(context)) elapsed else 0.0
        val subtle = factor
        val radius = maxOf(w, h) * 0.62f
        val colors = intArrayOf(
            0x5089945A,
            0x3DD0C79A,
            0x35414D2D
        )
        for (i in colors.indices) {
            val a = (phase * (0.22 + i * 0.035) + i * 2.1).toFloat()
            val cx = w * (0.5f + 0.36f * cos(a.toDouble()).toFloat())
            val cy = h * (0.48f + 0.28f * sin((a * 0.82f + i).toDouble()).toFloat())
            val base = colors[i]
            val alpha = (((base ushr 24) and 0xff) * subtle).toInt().coerceIn(0, 255)
            val color = (base and 0x00ffffff) or (alpha shl 24)
            paint.shader = RadialGradient(cx, cy, radius, color, 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, paint)
        }
        paint.shader = null

        // Sparse, low-contrast particles give the shared background the same depth
        // without competing with photography or foreground content.
        if (factor > 0.6f) {
            val alpha = (30f * factor).toInt()
            paint.color = (alpha shl 24) or 0x00D0C79A
            for (i in 0 until 18) {
                val seed = i * 1.618
                val x = ((0.5 + 0.43 * sin(seed + phase * 0.035)).toFloat() * w)
                val y = ((i / 18f) * h + 0.035f * h * sin(seed + phase * 0.06).toFloat()).coerceIn(0f, h)
                canvas.drawCircle(x, y, (1.1f + (i % 3) * 0.35f) * density, paint)
            }
        }
    }

    companion object {
        /** Adds the shared layer at the back of a root without changing its child layout. */
        fun attach(root: ViewGroup) {
            if (root.findViewWithTag<View>("drishyam-universal-background") != null) return
            val view = UniversalBackgroundView(root.context).apply {
                tag = "drishyam-universal-background"
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            root.addView(view, 0)
        }
    }
}
