package com.quistock.quistock

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.quistock.quistock.presentation.activity.MainActivity

/** Navigation completion is observed on the UI thread; no sleeps or cross-thread View reads. */
internal class NavigationDestinationIdler(private val nav: NavController, private val destination: Int) :
    IdlingResource,
    AutoCloseable {
    private var callback: IdlingResource.ResourceCallback? = null
    private val listener = NavController.OnDestinationChangedListener { _, current, _ ->
        if (current.id == destination) callback?.onTransitionToIdle()
    }

    init {
        nav.addOnDestinationChangedListener(listener)
    }

    override fun getName(): String = "navigation-destination-$destination"
    override fun isIdleNow(): Boolean = nav.currentDestination?.id == destination
    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
    override fun close() {
        nav.removeOnDestinationChangedListener(listener)
    }
}

internal fun awaitDestination(scenario: ActivityScenario<MainActivity>, destination: Int) {
    lateinit var idler: NavigationDestinationIdler
    scenario.onActivity { activity ->
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        idler = NavigationDestinationIdler(host.navController, destination)
    }
    IdlingRegistry.getInstance().register(idler)
    try {
        onView(isRoot()).check(matches(isDisplayed()))
    } finally {
        IdlingRegistry.getInstance().unregister(idler)
        scenario.onActivity { idler.close() }
    }
}
