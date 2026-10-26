package com.savan.drishyam

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A glass panel. It captures whatever is drawn behind it (the [source] view), shrinks and
 * blurs that picture, boosts its colour, then draws it inside a rounded shape with a tint,
 * a light sheen and a lit rim. If capturing fails or is too slow, it falls back to a
 * static frosted look, so the app never breaks because of the effect.
 */
class GlassLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val DOWN = 4f        // the backdrop is captured at 1/6 size
        private const val PAD = 10         // extra border so blurred edges stay clean
    }

    private var dirty = true

    var source: View? = null
        set(v) { field = v; dirty = true; invalidate() }
    var tickMs: Long = 70
    var radiusPx: Float = 100 * Resources.getSystem().displayMetrics.density
        set(v) { field = v; invalidate() }
    var dark: Boolean = false
        set(v) { field = v; invalidate() }
    var solid: Boolean = false
        set(v) { field = v; invalidate() }

    private val density = Resources.getSystem().displayMetrics.density
    private val tintColor = ContextCompat.getColor(context, R.color.glassTint)
    private val fillColor = ContextCompat.getColor(context, R.color.glassFill)
    private val rimStart = ContextCompat.getColor(context, R.color.glassRimStart)
    private val rimEnd = ContextCompat.getColor(context, R.color.glassRimEnd)
    private val bgColor = ContextCompat.getColor(context, R.color.bg)
    private val glassLevel = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        .getString("glass_level", "full") ?: "full"
    private val lowEffects = lowFx(context) || glassLevel == "off"
    // Backdrop is downsampled 4x: these radii approximate the prototype's 6dp/18dp levels.
    private val blurRadius = when (glassLevel) { "subtle" -> 2; "full" -> 4; else -> 0 }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }
    private val rect = RectF()
    private val rimRect = RectF()
    private val shaderMatrix = Matrix()
    private val satFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.22f) })

    private var shader: BitmapShader? = null
    private var bmp: Bitmap? = null
    private var pix: IntArray? = null
    private var tmp: IntArray? = null
    private var lastCapture = 0L
    private var failures = 0
    private var slowStreak = 0
    private var captures = 0
    private var disabled = false
    private val srcLoc = IntArray(2)
    private val myLoc = IntArray(2)

    init {
        setWillNotDraw(false)
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.GlassLayout)
            radiusPx = a.getDimension(R.styleable.GlassLayout_glassRadius, radiusPx)
            dark = a.getBoolean(R.styleable.GlassLayout_glassDark, false)
            solid = a.getBoolean(R.styleable.GlassLayout_glassSolid, false)
            a.recycle()
        }
    }

    /** Ask for a fresh backdrop soon (used while the list scrolls). */
    fun poke() {
        if (SystemClock.uptimeMillis() - lastCapture >= 33) dirty = true
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dirty = true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        rimPaint.shader = LinearGradient(
            0f, 0f, fw, fh,
            intArrayOf(rimStart, rimEnd, rimEnd, ColorUtils.setAlphaComponent(rimStart, 0x66)),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        sheenPaint.shader = LinearGradient(
            0f, 0f, 0f, fh * 0.6f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        dirty = true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = min(radiusPx, min(w, h) / 2f)
        rect.set(0f, 0f, w, h)

        val live = !solid && !disabled && !lowEffects && source != null
        if (live) {
            val now = SystemClock.uptimeMillis()
            if (dirty || now - lastCapture >= max(30L, tickMs - 10)) {
                capture()
                lastCapture = now
                dirty = false
            }
        }

        
val s = shader
if (solid) {
    tintPaint.color = ContextCompat.getColor(context, R.color.accent)
    tintPaint.shader = null
    bodyPaint.shader = null
    bodyPaint.colorFilter = null
} else if (live && s != null) {
    bodyPaint.shader = s
    bodyPaint.colorFilter = satFilter
    canvas.drawRoundRect(rect, r, r, bodyPaint)
    tintPaint.color = if (dark) 0x66000000 else tintColor
} else {
    tintPaint.color = if (dark) 0x80101018.toInt() else fillColor
}

        canvas.drawRoundRect(rect, r, r, tintPaint)

if (!solid) {
    canvas.drawRoundRect(rect, r, r, sheenPaint)

    val half = rimPaint.strokeWidth / 2f
    rimRect.set(half, half, w - half, h - half)
    val rr = max(0f, r - half)
    canvas.drawRoundRect(rimRect, rr, rr, rimPaint)
}
    

        if (live && tickMs > 0 && isShown) postInvalidateDelayed(tickMs)
    }

    private fun capture() {
        val src = source ?: return
        if (!src.isLaidOut || src.width == 0 || width == 0 || height == 0) return
        val t0 = System.nanoTime()
        try {
            val cw = ceil(width / DOWN).toInt() + PAD * 2
            val ch = ceil(height / DOWN).toInt() + PAD * 2
            var b = bmp
            if (b == null || b.width != cw || b.height != ch) {
                b = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
                bmp = b
                pix = IntArray(cw * ch)
                tmp = IntArray(cw * ch)
            }
            src.getLocationInWindow(srcLoc)
            getLocationInWindow(myLoc)
            val dx = (myLoc[0] - srcLoc[0]).toFloat()
            val dy = (myLoc[1] - srcLoc[1]).toFloat()

            val c = Canvas(b)
            c.drawColor(bgColor)
            c.save()
            c.scale(1f / DOWN, 1f / DOWN)
            c.translate(-dx + PAD * DOWN, -dy + PAD * DOWN)
            src.draw(c)
            c.restore()

            val p = pix!!
            val t = tmp!!
            b.getPixels(p, 0, cw, 0, 0, cw, ch)
            for (i in 0 until 1) {
                blurH(p, t, cw, ch, blurRadius)
                blurV(t, p, cw, ch, blurRadius)
            }
            b.setPixels(p, 0, cw, 0, 0, cw, ch)

            val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shaderMatrix.setScale(DOWN, DOWN)
            shaderMatrix.postTranslate(-PAD * DOWN, -PAD * DOWN)
            sh.setLocalMatrix(shaderMatrix)
            shader = sh
            failures = 0
        } catch (e: Exception) {
            failures++
            if (failures >= 3) disabled = true
        }

        // Adapt to slow phones: capture less often, then give up on live blur if still slow.
        val took = (System.nanoTime() - t0) / 1_000_000L
        captures++
        if (captures > 3 && took > 80) {
            disabled = true
        } else if (took > 20) {
            slowStreak++
            if (slowStreak >= 3) {
                tickMs = min(tickMs * 2, 700L)
                slowStreak = 0
            }
        } else {
            slowStreak = 0
        }
    }

    private fun blurH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                rs += (p shr 16) and 0xFF
                gs += (p shr 8) and 0xFF
                bs += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val pa = src[row + min(x + r + 1, w - 1)]
                val ps = src[row + max(x - r, 0)]
                rs += ((pa shr 16) and 0xFF) - ((ps shr 16) and 0xFF)
                gs += ((pa shr 8) and 0xFF) - ((ps shr 8) and 0xFF)
                bs += (pa and 0xFF) - (ps and 0xFF)
            }
        }
    }

    private fun blurV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = r * 2 + 1
        for (x in 0 until w) {
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                rs += (p shr 16) and 0xFF
                gs += (p shr 8) and 0xFF
                bs += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val pa = src[min(y + r + 1, h - 1) * w + x]
                val ps = src[max(y - r, 0) * w + x]
                rs += ((pa shr 16) and 0xFF) - ((ps shr 16) and 0xFF)
                gs += ((pa shr 8) and 0xFF) - ((ps shr 8) and 0xFF)
                bs += (pa and 0xFF) - (ps and 0xFF)
            }
        }
    }
}
