package com.savan.drishyam

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/**
 * Stretchy segmented indicator. Copied from the Settings prototype (moveInd, animation 5):
 *   - Duration 340 ms.
 *   - Overall curve cubic-bezier(0.34, 1.56, 0.64, 1).
 *   - Keyframes: start at the old option; at 45% stretch from the old option to the new one,
 *     with 8 dp extra on each side; end at the new option.
 *   - Reduced motion: move at once, no stretch.
 *
 * Use it on any segmented control whose indicator is a View inside a FrameLayout
 * (the indicator's top and bottom margins are 4 dp, as in the prototype's .seg .ind).
 */
object SegmentStretch {

    private const val DURATION_MS = 340L
    private const val STRETCH_AT = 0.45f
    private const val OVERSHOOT_DP = 8f
    private val CURVE = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)

    /** Places the indicator at once (used on screen open, with no animation). */
    fun place(indicator: View, left: Float, width: Float) {
        val lp = indicator.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = left.toInt()
        lp.width = width.toInt()
        indicator.layoutParams = lp
    }

    /**
     * Moves the indicator from where it is now to the selected option.
     * @param to left and width of the selected option, in pixels, relative to the segment.
     * @param density the screen density (for the 8 dp overshoot).
     * @param reduced true when the app's Motion setting is Reduced.
     */
    fun animate(indicator: View, to: Pair<Float, Float>, density: Float, reduced: Boolean) {
        val lp = indicator.layoutParams as FrameLayout.LayoutParams
        val fromL = lp.leftMargin.toFloat()
        val fromW = lp.width.toFloat()
        val toL = to.first
        val toW = to.second

        if (reduced) {
            place(indicator, toL, toW)
            return
        }

        val pad = OVERSHOOT_DP * density
        val stretchL = minOf(fromL, toL) - pad
        val stretchR = maxOf(fromL + fromW, toL + toW) + pad
        val stretchW = stretchR - stretchL

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = CURVE
            addUpdateListener { a ->
                val p = a.animatedFraction
                val l: Float
                val w: Float
                if (p < STRETCH_AT) {
                    val k = p / STRETCH_AT
                    l = fromL + (stretchL - fromL) * k
                    w = fromW + (stretchW - fromW) * k
                } else {
                    val k = (p - STRETCH_AT) / (1f - STRETCH_AT)
                    l = stretchL + (toL - stretchL) * k
                    w = stretchW + (toW - stretchW) * k
                }
                place(indicator, l, w)
            }
            start()
        }
    }
}