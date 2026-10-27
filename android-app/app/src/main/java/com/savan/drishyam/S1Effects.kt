package com.savan.drishyam

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

object S1Fx {
    val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
    val outCurve = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    val easeOut = PathInterpolator(0f, 0f, 0.58f, 1f)
    val easeInOut = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    val ease = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
}

fun addAmbientGlows(parent: FrameLayout, context: Context, animate: Boolean, lowFx: Boolean) {
    if (lowFx) return
    val d = context.resources.displayMetrics.density
    data class Blob(val color: Int, val alphaValue: Float, val duration: Long, val gravity: Int, val top: Int, val bottom: Int, val left: Int, val right: Int)
    val blobs = listOf(
        Blob(0xFF89945A.toInt(), .42f, 12000L, Gravity.TOP or Gravity.START, -70, 0, -90, 0),
        Blob(0xFFD0C79A.toInt(), .42f, 14000L, Gravity.TOP or Gravity.END, 240, 0, 0, -110),
        Blob(0xFF414D2D.toInt(), .22f, 16000L, Gravity.BOTTOM or Gravity.START, 0, 60, 0, 0)
    )
    blobs.forEach { b ->
        val v = object : View(context) {
            private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = b.color
                maskFilter = android.graphics.BlurMaskFilter(60f*d, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (android.os.Build.VERSION.SDK_INT < 31) {
                    canvas.drawCircle(width / 2f, height / 2f, 76f*d, glowPaint)
                }
            }
        }.apply {
            alpha = b.alphaValue
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(b.color) }
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60f*d, 60f*d, Shader.TileMode.CLAMP))
            } else {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
        }
        val lp = FrameLayout.LayoutParams((280*d).toInt(), (280*d).toInt(), b.gravity).apply {
            topMargin=(b.top*d).toInt(); bottomMargin=(b.bottom*d).toInt(); leftMargin=(b.left*d).toInt(); rightMargin=(b.right*d).toInt()
        }
        parent.addView(v, 0, lp)
        if (animate) ObjectAnimator.ofPropertyValuesHolder(v,
            PropertyValuesHolder.ofFloat(View.TRANSLATION_X,0f,40f*d),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y,0f,30f*d),
            PropertyValuesHolder.ofFloat(View.SCALE_X,1f,1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y,1f,1.15f)).apply {
            duration=b.duration; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE; interpolator=S1Fx.easeInOut; start()
        }
    }
}

fun View.attachPress(glowable: Boolean, lowFx: Boolean, reduced: Boolean) {
    val density = resources.displayMetrics.density
    val glow = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(0x6699A96A, 0x2699A96A, 0x0099A96A)
        gradientRadius = 180f*density
    }
    val useGlow = glowable && !lowFx && !reduced
    if (useGlow) foreground = glow
    var glowAnim: ValueAnimator? = null
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat(View.SCALE_X,.92f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y,.92f)).apply {
                    duration=if(reduced) 0L else 90L; interpolator=S1Fx.easeOut; start()
                }
                if(useGlow) {
                    glow.setGradientCenter((e.x/v.width.coerceAtLeast(1)), (e.y/v.height.coerceAtLeast(1)))
                    glowAnim?.cancel()
                    glowAnim=ValueAnimator.ofFloat(0f,1f).apply {
                        duration=720L; interpolator=android.view.animation.LinearInterpolator()
                        addUpdateListener { a ->
                            val t=a.animatedValue as Float
                            // Expand while fading instead of flashing brighter first.
                            glow.alpha=(210f*(1f-t)*(1f-t)).toInt().coerceIn(0,210)
                            glow.gradientRadius=density*(24f+220f*t)
                        }
                        start()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if(useGlow) {
                    glowAnim?.cancel()
                    glowAnim=ValueAnimator.ofInt(glow.alpha,0).apply { duration=450L; interpolator=S1Fx.ease; addUpdateListener { a -> glow.alpha=a.animatedValue as Int }; start() }
                }
                if(reduced) { v.scaleX=1f; v.scaleY=1f } else springBack(v)
            }
        }
        false
    }
}

fun springBack(v: View) {
    val x = PropertyValuesHolder.ofKeyframe(View.SCALE_X,
        Keyframe.ofFloat(0f, v.scaleX.coerceAtLeast(0.92f)),
        Keyframe.ofFloat(0.45f, 1.06f),
        Keyframe.ofFloat(0.70f, 0.985f),
        Keyframe.ofFloat(1f, 1f))
    val y = PropertyValuesHolder.ofKeyframe(View.SCALE_Y,
        Keyframe.ofFloat(0f, v.scaleY.coerceAtLeast(0.92f)),
        Keyframe.ofFloat(0.45f, 1.06f),
        Keyframe.ofFloat(0.70f, 0.985f),
        Keyframe.ofFloat(1f, 1f))
    ObjectAnimator.ofPropertyValuesHolder(v, x, y).apply {
        duration = 600L
        interpolator = android.view.animation.LinearInterpolator()
        start()
    }
}

class StoryFlowDrawable(private val density: Float) : android.graphics.drawable.Drawable() {
    var progress=0f
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors=intArrayOf(0xFF1F2A16.toInt(),0xFF414D2D.toInt(),0xFF89945A.toInt(),0xFFD0C79A.toInt(),0xFF414D2D.toInt(),0xFF1F2A16.toInt())
    override fun draw(canvas: Canvas) {
        val w=bounds.width().toFloat(); val h=bounds.height().toFloat(); val shift=w*2f*progress
        paint.shader=LinearGradient(-shift,0f,-shift+w*3f,h,colors,null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f,0f,w,h,34f*density,34f*density,paint)
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(colorFilter:android.graphics.ColorFilter?){paint.colorFilter=colorFilter}
    @Deprecated("Deprecated in Java") override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
}
fun startStoryFlow(d: StoryFlowDrawable) {
    ValueAnimator.ofFloat(0f,1f).apply { duration=9000L; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE; interpolator=S1Fx.easeInOut; addUpdateListener { d.progress=it.animatedValue as Float; d.invalidateSelf() }; start() }
}

class SheenView(context: Context): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:Canvas) {
        val w=width.toFloat(); val h=height.toFloat()
        canvas.save(); canvas.skew(-0.3249f,0f)
        paint.shader=LinearGradient(0f,0f,w,0f,intArrayOf(0x00FFFFFF,0x42FFFFFF,0x00FFFFFF),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint); canvas.restore()
    }
}
fun startSheen(v:View, cardWidth:Float) {
    ObjectAnimator.ofFloat(v,View.TRANSLATION_X,-.6f*cardWidth,1.2f*cardWidth).apply { duration=3800L; repeatCount=ValueAnimator.INFINITE; interpolator=S1Fx.easeInOut; start() }
}
fun startTwinkle(v:View, delayMs:Long) {
    ObjectAnimator.ofPropertyValuesHolder(v,PropertyValuesHolder.ofFloat(View.ALPHA,.25f,1f),PropertyValuesHolder.ofFloat(View.SCALE_X,.8f,1.15f),PropertyValuesHolder.ofFloat(View.SCALE_Y,.8f,1.15f)).apply { duration=2600L; startDelay=delayMs; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE; interpolator=S1Fx.easeInOut; start() }
}
fun startNudge(v:View,density:Float) { ObjectAnimator.ofFloat(v,View.TRANSLATION_X,0f,5f*density).apply { duration=1600L; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE; interpolator=S1Fx.easeInOut; start() } }

class FadedCircleImage(context:Context): ImageView(context) {
    private val maskPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    override fun onDraw(canvas:Canvas) {
        val w=width.toFloat(); val h=height.toFloat(); if(w<=0f||h<=0f)return
        val layer=canvas.saveLayer(0f,0f,w,h,null)
        super.onDraw(canvas)
        val cx=w*.5f; val cy=h*.44f
        val far=Math.hypot((w*.5).toDouble(),(h*.56).toDouble()).toFloat()
        maskPaint.shader=RadialGradient(cx,cy,far,intArrayOf(0xFF000000.toInt(),0xFF000000.toInt(),0x99000000.toInt(),0x00000000),floatArrayOf(0f,.52f,.70f,.90f),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,maskPaint); canvas.restoreToCount(layer)
    }
}
fun haloDrawable(density:Float)=GradientDrawable().apply { gradientType=GradientDrawable.RADIAL_GRADIENT; colors=intArrayOf(0x80D0C79A.toInt(),0x00D0C79A); gradientRadius=80f*density }
fun startHaloPulse(v:View) { ObjectAnimator.ofPropertyValuesHolder(v,PropertyValuesHolder.ofFloat(View.ALPHA,.35f,1f),PropertyValuesHolder.ofFloat(View.SCALE_X,.94f,1.08f),PropertyValuesHolder.ofFloat(View.SCALE_Y,.94f,1.08f)).apply { duration=2800L; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE; interpolator=S1Fx.easeInOut; start() } }
fun startBob(v:View,density:Float) { ObjectAnimator.ofFloat(v,View.TRANSLATION_Y,0f,-4f*density).apply { duration=3400L; repeatCount=ValueAnimator.INFINITE; repeatMode=ValueAnimator.REVERSE; interpolator=S1Fx.easeInOut; start() } }
fun staggerIn(views:List<View>,density:Float,enabled:Boolean) {
    if(!enabled)return
    views.forEachIndexed { i,v ->
        v.alpha=0f; v.translationY=16f*density
        v.animate().alpha(1f).translationY(0f).setStartDelay(i*40L).setDuration(420L).setInterpolator(S1Fx.outCurve).start()
    }
}


// Full-page ambient effects layer. Non-interactive and self-stops when detached.
class SettingsAmbientLayer(context: Context, private val motionEnabled: Boolean) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private val particles = (0 until 24).map { i ->
        floatArrayOf(((i * 37) % 97) / 97f, ((i * 61 + 13) % 89) / 89f,
            1.2f + ((i * 7) % 13) / 10f, ((i * 19) % 100) / 100f)
    }
    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (motionEnabled && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 18000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = S1Fx.easeInOut
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }
    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val drift = if (motionEnabled) phase else 0.18f
        val glowSpecs = arrayOf(
            floatArrayOf(w * (0.18f + 0.08f * drift), h * (0.24f + 0.04f * drift), 0x3889945A.toFloat()),
            floatArrayOf(w * (0.82f - 0.07f * drift), h * (0.53f + 0.05f * drift), 0x30D0C79A.toFloat()),
            floatArrayOf(w * (0.36f + 0.04f * drift), h * (0.82f - 0.03f * drift), 0x28414D2D.toFloat())
        )
        glowSpecs.forEach { spec ->
            val radius = 138f * density
            paint.shader = RadialGradient(spec[0], spec[1], radius,
                intArrayOf(spec[2].toInt(), 0x00000000), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(spec[0], spec[1], radius, paint)
        }
        paint.shader = null
        particles.forEachIndexed { i, p ->
            val travel = if (motionEnabled) ((phase + p[3]) % 1f) else p[3]
            val wave = kotlin.math.sin((phase * 6.28318f + i).toDouble()).toFloat()
            val x = p[0] * w + if (motionEnabled) wave * 7f * density else 0f
            val y = ((p[1] + travel * 0.12f) % 1f) * h
            paint.color = if (i % 4 == 0) 0x66D0C79A else 0x4489945A
            paint.alpha = (255f * (0.20f + 0.20f * kotlin.math.abs(wave))).toInt().coerceIn(12, 90)
            canvas.drawCircle(x, y, p[2] * density, paint)
        }
        paint.alpha = 255
    }
}
