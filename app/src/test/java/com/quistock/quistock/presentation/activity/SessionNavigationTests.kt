package com.quistock.quistock.presentation.activity

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionNavigationTests {
    @Test fun restoredSessionLeavesSplashForHome() {
        assertEquals(R.id.homeFragment, sessionDestination(SessionState.Active, R.id.splashFragment))
    }

    @Test fun transientRestoreFailureDoesNotLeaveSplash() {
        assertNull(sessionDestination(SessionState.Failure(SessionFailure.NETWORK), R.id.splashFragment))
    }

    @Test fun activeSessionPreservesProtectedDestination() {
        assertNull(sessionDestination(SessionState.Active, R.id.homeFragment))
    }
}
