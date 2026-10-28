package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.port.ProfilePhotoUploadResult
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CloudinaryUploadTests {
    private val fixture = CloudinaryUploadFixture()
    private val jpeg = byteArrayOf(1, 2)

    @Test fun acceptedUploadUsesOnlyPublicMultipartFieldsAndNewIds() = runTest {
        val first = fixture.repository.upload(jpeg) as ProfilePhotoUploadResult.Uploaded
        val firstBody = Buffer().also { requireNotNull(fixture.request.captured.body).writeTo(it) }.readUtf8()
        assertEquals("https://images.example.test/new.jpg", first.url)
        assertEquals("api.cloudinary.com", fixture.request.captured.url.host)
        assertEquals("/v1_1/fixture/image/upload", fixture.request.captured.url.encodedPath)
        assertNull(fixture.request.captured.header("Authorization"))
        assertNull(fixture.request.captured.header("Cookie"))
        assertNull(fixture.request.captured.header("X-CSRF-TOKEN"))
        assertTrue(firstBody.contains("fixture-preset"))
        assertTrue(firstBody.contains("name=\"public_id\""))
        assertTrue(firstBody.contains("Content-Type: image/jpeg"))
        fixture.repository.upload(jpeg)
        val secondBody = Buffer().also { requireNotNull(fixture.request.captured.body).writeTo(it) }.readUtf8()
        assertFalse(publicId(firstBody) == publicId(secondBody))
    }

    @Test fun rateLimitReturnsDeadlineWithoutAutomaticRetry() = runTest {
        fixture.status = 429
        fixture.headers = Headers.headersOf("Retry-After", "120")
        val result = fixture.repository.upload(jpeg) as ProfilePhotoUploadResult.Failed
        assertEquals(120_000L, result.retryAfterMillis)
        verify(exactly = 1) { fixture.calls.newCall(any()) }
    }

    @Test fun invalidConfigurationAndOversizedImageNeverReachTransport() = runTest {
        val unavailable = CloudinaryPhotoRepository("", "", fixture.calls)
        assertTrue(unavailable.upload(jpeg) is ProfilePhotoUploadResult.Failed)
        assertTrue(fixture.repository.upload(ByteArray(2 * 1024 * 1024 + 1)) is ProfilePhotoUploadResult.Failed)
        verify(exactly = 0) { fixture.calls.newCall(any()) }
    }

    @Test fun networkFailureDoesNotRetry() = runTest {
        fixture.respond = { it.onFailure(fixture.call, IOException("fixture")) }
        assertTrue(fixture.repository.upload(jpeg) is ProfilePhotoUploadResult.Failed)
        verify(exactly = 1) { fixture.call.enqueue(any()) }
    }

    @Test fun cancellationCancelsTheActualCall() = runTest {
        val entered = CompletableDeferred<Unit>()
        fixture.respond = { entered.complete(Unit) }
        val upload = async { fixture.repository.upload(jpeg) }
        entered.await()
        upload.cancelAndJoin()
        assertTrue(upload.isCancelled)
        verify(exactly = 1) { fixture.call.cancel() }
    }

    private fun publicId(body: String): String = body.substringAfter("name=\"public_id\"")
        .substringAfter("\r\n\r\n").substringBefore("\r\n")
}
