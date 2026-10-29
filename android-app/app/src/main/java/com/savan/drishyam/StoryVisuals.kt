package com.savan.drishyam

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.random.Random

/**
 * Lightweight light-mode-only ambient layer for Our Story.
 * Gradient shapes are static drawables; only their transforms are animated by the render thread.
 */
class StoryAmbientLayer(context: Context) : FrameLayout(context) {
    private val animators = ArrayList<Animator>()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(true)

        val specs = listOf(
            Triple(0x3389945A, Gravity.TOP or Gravity.START, 340),
            Triple(0x2EB7A879, Gravity.CENTER or Gravity.END, 300),
            Triple(0x24D5C9A5, Gravity.BOTTOM or Gravity.START, 360)
        )
        specs.forEachIndexed { index, spec ->
            val color = spec.first.toInt()
            val orb = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = spec.third * resources.displayMetrics.density
                    colors = intArrayOf(color, Color.TRANSPARENT)
                }
                alpha = 0.9f
                isClickable = false
            }
            val size = (spec.third * resources.displayMetrics.density).toInt()
            addView(orb, LayoutParams(size, size, spec.second))
            val dx = if (index == 1) -42f else 48f
            val dy = if (index == 0) 36f else -30f
            val set = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(orb, View.TRANSLATION_X, 0f, dx * resources.displayMetrics.density),
                    ObjectAnimator.ofFloat(orb, View.TRANSLATION_Y, 0f, dy * resources.displayMetrics.density),
                    ObjectAnimator.ofFloat(orb, View.SCALE_X, 0.96f, 1.06f),
                    ObjectAnimator.ofFloat(orb, View.SCALE_Y, 0.96f, 1.06f)
                )
                duration = 11500L + index * 1700L
                interpolator = AccelerateDecelerateInterpolator()
            }
            animators.add(set)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE && motionEnabled(context) && visualEffectsEnabled(context)) {
            animators.forEachIndexed { index, animator ->
                animator.startDelay = index * 900L
                animator.start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animators.forEach { it.cancel() }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this && visibility != VISIBLE) animators.forEach { it.cancel() }
        else if (changedView === this && visibility == VISIBLE && isAttachedToWindow) {
            animators.forEachIndexed { index, animator ->
                if (!animator.isStarted) {
                    animator.startDelay = index * 900L
                    animator.start()
                }
            }
        }
    }
}

/** A subtle, fixed grain texture. A tiny tile is generated once and GPU-tiled while drawing. */
class StoryGrainOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 24 }
    private val tile: Bitmap
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val size = 64
        tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = Random(73421)
        for (i in pixels.indices) {
            val a = random.nextInt(8, 30)
            pixels[i] = (a shl 24) or 0x00FFFFFF
        }
        tile.setPixels(pixels, 0, size, 0, 0, size, size)
        paint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
