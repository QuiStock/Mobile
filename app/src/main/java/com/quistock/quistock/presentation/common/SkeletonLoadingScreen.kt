package com.quistock.quistock.presentation.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout

private const val SCREEN_COLOR = 0xFF11061F.toInt()
private const val PLACEHOLDER_COLOR = 0xFF30243D.toInt()
private const val PLACEHOLDER_HIGHLIGHT = 0xFF443453.toInt()
private const val SKELETON_DURATION_MS = 550L

enum class SkeletonScreenType {
    HOME,
    CHATBOT,
    ORDER,
    ORDER_SENT,
    PROMOTION,
    PROMOTION_SENT,
    INTERFERENCES,
    PRODUCT_CONTROL,
    PRODUCT_DETAIL,
}

/** Adds a short, non-interactive loading skeleton over an existing destination. */
object SkeletonLoadingScreen {
    @JvmStatic
    fun wrap(content: View, type: SkeletonScreenType): View {
        val context = content.context
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(SkeletonOverlay(context, type), FrameLayout.LayoutParams(-1, -1))
        }
    }
}

private class SkeletonOverlay(context: Context, private val type: SkeletonScreenType) : FrameLayout(context) {
    private val pulse = ValueAnimator.ofObject(
        android.animation.ArgbEvaluator(),
        PLACEHOLDER_COLOR,
        PLACEHOLDER_HIGHLIGHT,
    ).apply {
        duration = 850L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            placeholderDrawables.forEach { it.setColor(color) }
        }
    }
    private val placeholderDrawables = mutableListOf<GradientDrawable>()

    init {
        setBackgroundColor(SCREEN_COLOR)
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val skeleton = buildSkeleton()
        skeleton.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(skeleton, LayoutParams(-1, -1))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulse.start()
        postDelayed({
            animate().alpha(0f).setDuration(160L).withEndAction {
                (parent as? ViewGroup)?.removeView(this)
            }.start()
        }, SKELETON_DURATION_MS)
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.text = "Carregando tela"
        info.isFocusable = true
    }

    private fun buildSkeleton(): View {
        if (type == SkeletonScreenType.ORDER_SENT || type == SkeletonScreenType.PROMOTION_SENT) {
            return confirmationSkeleton()
        }
        val page = column(24, 20, 24, 20)
        page.addView(header())
        when (type) {
            SkeletonScreenType.HOME -> {
                page.addView(block(190, 30, top = 36))
                page.addView(row(3, 116, top = 18))
                page.addView(block(175, 18, top = 32))
                repeat(3) { page.addView(card(68, top = 14)) }
            }

            SkeletonScreenType.CHATBOT -> {
                page.addView(chatBubble(0.78f, Gravity.START, 52))
                page.addView(chatBubble(0.66f, Gravity.END, 34))
                page.addView(chatBubble(0.88f, Gravity.START, 34))
                val spacer = View(context)
                page.addView(spacer, LinearLayout.LayoutParams(-1, 0, 1f))
                page.addView(card(48, top = 10))
            }

            SkeletonScreenType.ORDER, SkeletonScreenType.PROMOTION -> {
                page.addView(block(210, 26, top = 28))
                page.addView(block(140, 15, top = 10))
                repeat(4) { page.addView(field(top = 22)) }
                page.addView(card(52, top = 26))
            }

            SkeletonScreenType.INTERFERENCES -> {
                page.addView(block(200, 26, top = 28))
                page.addView(row(3, 88, top = 16))
                repeat(4) { page.addView(card(82, top = 14)) }
            }

            SkeletonScreenType.PRODUCT_CONTROL -> {
                page.addView(block(210, 26, top = 28))
                page.addView(card(42, top = 14))
                page.addView(row(2, 40, top = 14))
                repeat(3) { page.addView(card(88, top = 14)) }
            }

            SkeletonScreenType.PRODUCT_DETAIL -> {
                page.addView(block(110, 20, top = 24))
                page.addView(block(215, 24, top = 18))
                page.addView(block(165, 16, top = 10))
                page.addView(card(105, top = 20))
                repeat(3) { page.addView(field(top = 18)) }
                page.addView(card(50, top = 24))
            }

            SkeletonScreenType.ORDER_SENT, SkeletonScreenType.PROMOTION_SENT -> Unit
        }
        val spacer = View(context)
        page.addView(spacer, LinearLayout.LayoutParams(-1, 0, 1f))
        when (type) {
            SkeletonScreenType.HOME,
            SkeletonScreenType.CHATBOT,
            SkeletonScreenType.INTERFERENCES,
            SkeletonScreenType.PRODUCT_CONTROL,
            -> page.addView(bottomBar())

            else -> Unit
        }
        return page
    }

    private fun confirmationSkeleton(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(32), dp(32), dp(32))
        addView(block(76, 76))
        addView(block(210, 24, top = 24))
        addView(block(250, 16, top = 12))
        addView(block(170, 16, top = 8))
        addView(card(52, top = 32))
    }

    private fun header(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(block(34, 34))
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(block(126, 14))
            addView(block(88, 10, top = 7))
        }
        addView(titles, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        addView(block(22, 22))
    }

    private fun field(top: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(block(102, 12))
        addView(block(-1, 44, top = 10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    }

    private fun chatBubble(widthFraction: Float, gravity: Int, top: Int): View = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = placeholder(14)
            addView(block(-1, 12))
            addView(block(128, 12, top = 8))
        }
        val availableWidth = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 48
        addView(bubble, LayoutParams(dp((availableWidth * widthFraction).toInt()), -2, gravity))
    }

    private fun row(count: Int, height: Int, top: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        repeat(count) { index ->
            addView(
                block(0, height),
                LinearLayout.LayoutParams(0, dp(height), 1f).apply {
                    if (index > 0) marginStart = dp(10)
                },
            )
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    }

    private fun card(height: Int, top: Int): View = block(-1, height, top = top, radius = 14)

    private fun bottomBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        repeat(4) { index ->
            addView(
                block(28, 26),
                LinearLayout.LayoutParams(0, dp(26), 1f).apply {
                    if (index > 0) marginStart = dp(12)
                },
            )
        }
        layoutParams = LinearLayout.LayoutParams(-1, dp(68))
    }

    private fun block(width: Int, height: Int, top: Int = 0, radius: Int = 8): View = View(context).apply {
        background = placeholder(radius)
        layoutParams = LinearLayout.LayoutParams(if (width < 0) width else dp(width), dp(height)).apply {
            if (top > 0) topMargin = dp(top)
        }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun placeholder(radius: Int): GradientDrawable = rounded(PLACEHOLDER_COLOR, radius).also {
        placeholderDrawables.add(it)
    }

    private fun column(start: Int, top: Int, end: Int, bottom: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(start), dp(top), dp(end), dp(bottom))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
