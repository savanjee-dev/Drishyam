package com.savan.drishyam

import android.app.Activity
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

/**
 * Circular Portal page transitions. Touch coordinates are captured in screen space and
 * converted to the destination content's local coordinates after layout.
 */
object PortalTransition {
    private var lastTouch: Pair<Int, Int>? = null

    private const val EXTRA_X = "drishyam.portal.origin_x"
    private const val EXTRA_Y = "drishyam.portal.origin_y"
    private val portalEasing = PathInterpolator(0.20f, 0.78f, 0.20f, 1f)

    fun recordTouch(view: View, rawX: Float, rawY: Float) {
        lastTouch = rawX.toInt() to rawY.toInt()
    }

    fun launch(activity: Activity, source: View, intent: Intent) {
        val location = IntArray(2)
        source.getLocationOnScreen(location)
        val touched = lastTouch.also { lastTouch = null }
        val x = touched?.first?.takeIf { it >= 0 } ?: (location[0] + source.width / 2)
        val y = touched?.second?.takeIf { it >= 0 } ?: (location[1] + source.height / 2)
        intent.putExtra(EXTRA_X, x)
        intent.putExtra(EXTRA_Y, y)
        activity.window.setWindowAnimations(0)
        activity.startActivity(intent)
    }

    fun enter(activity: Activity) {
        // Page-opening animation is intentionally disabled for Phase S2.
        // Keep the portal window transparent so the independent Back closing
        // animation can reveal the previous Activity beneath this page.
        activity.window.setWindowAnimations(0)
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // A transparent window background alone is insufficient on some Android
        // builds: the decor/content host can retain the theme's opaque background.
        // Make both host layers transparent before moving/revealing the page so the
        // actual previous Activity is visible instead of a black sheet.
        activity.window.decorView.background = ColorDrawable(Color.TRANSPARENT)
        activity.findViewById<ViewGroup>(android.R.id.content)
            ?.setBackgroundColor(Color.TRANSPARENT)
        registerDirectionalBackGesture(activity)
    }

    /**
     * Keep system edge-back separate from the on-screen Back button.
     * On Android 14+, track predictive-back progress and slide the current page
     * toward the opposite edge, exposing the translucent previous Activity.
     * Cancellation returns the page to its exact starting position.
     */
    private fun registerDirectionalBackGesture(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        var direction = 1f
        var startTranslation = 0f
        val callback = object : android.window.OnBackAnimationCallback {
            override fun onBackStarted(backEvent: android.window.BackEvent) {
                // Android supports edge-back from either side. Move the page away
                // from the edge that received the gesture instead of silently
                // discarding right-edge gestures.
                direction = if (backEvent.swipeEdge == android.window.BackEvent.EDGE_RIGHT) -1f else 1f
                startTranslation = content.translationX
            }

            override fun onBackProgressed(backEvent: android.window.BackEvent) {
                if (activity.isFinishing || activity.isDestroyed || content.width <= 0) return
                val progress = backEvent.progress.coerceIn(0f, 1f) * 0.72f
                content.translationX = startTranslation + direction * content.width * progress
            }

            override fun onBackCancelled() {
                if (activity.isFinishing || activity.isDestroyed) return
                content.animate().cancel()
                ObjectAnimator.ofFloat(content, View.TRANSLATION_X, content.translationX, startTranslation).apply {
                    duration = 900L
                    interpolator = PathInterpolator(0.10f, 0.84f, 0.16f, 1f)
                    start()
                }
            }

            override fun onBackInvoked() {
                if (activity.isFinishing || activity.isDestroyed) return
                content.animate().cancel()
                val targetX = startTranslation + direction * content.width.toFloat()
                ObjectAnimator.ofFloat(content, View.TRANSLATION_X, content.translationX, targetX).apply {
                    duration = 850L
                    interpolator = PathInterpolator(0.10f, 0.84f, 0.16f, 1f)
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        private var cancelled = false
                        override fun onAnimationCancel(animation: android.animation.Animator) {
                            cancelled = true
                        }
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (cancelled || activity.isFinishing || activity.isDestroyed) return
                            activity.finish()
                            activity.overridePendingTransition(0, 0)
                        }
                    })
                    start()
                }
            }
        }
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
    }

    fun close(activity: Activity, backControl: View?) {
        if (activity.isFinishing) return
        // The shrinking content must reveal the still-visible Activity beneath it,
        // never a dimmed or opaque window surface.
        activity.window.setWindowAnimations(0)
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !motionEnabled(activity)) {
            activity.finish()
            return
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content == null || content.width <= 0 || content.height <= 0) {
            activity.finish()
            return
        }
        // Stop any in-flight child view property animations before starting the
        // close reveal. Otherwise staggered fades/translations can redraw over the
        // shrinking portal and look like a flash or flicker.
        fun cancelAnimations(view: View) {
            view.animate().cancel()
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) cancelAnimations(view.getChildAt(index))
            }
        }
        cancelAnimations(content)
        val rootLocation = IntArray(2)
        content.getLocationOnScreen(rootLocation)
        // A tap on the visible Back control makes the whole page collapse into
        // that control. Android/system Back, which has no view, reverses to the
        // original opening point when available.
        val hasOpeningOrigin = activity.intent.hasExtra(EXTRA_X) && activity.intent.hasExtra(EXTRA_Y)
        val sourceLocation = IntArray(2)
        if (backControl != null) backControl.getLocationOnScreen(sourceLocation)
        val originScreenX = if (backControl != null) sourceLocation[0] + backControl.width / 2
            else if (hasOpeningOrigin) activity.intent.getIntExtra(EXTRA_X, 0)
            else rootLocation[0] + content.width / 2
        val originScreenY = if (backControl != null) sourceLocation[1] + backControl.height / 2
            else if (hasOpeningOrigin) activity.intent.getIntExtra(EXTRA_Y, 0)
            else rootLocation[1] + content.height / 2
        val x = (originScreenX - rootLocation[0]).coerceIn(0, content.width)
        val y = (originScreenY - rootLocation[1]).coerceIn(0, content.height)
        val radius = maxRadius(content.width, content.height, x, y)
        runCatching {
            ViewAnimationUtils.createCircularReveal(content, x, y, radius, 0f).apply {
                // Slower close keeps the previous page visible as the portal collapses.
                duration = 900L
                interpolator = portalEasing
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var finished = false
                    private fun finishOnce() {
                        if (!finished) {
                            finished = true
                            // Do not mutate the decor background on the final frame:
                            // that creates a one-frame surface flash on some devices.
                            // The Portal theme already keeps the window transparent.
                            activity.finish()
                            activity.overridePendingTransition(0, 0)
                        }
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) = finishOnce()
                    override fun onAnimationCancel(animation: android.animation.Animator) = finishOnce()
                })
                start()
            }
        }.onFailure {
            activity.finish()
            activity.overridePendingTransition(0, 0)
        }
    }

    private fun maxRadius(width: Int, height: Int, x: Int, y: Int): Float {
        val farX = maxOf(x, width - x)
        val farY = maxOf(y, height - y)
        return hypot(farX.toDouble(), farY.toDouble()).toFloat().coerceAtLeast(1f)
    }
}
