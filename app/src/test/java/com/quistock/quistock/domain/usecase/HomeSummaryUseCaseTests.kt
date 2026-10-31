package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummary
import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SummaryFailure
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.HomeSummaryCache
import com.quistock.quistock.domain.port.HomeSummaryRepository
import com.quistock.quistock.domain.port.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeSummaryUseCaseTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val profiles = mockk<ProfileRepository>()
    private val profile = ProfileUseCase(profiles, session)
    private val remote = mockk<HomeSummaryRepository>()
    private val cache = mockk<HomeSummaryCache>()
    private val useCase = GetHomeSummaryUseCase(remote, cache, session, profile)
    private val data = HomeSummary(4, 2, 3, 5)
    private val scope = SessionBinding("session", "user", 51, System.currentTimeMillis() + 60_000)

    private suspend fun login() {
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        every { auth.currentBinding(any()) } returns scope
        coEvery { auth.bindProfile(any(), any(), any()) } returns scope
        coEvery {
            profiles.read()
        } returns ProfileResult.Success(
            UserProfile(
                "user",
                "Fixture",
                "fixture@example.test",
                UserRole.EMPLOYEE,
                UserStatus.ACTIVE,
                null,
                51,
                "Store",
            ),
        )
        coEvery { remote.read(51) } returns HomeSummaryResult.Success(data)
        coEvery { cache.write(scope, any()) } returns true
        session.login("fixture@example.test", "fixture")
    }

    @Test fun successSavesOnlyTheAuthorizedScopeAndCacheFailureKeepsRemoteData() = runTest {
        login()
        val first = useCase() as HomeSummaryResult.Success
        assertEquals(data, first.summary)
        assertTrue(first.persisted)
        coVerify { remote.read(51) }
        coEvery { cache.write(scope, any()) } throws java.io.IOException("disk unavailable")
        val second = useCase() as HomeSummaryResult.Success
        assertEquals(data, second.summary)
        assertFalse(second.persisted)
    }

    @Test fun lateResponseAfterLogoutCannotPublishOrWriteCache() = runTest {
        login()
        val release = CompletableDeferred<Unit>()
        coEvery { remote.read(51) } coAnswers {
            release.await()
            HomeSummaryResult.Success(data)
        }
        val pending = async { runCatching { useCase() } }
        runCurrent()
        session.logout()
        release.complete(Unit)
        assertTrue(pending.await().isFailure)
        coVerify(exactly = 0) { cache.write(any(), any()) }
    }

    @Test fun rateLimitIsSharedAcrossCallsAndFailureNeverWritesOrAdvancesTheCache() = runTest {
        login()
        val deadline = System.currentTimeMillis() + 60_000
        coEvery { remote.read(51) } returns HomeSummaryResult.Failure(SummaryFailure.RATE_LIMIT, deadline)
        assertEquals(deadline, (useCase() as HomeSummaryResult.Failure).retryAt)
        assertEquals(deadline, (useCase() as HomeSummaryResult.Failure).retryAt)
        coVerify(exactly = 1) { remote.read(51) }
        coVerify(exactly = 0) { cache.write(any(), any()) }
    }
}
