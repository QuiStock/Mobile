package com.quistock.quistock.skeleton

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkeletonLoadingScreenTests {

    @Test
    fun wrap_forAllScreenTypes_shouldCreateOverlayAndAttachAndDetachCleanly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (type in SkeletonScreenType.values()) {
                    val dummyContent = View(activity)
                    val wrapped = SkeletonLoadingScreen.wrap(dummyContent, type)

                    assertThat(wrapped is FrameLayout, `is`(true))
                    val frameLayout = wrapped as FrameLayout
                    assertThat(frameLayout.childCount, `is`(2))
                    assertThat(frameLayout.getChildAt(0), `is`(dummyContent))

                    val overlay = frameLayout.getChildAt(1)
                    assertThat(overlay, notNullValue())
                    assertThat(overlay.isClickable, `is`(true))
                    assertThat(overlay.isFocusable, `is`(true))

                    val accessibilityInfo = AccessibilityNodeInfo.obtain()
                    overlay.onInitializeAccessibilityNodeInfo(accessibilityInfo)
                    assertThat(accessibilityInfo.text?.toString(), `is`("Carregando tela"))
                    accessibilityInfo.recycle()

                    // Attach to activity root to trigger onAttachedToWindow and animations
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    root.addView(wrapped)
                    root.removeView(wrapped)
                }
            }
        }
    }

    @Test
    fun overlay_fadeAndRemoval_shouldDismissAfterDuration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var wrappedView: FrameLayout
            lateinit var root: ViewGroup

            scenario.onActivity { activity ->
                root = activity.findViewById(android.R.id.content)
                val dummyContent = View(activity)
                wrappedView = SkeletonLoadingScreen.wrap(dummyContent, SkeletonScreenType.HOME) as FrameLayout
                root.addView(wrappedView)
            }

            // Wait for skeleton duration (550ms) + fade duration (160ms) + buffer
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(850L)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                // The overlay child should have been removed from wrappedView
                assertThat(wrappedView.childCount, `is`(1))
                root.removeView(wrappedView)
            }
        }
    }
}
