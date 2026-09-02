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
        val context = mapOf(
            "operation" to "login",
            "provider" to "firebase_auth",
        )

        compositeErrorReporter.record(exception, context)

        verify(exactly = 1) {
            firstReporter.record(exception, context)
            secondReporter.record(exception, context)
        }
    }
}
