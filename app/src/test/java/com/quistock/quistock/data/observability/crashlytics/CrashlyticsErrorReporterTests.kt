package com.quistock.quistock.data.observability.crashlytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.KeyValueBuilder
import com.google.firebase.crashlytics.recordException
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class CrashlyticsErrorReporterTests {
    private val crashlytics = mockk<FirebaseCrashlytics>()
    private lateinit var errorReporter: CrashlyticsErrorReporter

    @Before
    fun setup() {
        mockkStatic("com.google.firebase.crashlytics.FirebaseCrashlyticsKt")
        errorReporter = CrashlyticsErrorReporter(crashlytics)
    }

    @After
    fun tearDown() {
        unmockkStatic("com.google.firebase.crashlytics.FirebaseCrashlyticsKt")
    }

    @Test
    fun `when recording an error, should send exception and context to Crashlytics`() {
        val exception = IllegalStateException("Unexpected authentication state")
        val context = mapOf(
            "operation" to "login",
            "provider" to "firebase_auth",
        )
        val customKeys = slot<KeyValueBuilder.() -> Unit>()
        every {
            crashlytics.recordException(exception, capture(customKeys))
        } just runs

        errorReporter.record(exception, context)

        verify(exactly = 1) {
            crashlytics.recordException(exception, any<KeyValueBuilder.() -> Unit>())
        }
        verifyCustomKeys(customKeys, context)
    }

    private fun verifyCustomKeys(customKeys: CapturingSlot<KeyValueBuilder.() -> Unit>, context: Map<String, String>) {
        val builder = mockk<KeyValueBuilder>(relaxed = true)

        customKeys.captured.invoke(builder)

        context.forEach { (name, value) ->
            verify(exactly = 1) { builder.key(name, value) }
        }
    }
}
