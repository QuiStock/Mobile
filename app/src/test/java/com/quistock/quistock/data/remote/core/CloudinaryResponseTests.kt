package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.port.ProfilePhotoUploadResult
import io.mockk.every
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudinaryResponseTests {
    private val fixture = CloudinaryUploadFixture()
    private val jpeg = byteArrayOf(1, 2)

    @Test fun incompatibleResponseNeverConfirmsUpload() = runTest {
        val responses = listOf(
            "not-json",
            "{}",
            "null",
            "[]",
            fixture.envelope(bytes = "null"),
            fixture.envelope(bytes = "0"),
            fixture.envelope(bytes = "-1"),
            fixture.envelope(bytes = "\"unknown\""),
            fixture.envelope(bytes = "2097153"),
            fixture.envelope(format = "png"),
            """{"bytes":128,"secure_url":"https://images.example.test/a.jpg"}""",
        )
        for (body in responses) {
            fixture.body = body
            assertTrue(fixture.repository.upload(jpeg) is ProfilePhotoUploadResult.Failed)
        }
    }

    @Test fun unsafeMissingAndOversizedUrlsAreRejected() = runTest {
        val urls = listOf(
            "http://images.example.test/a.jpg",
            "https://user:pass@images.example.test/a.jpg",
            "https://:pass@images.example.test/a.jpg",
            "relative.jpg",
            "",
            "https://images.example.test/" + "a".repeat(500),
        )
        for (url in urls) {
            fixture.body = fixture.envelope(url = url)
            assertTrue(fixture.repository.upload(jpeg) is ProfilePhotoUploadResult.Failed)
        }
        fixture.body = """{"bytes":128,"format":"jpg"}"""
        assertTrue(fixture.repository.upload(jpeg) is ProfilePhotoUploadResult.Failed)
    }

    @Test fun serverErrorsAndOversizedBodiesAreNotAcceptedAsUploads() = runTest {
        fixture.status = 500
        assertTrue(fixture.repository.upload(jpeg) is ProfilePhotoUploadResult.Failed)
        fixture.status = 200
        fixture.body = "a".repeat(64 * 1024 + 1)
        assertTrue(fixture.repository.upload(jpeg) is ProfilePhotoUploadResult.Failed)
    }

    @Test fun cancellationIsPropagatedInsteadOfConvertedIntoFailure() = runTest {
        every { fixture.calls.newCall(any()) } throws CancellationException("fixture")
        val result = runCatching { fixture.repository.upload(jpeg) }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
