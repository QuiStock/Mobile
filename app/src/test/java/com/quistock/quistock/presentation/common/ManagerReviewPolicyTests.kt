package com.quistock.quistock.presentation.common

import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerReviewPolicyTests {
    @Test
    fun `only manager from active current session can see review information`() {
        UserRole.entries.forEach { role ->
            val visible = ManagerReviewPolicy.canShow(ready(role), SessionState.Active, GENERATION)
            if (role == UserRole.MANAGER) assertTrue(visible) else assertFalse(visible)
        }
    }

    @Test
    fun `old session and missing or loading profile never expose manager information`() {
        assertFalse(ManagerReviewPolicy.canShow(ready(UserRole.MANAGER), SessionState.Active, GENERATION + 1))
        assertFalse(ManagerReviewPolicy.canShow(null, SessionState.Active, GENERATION))
        assertFalse(ManagerReviewPolicy.canShow(ProfileState.Loading, SessionState.Active, GENERATION))
        assertFalse(ManagerReviewPolicy.canShow(ready(UserRole.MANAGER), SessionState.LoggingOut, GENERATION))
    }

    private fun ready(role: UserRole) = ProfileState.Ready(
        GENERATION,
        UserProfile(
            "fixture", "Fixture", "fixture@example.test", role, UserStatus.ACTIVE,
            null, 17, "Fixture store",
        ),
    )

    private companion object {
        const val GENERATION = 7L
    }
}
