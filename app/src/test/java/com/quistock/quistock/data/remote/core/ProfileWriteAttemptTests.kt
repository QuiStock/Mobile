package com.quistock.quistock.data.remote.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProfileWriteAttemptTests {
    private val request = Request.Builder().url("https://core.example.test/profile").build()
    private val chain = mockk<Interceptor.Chain>()

    private fun response(status: Int) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(status).message("fixture").build()

    @Test fun automaticServerFollowUpCannotRepeatWrite() {
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns response(503)
        val attempt = ProfileWriteAttempt()
        assertEquals(503, attempt.send(chain).code)
        val error = runCatching { attempt.send(chain) }.exceptionOrNull()
        assertTrue(error is IOException)
        verify(exactly = 1) { chain.proceed(any()) }
    }

    @Test fun explicitUnauthorizedAllowsExactlyOneRenewalRetry() {
        every { chain.request() } returns request
        every { chain.proceed(any()) } returnsMany listOf(response(401), response(200))
        val attempt = ProfileWriteAttempt()
        attempt.send(chain)
        assertEquals(200, attempt.send(chain).code)
        assertTrue(runCatching { attempt.send(chain) }.isFailure)
        verify(exactly = 2) { chain.proceed(any()) }
    }

    @Test fun missingCloudinaryConfigurationDisablesUpload() {
        assertEquals(false, CloudinaryPhotoRepository("", "").available)
        assertEquals(false, CloudinaryPhotoRepository("../invalid", "fixture").available)
        assertEquals(true, CloudinaryPhotoRepository("fixture", "fixture-preset").available)
    }
}
