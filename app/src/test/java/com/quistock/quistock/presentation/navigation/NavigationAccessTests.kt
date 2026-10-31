package com.quistock.quistock.presentation.navigation

import com.quistock.quistock.R
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.presentation.activity.SessionViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAccessTests {
    private val model = mockk<SessionViewModel>(relaxed = true)
    private val profile = MutableStateFlow<ProfileState>(ProfileState.Empty)
    private val session = MutableStateFlow<SessionState>(SessionState.Active)

    private fun access(role: UserRole = UserRole.EMPLOYEE, generation: Long = 1): NavigationAccess {
        profile.value = ProfileState.Ready(
            generation,
            UserProfile(
                id = "1",
                name = "Fixture",
                email = "fixture@example.test",
                role = role,
                status = UserStatus.ACTIVE,
                photoUrl = null,
                storeId = 1,
                storeName = "Fixture store",
            ),
        )
        every { model.profile.state } returns profile
        every { model.session.state } returns session
        every { model.session.snapshot() } returns SessionSnapshot(1, true)
        return NavigationAccess(model)
    }

    @Test fun currentProfileAllowsOperatorRootsButRejectsManagerDestinations() {
        val access = access()
        assertNotNull(access.ready())
        assertFalse(access.rejects(R.id.productControlFragment))
        assertTrue(access.rejects(R.id.employeeFragment))
        assertTrue(access.rejects(R.id.managerInterferencesFragment))
        assertTrue(access.rejects(R.id.managerOrderDetailFragment))
    }

    @Test fun managerCanUseManagerDestinations() {
        val access = access(UserRole.MANAGER)
        assertFalse(access.rejects(R.id.employeeFragment))
        assertFalse(access.rejects(R.id.managerInterferencesFragment))
    }

    @Test fun oldGenerationAndUnsupportedRoleCannotSupplyButtons() {
        assertNull(access(generation = 0).ready())
        assertNull(access(UserRole.REGIONAL_MANAGER).ready())
    }

    @Test fun loadingAndRejectedProfileCannotSupplyButtons() {
        val access = access()
        profile.value = ProfileState.Loading
        assertNull(access.ready())
        profile.value = ProfileState.Empty
        assertNull(access.ready())
    }

    @Test fun logoutAndExpirationBlockImmediatelyAndRequireMemoryCleanup() {
        val access = access()
        session.value = SessionState.LoggingOut
        assertNull(access.ready())
        assertTrue(access.shouldClear())
        session.value = SessionState.Expired
        assertNull(access.ready())
        assertTrue(access.shouldClear())
    }

    @Test fun offlineReadOnlyBlocksAllTabsAndClearsProtectedVisitHistory() {
        val access = access()
        session.value = SessionState.OfflineReadOnly(com.quistock.quistock.domain.model.SessionFailure.NETWORK)
        assertNull(access.ready())
        assertTrue(access.shouldClear())
    }

    @Test fun temporaryFailureBlocksWithoutDiscardingCurrentSessionState() {
        val access = access()
        session.value = SessionState.Failure(com.quistock.quistock.domain.model.SessionFailure.NETWORK)
        assertNull(access.ready())
        assertFalse(access.shouldClear())
    }
}
