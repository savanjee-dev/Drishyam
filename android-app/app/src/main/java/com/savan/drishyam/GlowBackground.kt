package com.savan.drishyam

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight atmospheric canvas for the home feed.
 *
 * Gradients and texture points are prepared when the view size or atmosphere changes.
 * The animation only changes a few paint alpha values; it does not create bitmaps,
 * blur layers, particles, or shaders on every frame.
 */
class GlowBackground @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Palette(
        val base: Int,
        val colours: IntArray
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var phase = 0f
    private var atmosphere = "For You"
    private var palette = paletteFor(atmosphere)
    private var baseShader: LinearGradient? = null
    private var radialShaders: Array<RadialGradient> = emptyArray()
    private var texturePoints = FloatArray(0)

    private val animator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
        duration = 22000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setAtmosphere(category: String) {
        if (atmosphere.equals(category, ignoreCase = true)) return
        atmosphere = category
        palette = paletteFor(category)
        rebuildArtwork()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!lowFx(context) && !animator.isRunning) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildArtwork()
    }

    private fun rebuildArtwork() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val colours = palette.colours
        baseShader = LinearGradient(
            0f, 0f, w * 0.9f, h,
            intArrayOf(palette.base, colours[0], palette.base),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        val radius = w * 0.78f
        radialShaders = arrayOf(
            RadialGradient(w * 0.18f, h * 0.20f, radius, colours[0], transparent(colours[0]), Shader.TileMode.CLAMP),
            RadialGradient(w * 0.86f, h * 0.56f, radius, colours[1], transparent(colours[1]), Shader.TileMode.CLAMP),
            RadialGradient(w * 0.38f, h * 0.92f, radius, colours[2], transparent(colours[2]), Shader.TileMode.CLAMP)
        )

        // Deterministic, very sparse grain: generated once, then reused on every draw.
        val count = ((w * h) / 18000f).toInt().coerceIn(36, 150)
        texturePoints = FloatArray(count * 2)
        var seed = 0x51A7L
        for (i in 0 until count) {
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            texturePoints[i * 2] = (seed.toFloat() / Int.MAX_VALUE) * w
            seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
            texturePoints[i * 2 + 1] = (seed.toFloat() / Int.MAX_VALUE) * h
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        paint.shader = baseShader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        if (radialShaders.isNotEmpty()) {
            val wave = (sin(phase) + 1f) * 0.5f
            val alphas = intArrayOf(
                (34 + wave * 18f).toInt(),
                (25 + (1f - wave) * 17f).toInt(),
                (18 + ((sin(phase * 0.63f) + 1f) * 8f)).toInt()
            )
            for (i in radialShaders.indices) {
                paint.shader = radialShaders[i]
                paint.alpha = alphas[i]
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }

        paint.shader = null
        paint.alpha = 255
        texturePaint.color = 0x16FFFFFF
        if (texturePoints.isNotEmpty()) canvas.drawPoints(texturePoints, texturePaint)
    }

    private fun transparent(colour: Int): Int = colour and 0x00FFFFFF

    private fun paletteFor(category: String): Palette {
        val key = category.lowercase()
        return when {
            key in setOf("architecture", "travel", "street", "india") ->
                Palette(0xFF17130F.toInt(), intArrayOf(0xFF9B6844.toInt(), 0xFF72513B.toInt(), 0xFFC19A68.toInt()))
            key in setOf("night sky", "abstract") ->
                Palette(0xFF090D18.toInt(), intArrayOf(0xFF344A75.toInt(), 0xFF34436B.toInt(), 0xFF6A477E.toInt()))
            key in setOf("ocean") ->
                Palette(0xFF0C1718.toInt(), intArrayOf(0xFF315D5A.toInt(), 0xFF244A58.toInt(), 0xFF55766B.toInt()))
            key in setOf("forest", "nature", "earth", "hiking", "mountains", "wildlife") ->
                Palette(0xFF101713.toInt(), intArrayOf(0xFF3D6650.toInt(), 0xFF294B3D.toInt(), 0xFF75866A.toInt()))
            else ->
                Palette(0xFF111612.toInt(), intArrayOf(0xFF45604B.toInt(), 0xFF4D5140.toInt(), 0xFF7C8065.toInt()))
        }
    }
}
