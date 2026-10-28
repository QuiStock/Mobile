package com.quistock.quistock.profile

import androidx.navigation.NavController
import androidx.test.espresso.IdlingResource

/** Navigation completion is observed on the UI thread; no sleeps or cross-thread View reads. */
internal class ProfileDestinationIdler(
    private val nav: NavController,
    private val destination: Int,
) : IdlingResource, AutoCloseable {
    private var callback: IdlingResource.ResourceCallback? = null
    private val listener = NavController.OnDestinationChangedListener { _, current, _ ->
        if (current.id == destination) callback?.onTransitionToIdle()
    }

    init { nav.addOnDestinationChangedListener(listener) }

    override fun getName(): String = "profile-destination-$destination"
    override fun isIdleNow(): Boolean = nav.currentDestination?.id == destination
    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback) {
        this.callback = callback
    }
    override fun close() { nav.removeOnDestinationChangedListener(listener) }
}
