package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileUseCaseTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val repository = mockk<ProfileRepository>()
    private val profile = ProfileUseCase(repository, session)

    private suspend fun login() {
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        session.login("fixture@example.test", "fixture")
    }

    private fun valid(role: String = "FUNCIONARIO", storeId: Long? = 7, storeName: String? = "Fixture store") =
        UserProfile("1", "Fixture user", "fixture@example.test", role, "ACTIVE", null, storeId, storeName)

    @Test fun profileIsSharedInMemoryAfterSuccessfulLoad() = runTest {
        login()
        val expected = valid()
        coEvery { repository.read() } returns ProfileResult.Success(expected)
        profile.load()
        profile.load()
        assertSame(expected, (profile.state.value as ProfileState.Ready).profile)
        coVerify(exactly = 1) { repository.read() }
    }

    @Test fun concurrentReadersShareEvenAFailedRequest() = runTest {
        login()
        val response = CompletableDeferred<ProfileResult>()
        coEvery { repository.read() } coAnswers { response.await() }
        val first = async { profile.load() }
        runCurrent()
        val second = async { profile.load() }
        runCurrent()
        assertEquals(ProfileState.Loading, profile.state.value)
        response.complete(ProfileResult.Failure())
        first.await()
        second.await()
        coVerify(exactly = 1) { repository.read() }
        assertTrue(profile.state.value is ProfileState.Failure)
        assertEquals(SessionState.Active, session.state.value)
    }

    @Test fun manualRetryKeepsAuthenticationAndCanLoadProfile() = runTest {
        login()
        coEvery { repository.read() } returnsMany listOf(ProfileResult.Failure(1000), ProfileResult.Success(valid()))
        profile.load()
        assertEquals(1000L, (profile.state.value as ProfileState.Failure).retryAfterMillis)
        profile.load()
        assertTrue(profile.state.value is ProfileState.Ready)
        coVerify(exactly = 1) { auth.login(any(), any(), any()) }
    }

    @Test fun forbiddenRevokesSessionAndPreservesRejectionReason() = runTest {
        login()
        coEvery { repository.read() } returns ProfileResult.Forbidden
        profile.load()
        assertEquals(ProfileRejection.FORBIDDEN, (profile.state.value as ProfileState.Rejected).reason)
        assertEquals(SessionState.Expired, session.state.value)
    }

    @Test fun missingStoreRejectsBothMobileRoles() = runTest {
        for (role in listOf("FUNCIONARIO", "GERENTE")) {
            login()
            coEvery { repository.read() } returns ProfileResult.Success(valid(role, null, null))
            profile.load()
            assertEquals(ProfileRejection.NO_STORE, (profile.state.value as ProfileState.Rejected).reason)
            assertEquals(SessionState.Expired, session.state.value)
        }
    }

    @Test fun restoredAdministrativeRolesCannotEnterEvenWithStore() = runTest {
        for (role in listOf("ADMIN", "GERENTE_REGIONAL")) {
            login()
            coEvery { repository.read() } returns ProfileResult.Success(valid(role))
            profile.load()
            assertEquals(ProfileRejection.ROLE, (profile.state.value as ProfileState.Rejected).reason)
            assertEquals(SessionState.Expired, session.state.value)
        }
    }

    @Test fun unknownRoleIsInvalidAndDoesNotPublishProfile() = runTest {
        login()
        coEvery { repository.read() } returns ProfileResult.Success(valid("UNKNOWN"))
        profile.load()
        assertTrue(profile.state.value is ProfileState.Failure)
        assertEquals(SessionState.Active, session.state.value)
    }

    @Test fun responseFromPreviousSessionCannotPublishOrRevokeNewSession() = runTest {
        login()
        val response = CompletableDeferred<ProfileResult>()
        coEvery { repository.read() } coAnswers { response.await() }
        val oldRequest = async { profile.load() }
        runCurrent()
        session.expire(session.snapshot())
        profile.clear()
        login()
        response.complete(ProfileResult.Forbidden)
        oldRequest.await()
        assertFalse(profile.state.value is ProfileState.Ready)
        assertEquals(SessionState.Active, session.state.value)
    }

    @Test fun signedOutSessionDoesNotRequestProfile() = runTest {
        profile.load()
        coVerify(exactly = 0) { repository.read() }
    }
}
