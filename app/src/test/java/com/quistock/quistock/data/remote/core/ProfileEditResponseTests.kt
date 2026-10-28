package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ProfileEditResponseTests {
    private val api = mockk<ProfileEditApi>()
    private val repository = RetrofitProfileEditRepository(api)
    private val changes = ProfileChanges(name = "New")
    private val expected = SessionSnapshot(1, true)

    @Test fun validUserDtoIsReturnedWithoutAnotherRead() = runTest {
        coEvery { api.update(any(), any()) } returns Response.success(
            ProfileResponse("fixture", "New", "fixture@example.test", "EMPLOYEE", "ACTIVE", storeId = 1),
        )
        val result = repository.update(changes, expected) as ProfileEditResult.Saved
        assertEquals("New", result.profile.name)
    }

    @Test fun validationAndOtherClientErrorsRemainDefinitive() = runTest {
        for (status in listOf(400, 404, 409)) {
            coEvery { api.update(any(), any()) } returns Response.error(status, "fixture".toResponseBody())
            val result = repository.update(changes, expected) as ProfileEditResult.Rejected
            assertEquals(status == 400, result.invalidInput)
        }
        coEvery { api.update(any(), any()) } returns Response.error(500, "fixture".toResponseBody())
        assertSame(ProfileEditResult.Uncertain, repository.update(changes, expected))
    }

    @Test fun rateLimitPreservesRetryAfter() = runTest {
        val raw = okhttp3.Response.Builder().request(Request.Builder().url("https://core.example.test/profile").build())
            .protocol(Protocol.HTTP_1_1).code(429).message("fixture")
            .headers(Headers.headersOf("Retry-After", "120")).build()
        coEvery { api.update(any(), any()) } returns Response.error("fixture".toResponseBody(), raw)
        val result = repository.update(changes, expected) as ProfileEditResult.Rejected
        assertEquals(120_000L, result.retryAfterMillis)
        assertFalse(result.invalidInput)
    }

    @Test fun cancellationIsNotReportedAsAnUncertainWrite() = runTest {
        coEvery { api.update(any(), any()) } throws CancellationException("fixture")
        val result = runCatching { repository.update(changes, expected) }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
