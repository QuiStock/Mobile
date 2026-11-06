package com.quistock.quistock.presentation.chatbot

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.quistock.quistock.R
import kotlin.math.max
import kotlin.math.sin

/** Three bouncing dots, shown in a bot bubble while the answer is being generated. */
class TypingIndicatorView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val radius = DOT_RADIUS_DP * density
    private val gap = DOT_GAP_DP * density
    private val bounce = BOUNCE_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_subtitle)
    }
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MILLIS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setBackgroundResource(R.drawable.bg_chat_bubble_bot)
        val horizontal = (PAD_H_DP * density).toInt()
        val vertical = (PAD_V_DP * density).toInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        contentDescription = context.getString(R.string.chatbot_typing)
    }

    /** Places the indicator as the last child of [container] while [visible], and removes it otherwise. */
    fun update(container: LinearLayout, scroll: ScrollView?, visible: Boolean) {
        container.removeView(this)
        if (!visible) return
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.START
            bottomMargin = (BOTTOM_MARGIN_DP * density).toInt()
        }
        container.addView(this)
        scroll?.post { scroll.fullScroll(FOCUS_DOWN) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = paddingLeft + paddingRight + (DOTS * 2 * radius + (DOTS - 1) * gap).toInt()
        val height = paddingTop + paddingBottom + (2 * radius + bounce).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val baseY = paddingTop + bounce + radius
        for (index in 0 until DOTS) {
            val wave = max(0f, sin((phase - index * DOT_DELAY) * TWO_PI))
            paint.alpha = ((MIN_ALPHA + (1f - MIN_ALPHA) * wave) * MAX_ALPHA).toInt()
            val x = paddingLeft + radius + index * (2 * radius + gap)
            canvas.drawCircle(x, baseY - bounce * wave, radius, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DOTS = 3
        const val DOT_RADIUS_DP = 3.5f
        const val DOT_GAP_DP = 5f
        const val BOUNCE_DP = 5f
        const val BOTTOM_MARGIN_DP = 8
        const val PAD_H_DP = 16
        const val PAD_V_DP = 12
        const val CYCLE_MILLIS = 1100L
        const val DOT_DELAY = 0.18f
        const val MIN_ALPHA = 0.4f
        const val MAX_ALPHA = 255
        const val TWO_PI = (2 * Math.PI).toFloat()
    }
}
