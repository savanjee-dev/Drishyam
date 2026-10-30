package com.savan.drishyam

import android.app.Activity
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.animation.ValueAnimator
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

    fun hasOpeningOrigin(activity: Activity): Boolean =
        activity.intent.hasExtra(EXTRA_X) && activity.intent.hasExtra(EXTRA_Y)

    fun enter(
        activity: Activity,
        enableDirectionalBackGesture: Boolean = true,
        animateOpening: Boolean = true
    ) {
        // Keep the portal window transparent so the opening reveal and the
        // independent Back closing animation can show the real Activity beneath it.
        activity.window.setWindowAnimations(0)
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        activity.window.decorView.background = ColorDrawable(Color.TRANSPARENT)
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content?.setBackgroundColor(Color.TRANSPARENT)

        if (enableDirectionalBackGesture) registerDirectionalBackGesture(activity)

        // Only animate pages launched through the portal. The launcher/Home instance
        // has no saved origin and must retain Android's native predictive-Back preview.
        if (!animateOpening || !hasOpeningOrigin(activity) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
            !motionEnabled(activity) || content == null) return

        // Hide the destination until its layout is measured so no un-clipped frame
        // can appear before the circular reveal begins.
        content.visibility = View.INVISIBLE
        content.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (content.width <= 0 || content.height <= 0) {
                content.visibility = View.VISIBLE
                return@post
            }

            val rootLocation = IntArray(2)
            content.getLocationOnScreen(rootLocation)
            val originScreenX = activity.intent.getIntExtra(EXTRA_X, rootLocation[0] + content.width / 2)
            val originScreenY = activity.intent.getIntExtra(EXTRA_Y, rootLocation[1] + content.height / 2)
            val x = (originScreenX - rootLocation[0]).coerceIn(0, content.width)
            val y = (originScreenY - rootLocation[1]).coerceIn(0, content.height)
            val radius = maxRadius(content.width, content.height, x, y)

            content.visibility = View.VISIBLE
            runCatching {
                ViewAnimationUtils.createCircularReveal(content, x, y, 0f, radius).apply {
                    duration = 780L
                    interpolator = portalEasing
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: android.animation.Animator) {
                            content.visibility = View.VISIBLE
                        }
                    })
                    start()
                }
            }.onFailure {
                content.visibility = View.VISIBLE
            }
        }
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
        val density = activity.resources.displayMetrics.density
        val minCornerRadius = 4f * density
        val maxCornerRadius = 32f * density
        var currentCornerRadius = minCornerRadius
        content.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, currentCornerRadius)
            }
        }
        content.clipToOutline = true
        fun animateCornerRadius(targetRadius: Float, durationMs: Long) {
            ValueAnimator.ofFloat(currentCornerRadius, targetRadius).apply {
                duration = durationMs
                interpolator = PathInterpolator(0.10f, 0.84f, 0.16f, 1f)
                addUpdateListener { animator ->
                    currentCornerRadius = animator.animatedValue as Float
                    content.invalidateOutline()
                }
                start()
            }
        }
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
                val progress = backEvent.progress.coerceIn(0f, 1f)
                currentCornerRadius = minCornerRadius + (maxCornerRadius - minCornerRadius) * progress
                content.invalidateOutline()
                content.translationX = startTranslation + direction * content.width * progress
            }

            override fun onBackCancelled() {
                if (activity.isFinishing || activity.isDestroyed) return
                content.animate().cancel()
                animateCornerRadius(minCornerRadius, 700L)
                ObjectAnimator.ofFloat(content, View.TRANSLATION_X, content.translationX, startTranslation).apply {
                    duration = 700L
                    interpolator = PathInterpolator(0.10f, 0.84f, 0.16f, 1f)
                    start()
                }
            }

            override fun onBackInvoked() {
                if (activity.isFinishing || activity.isDestroyed) return
                content.animate().cancel()
                animateCornerRadius(maxCornerRadius, 450L)
                val targetX = startTranslation + direction * content.width.toFloat()
                ObjectAnimator.ofFloat(content, View.TRANSLATION_X, content.translationX, targetX).apply {
                    duration = 450L
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
                // Faster close makes the page collapse into the Back button promptly.
                duration = 700L
                interpolator = portalEasing
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var finished = false
                    private fun finishOnce() {
                        if (!finished) {
                            finished = true
                            // ViewAnimationUtils removes its reveal clip when the animator ends.
                            // Hide the outgoing content before finishing so Android cannot draw
                            // one full, un-clipped frame between the collapsed portal and finish().
                            // Keep the translucent window/decor backgrounds untouched.
                            content.visibility = View.INVISIBLE
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
