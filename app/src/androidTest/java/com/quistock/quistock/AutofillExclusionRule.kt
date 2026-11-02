package com.quistock.quistock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * Keep the device autofill service (for example, an account saved in Google Password Manager)
 * from filling form fields or opening its overlay, which steals window focus from Espresso.
 * The exclusion is applied as each activity is created, before its window gains focus, so it
 * also covers `scenario.recreate()`.
 */
class AutofillExclusionRule : ExternalResource() {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activity.window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun before() {
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    override fun after() {
        application.unregisterActivityLifecycleCallbacks(callbacks)
    }
}
