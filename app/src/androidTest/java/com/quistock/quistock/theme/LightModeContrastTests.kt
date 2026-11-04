package com.quistock.quistock.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inflates every screen in light mode and measures each text against its effective background. */
@RunWith(AndroidJUnit4::class)
class LightModeContrastTests {
    private val problems = mutableListOf<String>()
    private lateinit var context: Context

    @Test fun textIsLegibleAndWhiteOnPurpleInEveryLayout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val light = Configuration(target.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        context = ContextThemeWrapper(target.createConfigurationContext(light), R.style.Theme_QuiStock)
        val checked = mutableListOf<String>()
        instrumentation.runOnMainSync {
            R.layout::class.java.fields.filter { it.name !in NOT_INFLATABLE }.forEach { field ->
                val root = LayoutInflater.from(context).inflate(field.getInt(null), null)
                check(field.name, root, windowColor())
                checked += field.name
            }
        }
        assertTrue("No layouts were inspected", checked.size > MIN_LAYOUTS)
        assertTrue(problems.joinToString("\n", "\n"), problems.isEmpty())
    }

    private fun check(layout: String, view: View, parentColor: Int) {
        val color = fill(view.background)?.let { ColorUtils.compositeColors(it, parentColor) } ?: parentColor
        if (view is TextView) checkText(layout, view, color)
        if (view is ViewGroup) (0 until view.childCount).forEach { check(layout, view.getChildAt(it), color) }
    }

    private fun checkText(layout: String, view: TextView, background: Int) {
        val text = ColorUtils.compositeColors(view.currentTextColor, background)
        val ratio = ColorUtils.calculateContrast(text, background)
        val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault(view.text.toString())
        if (background == context.getColor(R.color.color_violeta) && text != Color.WHITE) {
            problems += "$layout/$name: not white on purple"
        } else if (ratio < MIN_CONTRAST) {
            problems += "$layout/$name: contrast %.2f".format(ratio)
        }
    }

    private fun windowColor(): Int = context.getColor(R.color.color_petroleo)

    private fun fill(drawable: Drawable?): Int? = when (drawable) {
        is ColorDrawable -> drawable.color

        is GradientDrawable -> drawable.colors?.first() ?: drawable.color?.getColorForState(
            drawable.state,
            Color.TRANSPARENT,
        )

        is LayerDrawable -> if (drawable.numberOfLayers > 0) fill(drawable.getDrawable(0)) else null

        is DrawableContainer -> fill(drawable.current)

        else -> null
    }

    private companion object {
        const val MIN_CONTRAST = 3.0
        const val MIN_LAYOUTS = 30

        // activity_main and fragment_maps host fragments, so they only inflate inside the activity.
        // The status popup's background is chosen in code and paired with white text.
        val NOT_INFLATABLE = setOf("activity_main", "fragment_maps", "view_status_popup")
    }
}
