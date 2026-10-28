package com.savan.drishyam

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ScrollView

/**
 * Pull-stretch at the top and at the bottom of a ScrollView.
 * Copied from the Settings prototype (bindStretch, animation 10):
 *   - Pull at the top (finger moves down) or at the bottom (finger moves up), only when the page is at that edge.
 *   - Pull distance = finger distance x 0.38, capped at 120 dp.
 *   - The content moves with the pull (translationY). Top pulls down; bottom pulls up.
 *   - On release the content springs back to 0 over 560 ms, curve cubic-bezier(0.34, 1.56, 0.64, 1).
 *   - Reduced motion: the release takes 150 ms (prototype dur() rule).
 *
 * The scroll view's content must be its only direct child (the Settings hub and Appearance pages already are).
 */
object PageStretch {

    private const val RATIO = 0.38f
    private const val MAX_DP = 120f
    private const val RELEASE_MS = 560L
    private const val REDUCED_RELEASE_MS = 150L
    private val SPRING = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)

    /**
     * Attaches the stretch to [scroll].
     * @param density the screen density (dp to px).
     * @param reduced true when the app's Motion setting is Reduced.
     */
    fun attach(scroll: ScrollView, density: Float, reduced: Boolean) {
        // Turn off the built-in overscroll glow so only this stretch is shown.
        scroll.overScrollMode = View.OVER_SCROLL_NEVER

        var startY = 0f
        var candidate = 0      // -1 = top (pull down), +1 = bottom (pull up), 0 = none
        var stretching = false
        var offset = 0f       // current translation of the content, px

        scroll.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.y
                    stretching = false
                    candidate = when {
                        !scroll.canScrollVertically(-1) -> -1   // at the top
                        !scroll.canScrollVertically(1) -> 1      // at the bottom
                        else -> 0
                    }
                    false   // let the scroll view see the touch down
                }

                MotionEvent.ACTION_MOVE -> {
                    if (candidate == 0) return@setOnTouchListener false
                    val dy = e.y - startY
                    // Top: only a pull down (dy > 0) stretches. Bottom: only a pull up (dy < 0) stretches.
                    val pullingOut = (candidate == -1 && dy > 0) || (candidate == 1 && dy < 0)
                    if (!pullingOut) {
                        if (stretching) { offset = 0f; scroll.getChildAt(0).translationY = 0f }
                        stretching = false
                        return@setOnTouchListener false
                    }
                    // Still at the edge? If the page has moved away from the edge, hand the gesture back.
                    val stillAtEdge = if (candidate == -1) !scroll.canScrollVertically(-1) else !scroll.canScrollVertically(1)
                    if (!stillAtEdge) { candidate = 0; return@setOnTouchListener false }

                    stretching = true
                    val pull = minOf(Math.abs(dy) * RATIO, MAX_DP * density)
                    offset = if (candidate == -1) pull else -pull   // top: down (positive); bottom: up (negative)
                    scroll.getChildAt(0).translationY = offset
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!stretching) {
                        candidate = 0
                        return@setOnTouchListener false
                    }
                    val from = offset
                    stretching = false
                    candidate = 0
                    offset = 0f
                    springBack(scroll.getChildAt(0), from, reduced)
                    true
                }

                else -> false
            }
        }
    }

    /** Moves the content from its pulled position back to 0 (prototype: keyframes translateY(from) to 0). */
    private fun springBack(content: View, from: Float, reduced: Boolean) {
        ValueAnimator.ofFloat(from, 0f).apply {
            duration = if (reduced) REDUCED_RELEASE_MS else RELEASE_MS
            interpolator = SPRING
            addUpdateListener { a -> content.translationY = a.animatedValue as Float }
            start()
        }
    }
}
