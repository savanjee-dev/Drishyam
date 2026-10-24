package com.savan.drishyam

import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Applies a short, restrained page entrance. Keeps motion consistent without
 * spring overshoot and safely ignores views that are not attached yet.
 */
fun animatePageEntrance(view: View) {
    view.alpha = 0f
    view.translationY = 12f * view.resources.displayMetrics.density
    view.animate().cancel()
    view.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(220L)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
