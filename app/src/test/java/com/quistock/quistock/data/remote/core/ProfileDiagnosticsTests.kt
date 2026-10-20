package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ProfileDiagnosticsTests {
    private val logger = RecordingCoreLogger()

    @Test fun invalidPayloadLogsDecodeCategoryWithoutPayloadOrExceptionMessage() = runTest {
        val api = mockk<ProfileApi>()
        coEvery { api.read() } throws SerializationException("private-sentinel")
        assertTrue(RetrofitProfileRepository(api, logger).read() is ProfileResult.Failure)
        assertEquals("invalid_response", logger.event("Core profile load failed")["failure"])
        logger.assertSafe()
    }

    @Test fun emptySuccessBodyIsDiagnosedWithoutChangingFailureMapping() = runTest {
        val api = mockk<ProfileApi>()
        coEvery { api.read() } returns Response.success<ProfileResponse>(null)
        assertTrue(RetrofitProfileRepository(api, logger).read() is ProfileResult.Failure)
        assertEquals("empty_body", logger.event("Core profile load failed")["failure"])
        logger.assertSafe()
    }

    @Test fun cancellationStillPropagatesAndDoesNotLogThrowableDetails() = runTest {
        val api = mockk<ProfileApi>()
        val cancelled = CancellationException("private-sentinel")
        coEvery { api.read() } throws cancelled
        val repository = RetrofitProfileRepository(api, logger)
        assertSame(cancelled, runCatching { repository.read() }.exceptionOrNull())
        assertEquals("core_profile", logger.event("Core profile request cancelled")["operation"])
        logger.assertSafe()
    }

    @Test fun missingStoreLogsOnlyRejectionCategoryAndPreservesBlocking() = runTest {
        val session = activeSession()
        val repository = mockk<ProfileRepository>()
        coEvery { repository.read() } returns ProfileResult.Success(profile(null, null))
        val useCase = ProfileUseCase(repository, session, logger)
        useCase.load()
        assertEquals(ProfileRejection.NO_STORE, (useCase.state.value as ProfileState.Rejected).reason)
        assertEquals("no_store", logger.event("Core profile rejected")["reason"])
        logger.assertSafe()
    }

    @Test fun successfulLoadAndCacheReuseDoNotLogUserOrStoreData() = runTest {
        val session = activeSession()
        val repository = mockk<ProfileRepository>()
        coEvery { repository.read() } returns ProfileResult.Success(profile(1, "private-sentinel"))
        val useCase = ProfileUseCase(repository, session, logger)
        useCase.load()
        useCase.load()
        assertTrue(useCase.state.value is ProfileState.Ready)
        assertEquals("core_profile", logger.event("Core profile loaded")["operation"])
        assertEquals("core_profile", logger.event("Core profile cache reused")["operation"])
        coVerify(exactly = 1) { repository.read() }
        logger.assertSafe()
    }

    private suspend fun activeSession(): SessionUseCase {
        val auth = mockk<AuthRepository>(relaxed = true)
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        return SessionUseCase(auth).also { it.login("fixture@example.test", "fixture") }
    }

    private fun profile(storeId: Long?, storeName: String?) = UserProfile(
        id = "private-sentinel",
        name = "private-sentinel",
        email = "private-sentinel",
        role = "FUNCIONARIO",
        status = "ACTIVE",
        photoUrl = "https://example.test/private-sentinel",
        storeId = storeId,
        storeName = storeName,
    )
}
