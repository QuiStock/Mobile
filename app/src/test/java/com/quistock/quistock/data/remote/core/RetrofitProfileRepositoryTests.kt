package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class RetrofitProfileRepositoryTests {
    private val api = mockk<ProfileApi>()
    private val repository = RetrofitProfileRepository(api)

    @Test fun forbiddenIsDifferentFromTemporaryFailure() = runTest {
        coEvery { api.read() } returns Response.error(403, "".toResponseBody())
        assertSame(ProfileResult.Forbidden, repository.read())
    }

    @Test fun profileMappingKeepsNumericStoreIdsAndNullablePhoto() = runTest {
        coEvery { api.read() } returns Response.success(
            ProfileResponse("1", "Fixture", "fixture@example.test", "FUNCIONARIO", "ACTIVE", storeId = 7),
        )
        val profile = (repository.read() as ProfileResult.Success).profile
        assertEquals(7L, profile.storeId)
        assertEquals(null, profile.photoUrl)
    }

    @Test fun emptySuccessfulResponseFailsClosed() = runTest {
        coEvery { api.read() } returns Response.success<ProfileResponse>(null)
        assertTrue(repository.read() is ProfileResult.Failure)
    }

    @Test fun networkFailureIsRecoverableWithoutLeakingThrowable() = runTest {
        coEvery { api.read() } throws IOException("fixture")
        assertTrue(repository.read() is ProfileResult.Failure)
    }

    @Test fun cancellationIsNotConvertedToRetryableError() = runTest {
        coEvery { api.read() } throws CancellationException()
        var cancelled = false
        try {
            repository.read()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test fun rateLimitUsesRetryAfterHeader() = runTest {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://core.example.test/profile").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(429).message("fixture")
            .headers(Headers.headersOf("Retry-After", "3")).build()
        coEvery { api.read() } returns Response.error("".toResponseBody("text/plain".toMediaType()), raw)
        assertEquals(3000L, (repository.read() as ProfileResult.Failure).retryAfterMillis)
    }

    @Test fun retryAfterAcceptsSecondsOrHttpDateAndRejectsInvalidValues() {
        assertEquals(2000L, retryAfterMillis("2", 0))
        assertEquals(1000L, retryAfterMillis("Thu, 01 Jan 1970 00:00:01 GMT", 0))
        assertEquals(0L, retryAfterMillis("-1", 0))
        assertEquals(0L, retryAfterMillis("invalid", 0))
        assertEquals(0L, retryAfterMillis(null, 0))
    }
}
