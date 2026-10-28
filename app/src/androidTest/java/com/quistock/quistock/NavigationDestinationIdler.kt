package com.quistock.quistock

import androidx.test.core.app.ActivityScenario
import com.quistock.quistock.navigation.awaitNavigationReady
import com.quistock.quistock.presentation.activity.MainActivity

/** Wait for the actual destination view, including removal of the intercepting loading overlay. */
internal fun awaitDestination(scenario: ActivityScenario<MainActivity>, destination: Int) {
    awaitNavigationReady(scenario, destination)
}
