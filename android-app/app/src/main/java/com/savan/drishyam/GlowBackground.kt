package com.savan.drishyam

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** Three soft colour orbs that drift slowly behind the content. Static in low-effects mode. */
class GlowBackground @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0x508BA08B.toInt(), 0x3DD8C6A1.toInt(), 0x354F725D.toInt())
    private var phase = 0f

    private val anim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 16000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!lowFx(context) && !anim.isRunning) anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val radius = w * 0.6f
        for (i in colors.indices) {
            val a = phase + i * 2.1f
            val cx = w * (0.5f + 0.38f * cos(a).toFloat())
            val cy = h * (0.5f + 0.30f * sin(a * 0.8f + i).toFloat())
            paint.shader = RadialGradient(cx, cy, radius, colors[i], 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
