package com.savan.drishyam

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.animation.DecelerateInterpolator
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
        // Keep the entering Activity window opaque. A transparent window here
        // exposes the previous (home) Activity outside the circular reveal and
        // can cause a visible home-screen flash. Transparency is applied only
        // when closing, where revealing the Activity underneath is intentional.
        activity.window.setWindowAnimations(0)
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        // Paint the decor with the app surface during entry so the translucent
        // portal window cannot expose the outgoing Activity around the reveal.
        val backgroundValue = TypedValue()
        val surfaceColor = if (activity.theme.resolveAttribute(android.R.attr.colorBackground, backgroundValue, true)) {
            if (backgroundValue.resourceId != 0) runCatching {
                androidx.core.content.ContextCompat.getColor(activity, backgroundValue.resourceId)
            }.getOrDefault(Color.BLACK) else backgroundValue.data
        } else Color.BLACK
        activity.window.decorView.background = ColorDrawable(surfaceColor)

        // Back is animated after recognition; animation progress is not tied to
        // the finger. Keep the home Activity's normal exit behaviour unchanged.
        if (activity !is MainActivity && activity is ComponentActivity) {
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = slideBack(activity)
            })
        }

        val intent = activity.intent
        if (!intent.hasExtra(EXTRA_X) || !intent.hasExtra(EXTRA_Y) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !motionEnabled(activity)
        ) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.post {
            if (activity.isFinishing || activity.isDestroyed || content.width <= 0 || content.height <= 0) return@post
            val location = IntArray(2)
            content.getLocationOnScreen(location)
            val x = (intent.getIntExtra(EXTRA_X, location[0] + content.width / 2) - location[0])
                .coerceIn(0, content.width)
            val y = (intent.getIntExtra(EXTRA_Y, location[1] + content.height / 2) - location[1])
                .coerceIn(0, content.height)
            val radius = maxRadius(content.width, content.height, x, y)
            runCatching {
                ViewAnimationUtils.createCircularReveal(content, x, y, 0f, radius).apply {
                    duration = 560L
                    interpolator = portalEasing
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                activity.window.decorView.background = ColorDrawable(Color.TRANSPARENT)
                            }
                        }
                    })
                    start()
                }
            }
        }
    }

    private fun slideBack(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content == null || content.width <= 0 || !motionEnabled(activity)) {
            activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            activity.finish()
            activity.overridePendingTransition(0, 0)
            return
        }
        activity.window.setWindowAnimations(0)
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // The decor was deliberately opaque during entry. It must become
        // transparent before the outgoing content moves, otherwise the
        // previous Activity cannot be previewed through the exposed area.
        activity.window.decorView.background = ColorDrawable(Color.TRANSPARENT)
        content.animate().cancel()
        content.translationX = 0f
        content.alpha = 1f
        content.animate()
            .translationX(content.width.toFloat())
            .alpha(1f)
            .setDuration(300L)
            .setInterpolator(PathInterpolator(0.18f, 0.72f, 0.22f, 1f))
            .withEndAction {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    content.visibility = View.INVISIBLE
                    activity.overridePendingTransition(0, 0)
                    activity.finish()
                    activity.overridePendingTransition(0, 0)
                }
            }
            .start()
    }

    fun close(activity: Activity, backControl: View?) {
        if (activity.isFinishing) return
        // The shrinking content must reveal the still-visible Activity beneath it,
        // never a dimmed or opaque window surface.
        activity.window.setWindowAnimations(0)
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Preserve the Back-button portal collapse: the real previous Activity
        // must show through as the page shrinks into the control.
        activity.window.decorView.background = ColorDrawable(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !motionEnabled(activity)) {
            activity.finish()
            return
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        if (content == null || content.width <= 0 || content.height <= 0) {
            activity.finish()
            return
        }
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
                duration = 620L
                interpolator = portalEasing
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var finished = false
                    private fun finishOnce() {
                        if (!finished) {
                            finished = true
                            // Drop the fully-revealed content before finishing so a
                            // final stale frame cannot flash over the Activity beneath.
                            content.visibility = View.INVISIBLE
                            activity.window.decorView.background = ColorDrawable(Color.TRANSPARENT)
                            activity.overridePendingTransition(0, 0)
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
