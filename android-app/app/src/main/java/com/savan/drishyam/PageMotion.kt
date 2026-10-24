package com.savan.drishyam

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Consistent Material-inspired entrance motion. Respects the app's low-effects
 * preference and Android's system animator setting; content never stays hidden.
 */
fun animatePageEntrance(view: View) {
    if (lowFx(view.context) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled())) {
        view.alpha = 1f
        view.translationY = 0f
        return
    }
    view.animate().cancel()
    view.alpha = 0f
    view.translationY = 16f * view.resources.displayMetrics.density
    view.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(280L)
        .setInterpolator(DecelerateInterpolator())
        .start()
}
