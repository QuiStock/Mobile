package com.quistock.quistock.presentation.navigation

import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.UserRole

internal class BottomBarView(private val container: LinearLayout, private val onSelect: (MainTab) -> Unit) {
    private var tabs = emptyList<MainTab>()
    private var generation: Long? = null

    fun render(role: UserRole?, selected: MainTab?, visible: Boolean, currentGeneration: Long?) {
        val next = role?.let(MainTab::forRole).orEmpty()
        if (tabs != next || generation != currentGeneration) {
            tabs = next
            generation = currentGeneration
            container.removeAllViews()
            tabs.forEach { container.addView(button(it)) }
        }
        tabs.forEachIndexed { index, tab ->
            update(container.getChildAt(index) as AppCompatTextView, tab, selected)
        }
        container.isVisible = visible && tabs.isNotEmpty()
    }

    private fun button(tab: MainTab): AppCompatTextView = object : AppCompatTextView(container.context) {
        override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
    }.apply {
        id = buttonId(tab)
        setText(tab.label)
        gravity = Gravity.CENTER_HORIZONTAL
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.navigation_label_size))
        val padH = resources.getDimensionPixelSize(R.dimen.navigation_item_padding_horizontal)
        val padTop = resources.getDimensionPixelSize(R.dimen.navigation_item_padding_top)
        val padBottom = resources.getDimensionPixelSize(R.dimen.navigation_item_padding_bottom)
        setPadding(padH, padTop, padH, padBottom)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        isFocusable = true
        isClickable = true
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.navigation_drawable_padding)
        ViewCompat.setAccessibilityHeading(this, false)
        setOnClickListener { if (parent === container) onSelect(tab) }
    }

    private fun update(button: AppCompatTextView, tab: MainTab, selected: MainTab?) {
        button.isSelected = tab == selected
        val style = if (button.isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        button.setTypeface(button.typeface, style)
        val icon = if (button.isSelected) tab.activeIcon else tab.icon
        val drawable = ContextCompat.getDrawable(container.context, icon)
        val size = container.resources.getDimensionPixelSize(R.dimen.nav_bar_icon_size)
        drawable?.setBounds(0, 0, size, size)
        button.setCompoundDrawables(null, drawable, null, null)
        val color = if (button.isSelected) R.color.color_limao else R.color.color_branco
        button.setTextColor(ContextCompat.getColor(container.context, color))
    }

    private fun buttonId(tab: MainTab): Int = when (tab) {
        MainTab.HOME -> R.id.navigation_home
        MainTab.INTERFERENCES -> R.id.navigation_interferences
        MainTab.PRODUCTS -> R.id.navigation_products
        MainTab.EMPLOYEES -> R.id.navigation_employees
        MainTab.CHATBOT -> R.id.navigation_chatbot
    }
}
