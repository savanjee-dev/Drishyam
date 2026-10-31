package com.savan.drishyam

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView

/** Real extruded 3D Drishyam mark, rendered locally with Three.js/WebGL. */
class DrishyamLoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val reducedMotion = context
        .getSharedPreferences("drishyam_settings", Context.MODE_PRIVATE)
        .getString("motion", "standard") == "reduced"

    private val nightMode =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val fallback = ImageView(context).apply {
        setImageResource(R.drawable.drishyam_logo)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var webView: WebView? = null
    private var pageStarted = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        addView(fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ensureWebView()
    }

    private fun ensureWebView() {
        if (webView != null) return
        val view = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            setOnLongClickListener { true }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = true
                allowContentAccess = false
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(LoaderBridge(), "DrishyamLoaderBridge")
        }
        webView = view
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureWebView()
        val view = webView ?: return
        view.onResume()
        if (!pageStarted) {
            pageStarted = true
            val motion = if (reducedMotion) "reduced" else "standard"
            val theme = if (nightMode) "dark" else "light"
            view.loadUrl("file:///android_asset/drishyam-loader/index.html?motion=$motion&theme=$theme")
        } else {
            view.evaluateJavascript("window.Drishyam3DLoader && window.Drishyam3DLoader.resume()", null)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val view = webView ?: return
        if (visibility == View.VISIBLE) {
            view.onResume()
            view.evaluateJavascript("window.Drishyam3DLoader && window.Drishyam3DLoader.resume()", null)
        } else {
            view.evaluateJavascript("window.Drishyam3DLoader && window.Drishyam3DLoader.pause()", null)
            view.onPause()
        }
    }

    override fun onDetachedFromWindow() {
        val view = webView
        webView = null
        pageStarted = false
        if (view != null) {
            view.stopLoading()
            view.onPause()
            view.removeJavascriptInterface("DrishyamLoaderBridge")
            removeView(view)
            view.destroy()
        }
        super.onDetachedFromWindow()
    }

    private inner class LoaderBridge {
        @JavascriptInterface
        fun onReady() {
            post {
                if (fallback.visibility == View.VISIBLE) {
                    fallback.animate().alpha(0f).setDuration(180L)
                        .withEndAction { fallback.visibility = View.GONE }.start()
                }
            }
        }
    }
}