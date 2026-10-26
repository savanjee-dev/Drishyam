package com.savan.drishyam

import android.app.Activity
import android.animation.ValueAnimator
import android.os.Build
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

const val PREFS_SETTINGS = "drishyam_settings"

fun Int.dp(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

fun hapticsOn(c: Context): Boolean =
    c.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).getBoolean("haptics", true)

/** Persisted glass level; supports the legacy low_fx preference for existing installs. */
fun glassLevel(c: Context): String {
    val p = c.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
    return p.getString("glass_level", null)?.lowercase()
        ?: if (p.getBoolean("low_fx", false)) "off" else "full"
}

/** Off disables live blur; Subtle and Full retain live glass at different strengths. */
fun lowFx(c: Context): Boolean = glassLevel(c) == "off"

/** Single motion gate shared by page and component transitions. */
fun motionEnabled(c: Context): Boolean =
    !lowFx(c) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())

/** Crisp press feedback with no spring, bounce, or elastic overshoot. */
fun View.pressFx() {
    setOnTouchListener { v, e ->
        if (e.actionMasked == MotionEvent.ACTION_DOWN && hapticsOn(v.context)) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (!motionEnabled(v.context)) return@setOnTouchListener false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.84f).setDuration(105).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                if (hapticsOn(v.context)) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
                    .setInterpolator(DecelerateInterpolator()).start()
        }
        false // keep click handling intact
    }
}

fun ImageView.setHeart(liked: Boolean) {
    setImageResource(if (liked) R.drawable.ic_heart_fill else R.drawable.ic_heart_outline)
    imageTintList = ColorStateList.valueOf(if (liked) ContextCompat.getColor(context, R.color.accent2) else 0xFFFFFFFF.toInt())
}

/** Pill segment: gradient when selected, frosted glass when not. */
fun TextView.styleSeg(on: Boolean) {
    background = ContextCompat.getDrawable(context, if (on) R.drawable.accent_pill else R.drawable.glass_pill)
    val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val selectedColor = if (night) 0xFF0A0D08.toInt() else 0xFFF3F1E7.toInt()
    setTextColor(if (on) selectedColor
        else com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, ContextCompat.getColor(context, R.color.fg)))
}

/** Points every glass panel inside [root] at the view whose picture they should blur. */
fun bindGlass(root: View, source: View, tickMs: Long) {
    if (root is GlassLayout) {
        root.source = source
        root.tickMs = tickMs
    }
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) bindGlass(root.getChildAt(i), source, tickMs)
    }
}

// ---------- In-app glass pop-ups ----------

/** Remembers the open pop-up so the Back button can close it first. */
object PopupStack {
    private var closer: (() -> Unit)? = null
    fun set(c: (() -> Unit)?) { closer = c }
    fun dismiss(): Boolean {
        val c = closer ?: return false
        c()
        return true
    }
}

fun hideKeyboard(activity: Activity, v: View) {
    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(v.windowToken, 0)
}

/**
 * Shows a floating glass card over a dimmed screen. The card blurs [source] behind it,
 * keeps a margin from every screen edge, and enters with a short fade and controlled scale.
 * [build] fills the card; call the given function to close it.
 */
fun showGlassPopup(
    activity: Activity,
    source: View,
    topOffsetDp: Int = -1,
    build: (LinearLayout, () -> Unit) -> Unit
) {
    PopupStack.dismiss()
    val host = activity.findViewById<ViewGroup>(android.R.id.content)
    val scrim = FrameLayout(activity).apply {
        setBackgroundColor(0x66000000)
        isClickable = true
        alpha = 0f
    }
    val card = GlassLayout(activity).apply {
        radiusPx = 32.dp().toFloat()
        this.source = source
        tickMs = 90
        isClickable = true
    }
    val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    card.addView(box, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

    val top = topOffsetDp >= 0
    val lp = FrameLayout.LayoutParams(
        MATCH_PARENT, WRAP_CONTENT,
        if (top) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
    ).apply { setMargins(22.dp(), if (top) topOffsetDp.dp() else 0, 22.dp(), 0) }
    scrim.addView(card, lp)
    host.addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

    var closed = false
    val close: () -> Unit = {
        if (!closed) {
            closed = true
            PopupStack.set(null)
            hideKeyboard(activity, scrim)
            if (motionEnabled(activity)) {
                card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(150).start()
                scrim.animate().alpha(0f).setDuration(170)
                    .withEndAction { host.removeView(scrim) }.start()
            } else {
                host.removeView(scrim)
            }
        }
    }
    scrim.setOnClickListener { close() }
    PopupStack.set(close)
    build(box, close)

    if (motionEnabled(activity)) {
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        card.alpha = 0f
        scrim.animate().alpha(1f).setDuration(180).start()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190)
            .setInterpolator(DecelerateInterpolator()).start()
    } else {
        card.scaleX = 1f
        card.scaleY = 1f
        card.alpha = 1f
        scrim.alpha = 1f
    }
}

private fun popupTitle(a: Activity, text: String) = TextView(a).apply {
    this.text = text
    typeface = ResourcesCompat.getFont(a, R.font.montserrat_semibold)
    textSize = 28f
    setTextColor(ContextCompat.getColor(a, R.color.fg))
}

private fun popupButton(a: Activity, label: String, primary: Boolean, onClick: () -> Unit) = TextView(a).apply {
    text = label
    gravity = Gravity.CENTER
    textSize = 15f
    typeface = ResourcesCompat.getFont(a, R.font.poppins_medium) ?: Typeface.DEFAULT
    setTextColor(ContextCompat.getColor(a, if (primary) android.R.color.white else R.color.fg))
    background = ContextCompat.getDrawable(a, if (primary) R.drawable.accent_pill else R.drawable.glass_pill)
    isClickable = true
    isFocusable = true
    pressFx()
    setOnClickListener { onClick() }
}

fun messagePopup(activity: Activity, source: View, title: String, message: String) {
    showGlassPopup(activity, source) { box, close ->
        box.setPadding(24.dp(), 24.dp(), 24.dp(), 20.dp())
        box.addView(popupTitle(activity, title))
        box.addView(TextView(activity).apply {
            text = message
            textSize = 15f
            setLineSpacing(4.dp().toFloat(), 1f)
            setTextColor(ContextCompat.getColor(activity, R.color.fg))
            setPadding(0, 10.dp(), 0, 0)
        })
        box.addView(
            popupButton(activity, activity.getString(R.string.ok), true) { close() },
            LinearLayout.LayoutParams(MATCH_PARENT, 50.dp()).apply { topMargin = 20.dp() }
        )
    }
}

fun choicePopup(activity: Activity, source: View, title: String, options: List<String>, onPick: (Int) -> Unit) {
    showGlassPopup(activity, source) { box, close ->
        box.setPadding(24.dp(), 24.dp(), 24.dp(), 20.dp())
        box.addView(popupTitle(activity, title))
        options.forEachIndexed { i, label ->
            box.addView(
                popupButton(activity, label, false) { close(); onPick(i) },
                LinearLayout.LayoutParams(MATCH_PARENT, 52.dp()).apply { topMargin = if (i == 0) 16.dp() else 10.dp() }
            )
        }
        box.addView(
            popupButton(activity, activity.getString(R.string.cancel), false) { close() },
            LinearLayout.LayoutParams(MATCH_PARENT, 48.dp()).apply { topMargin = 14.dp() }
        )
    }
}

/** Native, non-elastic edge feedback for scroll containers. */
fun installScrollBounce(view: View) {
    // Use Android's native overscroll feedback; no custom bounce, spring, or elastic translation.
    view.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
}
