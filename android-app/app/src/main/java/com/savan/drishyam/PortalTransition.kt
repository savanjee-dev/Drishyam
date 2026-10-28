package com.savan.drishyam

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.hypot

/**
 * Circular Portal page transitions. The launch origin is recorded in screen coordinates
 * and converted into the destination content's coordinate space after layout.
 */
object PortalTransition {
    private var lastTouch: Pair<Int, Int>? = null

    fun recordTouch(view: View, rawX: Float, rawY: Float) {
        lastTouch = rawX.toInt() to rawY.toInt()
    }
    private const val EXTRA_X = "drishyam.portal.origin_x"
    private const val EXTRA_Y = "drishyam.portal.origin_y"

    fun launch(activity: Activity, source: View, intent: Intent) {
        val location = IntArray(2)
        source.getLocationOnScreen(location)
        val touched = lastTouch.also { lastTouch = null }
        val x = touched?.first?.takeIf { it >= 0 } ?: (location[0] + source.width / 2)
        val y = touched?.second?.takeIf { it >= 0 } ?: (location[1] + source.height / 2)
        intent.putExtra(EXTRA_X, x)
        intent.putExtra(EXTRA_Y, y)
        if (motionEnabled(activity)) activity.window.setWindowAnimations(0)
        activity.startActivity(intent)
    }

    fun enter(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
            !motionEnabled(activity) || !activity.intent.hasExtra(EXTRA_X) ||
            !activity.intent.hasExtra(EXTRA_Y)) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        activity.window.setWindowAnimations(0)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        content.alpha = 0f
        content.post {
            if (activity.isFinishing || content.width <= 0 || content.height <= 0) return@post
            val rootLocation = IntArray(2)
            content.getLocationOnScreen(rootLocation)
            val cx = (activity.intent.getIntExtra(EXTRA_X, rootLocation[0] + content.width / 2) - rootLocation[0])
                .coerceIn(0, content.width)
            val cy = (activity.intent.getIntExtra(EXTRA_Y, rootLocation[1] + content.height / 2) - rootLocation[1])
                .coerceIn(0, content.height)
            val radius = maxRadius(content.width, content.height, cx, cy)
            content.alpha = 1f
            runCatching {
                ViewAnimationUtils.createCircularReveal(content, cx, cy, 0f, radius).apply {
                    duration = 420L
                    interpolator = AccelerateDecelerateInterpolator()
                }.start()
            }
        }
    }

    fun close(activity: Activity, backControl: View?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !motionEnabled(activity)) {
            activity.finish()
            return
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        activity.window.setWindowAnimations(0)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (content == null || content.width <= 0 || content.height <= 0) {
            activity.finish()
            return
        }
        val rootLocation = IntArray(2)
        content.getLocationOnScreen(rootLocation)
        val sourceLocation = IntArray(2)
        if (backControl != null) backControl.getLocationOnScreen(sourceLocation)
        val cx = if (backControl != null) sourceLocation[0] + backControl.width / 2 - rootLocation[0] else content.width / 2
        val cy = if (backControl != null) sourceLocation[1] + backControl.height / 2 - rootLocation[1] else 0
        val x = cx.coerceIn(0, content.width)
        val y = cy.coerceIn(0, content.height)
        val radius = maxRadius(content.width, content.height, x, y)
        runCatching {
            ViewAnimationUtils.createCircularReveal(content, x, y, radius, 0f).apply {
                duration = 300L
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        activity.finish()
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (!activity.isFinishing) activity.finish()
                    }
                })
                start()
            }
        }.onFailure { activity.finish() }
    }

    private fun maxRadius(width: Int, height: Int, x: Int, y: Int): Float {
        val farX = maxOf(x, width - x)
        val farY = maxOf(y, height - y)
        return hypot(farX.toDouble(), farY.toDouble()).toFloat().coerceAtLeast(1f)
    }
}
