package com.savan.drishyam

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat

/**
 * Full-screen Search page (Phase H2). Opened from the search bar and the Search tab on Home.
 * Typing filters the category list live. Tapping a category returns it to Home, which shows that category's photos.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        MainActivity.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(color(R.color.bg)))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply { setBackgroundColor(color(R.color.bg)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(40))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        // Back button: plain 48 dp circle
        content.addView(TextView(this).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
            setTextColor(color(R.color.fg))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color(R.color.glassFill))
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            setOnClickListener { finish() }
        })

        content.addView(tv("Explore categories", 11f, R.color.accent, R.font.poppins_semibold, 22, 0).apply { letterSpacing = 0.16f })
        content.addView(tv("Search", 38f, R.color.fg, R.font.poppins_extrabold, 6, 4).apply { letterSpacing = -0.02f })

        // Search pill (54 dp, glass). Typing filters the list live.
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.glass_pill)
            setPadding(dp(18), 0, dp(18), 0)
        }
        pill.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = android.content.res.ColorStateList.valueOf(color(R.color.mute))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        input = EditText(this).apply {
            hint = "Search forest, hiking, night sky"
            setHintTextColor(color(R.color.mute))
            setTextColor(color(R.color.fg))
            typeface = ResourcesCompat.getFont(this@SearchActivity, R.font.poppins_medium)
            textSize = 14f
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(10), 0, 0, 0)
        }
        pill.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        content.addView(pill, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(14) })

        content.addView(tv("Categories", 11f, R.color.accent, R.font.poppins_semibold, 28, 12).apply {
            letterSpacing = 0.14f
            setPadding(dp(4), dp(28), 0, dp(12))
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, LinearLayout.LayoutParams(-1, -2))

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) {}
        })
        input.setOnEditorActionListener { _, _, _ -> filter(input.text.toString()); true }

        filter("")
        input.postDelayed({
            input.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 250L)
    }

    /** Shows the categories whose name or search words match the text. Empty text shows all of them. */
    private fun filter(text: String) {
        val q = text.trim()
        list.removeAllViews()
        val matches = MainActivity.PHOTO_CATEGORIES.filter {
            q.isEmpty() || it.first.contains(q, ignoreCase = true) || it.second.contains(q, ignoreCase = true)
        }
        if (matches.isEmpty()) {
            list.addView(tv("No categories match. Try another word.", 14f, R.color.mute, R.font.poppins_regular, 8, 0))
            return
        }
        matches.forEachIndexed { index, (name, _) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.glass_row)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    setResult(Activity.RESULT_OK, Intent().putExtra("category", name))
                    finish()
                }
            }
            row.addView(tv(name, 15f, R.color.fg, R.font.poppins_semibold), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tv("›", 20f, R.color.mute, R.font.poppins_regular))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            if (!lowFx(this)) {
                row.alpha = 0f
                row.translationY = dpf(12)
                row.animate().alpha(1f).translationY(0f).setStartDelay(index * 30L).setDuration(260L)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun tv(value: String, size: Float, colorRes: Int, fontRes: Int, top: Int = 0, bottom: Int = 0): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color(colorRes))
            typeface = ResourcesCompat.getFont(this@SearchActivity, fontRes)
            setPadding(0, dp(top), 0, dp(bottom))
        }

    private fun color(id: Int): Int = ContextCompat.getColor(this, id)
    private fun dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density).toInt()
    private fun dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density
}
