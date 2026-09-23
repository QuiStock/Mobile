package com.quistock.quistock.data.observability

import com.quistock.quistock.domain.port.ErrorReporter
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class CompositeErrorReporterTests {
    private val firstReporter = mockk<ErrorReporter>(relaxed = true)
    private val secondReporter = mockk<ErrorReporter>(relaxed = true)

    private val compositeErrorReporter = CompositeErrorReporter(
        reporters = listOf(firstReporter, secondReporter),
    )

    @Test
    fun `when recording an error, should forward it to every reporter`() {
        val exception = IllegalStateException("Unexpected authentication state")
        val message = "Authentication failed"
        val context = mapOf(
            "operation" to "login",
            "provider" to "firebase_auth",
        )

        compositeErrorReporter.record(message, exception, context)

        verify(exactly = 1) {
            firstReporter.record(message, exception, context)
            secondReporter.record(message, exception, context)
        }
    }
}
