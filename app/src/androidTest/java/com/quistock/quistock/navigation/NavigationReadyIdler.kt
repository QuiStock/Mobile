package com.quistock.quistock.navigation

import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.quistock.quistock.R
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen

/** All lifecycle/View reads and listener installation/removal happen on the main thread. */
internal class NavigationReadyIdler(
    private val activity: MainActivity,
    private val destination: Int,
    private val condition: (MainActivity) -> Boolean,
) : FragmentManager.FragmentLifecycleCallbacks(),
    IdlingResource,
    AutoCloseable {
    private val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    private var callback: IdlingResource.ResourceCallback? = null
    private val layout = ViewTreeObserver.OnGlobalLayoutListener { signal() }
    private val navigation = NavController.OnDestinationChangedListener { _, _, _ -> signal() }

    init {
        host.childFragmentManager.registerFragmentLifecycleCallbacks(this, false)
        host.navController.addOnDestinationChangedListener(navigation)
        activity.binding.root.viewTreeObserver.addOnGlobalLayoutListener(layout)
    }

    override fun getName(): String = "navigation-resumed-and-laid-out-$destination"

    override fun isIdleNow(): Boolean {
        val entry = host.navController.currentBackStackEntry
        val fragment = host.childFragmentManager.findFragmentByTag(entry?.id)
        val resumed = fragment?.lifecycle?.currentState == Lifecycle.State.RESUMED
        val view = fragment?.view
        val laidOut = view?.run { isLaidOut && isShown } == true
        val uncovered = view?.findViewWithTag<android.view.View>(SkeletonLoadingScreen.OVERLAY_TAG) == null
        val settled = resumed && laidOut && uncovered
        val ready = host.navController.currentDestination?.id == destination && settled
        return ready && condition(activity)
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }

    override fun onFragmentResumed(fm: FragmentManager, f: Fragment) = signal()

    private fun signal() {
        if (isIdleNow()) callback?.onTransitionToIdle()
    }

    override fun close() {
        host.childFragmentManager.unregisterFragmentLifecycleCallbacks(this)
        host.navController.removeOnDestinationChangedListener(navigation)
        activity.binding.root.viewTreeObserver.removeOnGlobalLayoutListener(layout)
    }
}

internal fun awaitNavigationReady(
    scenario: ActivityScenario<MainActivity>,
    destination: Int,
    condition: (MainActivity) -> Boolean = { true },
) {
    lateinit var idler: NavigationReadyIdler
    scenario.onActivity { idler = NavigationReadyIdler(it, destination, condition) }
    IdlingRegistry.getInstance().register(idler)
    try {
        onView(isRoot()).check(matches(isDisplayed()))
    } finally {
        IdlingRegistry.getInstance().unregister(idler)
        scenario.onActivity { idler.close() }
    }
}
