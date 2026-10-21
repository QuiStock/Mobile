package com.quistock.quistock.presentation.activity

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionLogoutNavigationTests {
    @Test fun incompleteLogoutNeverNavigatesBackToAuthenticatedContent() {
        assertNull(sessionDestination(SessionState.LoggingOut, R.id.profileFragment))
        assertNull(sessionDestination(SessionState.LogoutFailed(false), R.id.profileFragment))
    }

    @Test fun completedLogoutGoesToLoginRegardlessOfRemoteResult() {
        for (confirmed in listOf(true, false)) {
            assertEquals(
                R.id.loginFragment,
                sessionDestination(SessionState.LoggedOut(confirmed), R.id.profileFragment),
            )
            assertNull(sessionDestination(SessionState.LoggedOut(confirmed), R.id.loginFragment))
        }
    }
}
