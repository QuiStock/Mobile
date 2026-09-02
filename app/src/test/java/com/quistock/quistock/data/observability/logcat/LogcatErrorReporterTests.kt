package com.quistock.quistock.data.observability.logcat

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class LogcatErrorReporterTests {
    private lateinit var errorReporter: LogcatErrorReporter

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        errorReporter = LogcatErrorReporter()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `when recording an error, should write exception and context to Logcat`() {
        val exception = IllegalStateException("Unexpected authentication state")
        val context = mapOf(
            "operation" to "login",
            "provider" to "firebase_auth",
        )

        errorReporter.record(exception, context)

        verify(exactly = 1) {
            Log.e(
                "QuiStockError",
                "operation=login, provider=firebase_auth",
                exception,
            )
        }
    }
}
