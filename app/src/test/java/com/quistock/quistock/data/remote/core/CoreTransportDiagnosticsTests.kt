package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.auth.AuthCookieCustody
import com.quistock.quistock.data.remote.auth.CoreSessionInterceptor
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class CoreTransportDiagnosticsTests {
    @Test fun successfulRequestLogsOnlyAllowlistedContextAndStatus() {
        val fixture = CoreDiagnosticsFixture()
        every { fixture.chain.proceed(any()) } returns fixture.response(200)
        fixture.intercept().close()

        val start = fixture.logger.event("Core HTTP request started")
        assertEquals("core_profile", start["operation"])
        assertEquals("GET", start["method"])
        assertEquals(true, start["https"])
        assertEquals(true, start["base_path_present"])
        val response = fixture.logger.event("Core HTTP response received")
        assertEquals(200, response["http_status"])
        assertEquals(1, response["attempt"])
        assertTrue(response["elapsed_ms"] is Long)
        fixture.logger.assertSafe()
    }

    @Test fun unauthorizedResponseLogsRenewalAndOneReplay() {
        val fixture = CoreDiagnosticsFixture()
        every { fixture.chain.proceed(any()) } returnsMany listOf(fixture.response(401), fixture.response(200))
        fixture.intercept().close()

        val responses = fixture.logger.events.filter { it.first == "Core HTTP response received" }
        assertEquals(listOf(1, 2), responses.map { it.second["attempt"] })
        assertEquals("unauthorized", fixture.logger.event("Core session renewal started")["reason"])
        assertEquals(true, fixture.logger.event("Core session renewal completed")["access_available"])
        coVerify(exactly = 1) { fixture.session.refresh(any()) }
        verify(exactly = 2) { fixture.chain.proceed(any()) }
        fixture.logger.assertSafe()
    }

    @Test fun transportFailureKeepsExceptionAndLogsCategoryWithoutMessage() {
        val fixture = CoreDiagnosticsFixture()
        val error = UnknownHostException("private-sentinel")
        every { fixture.chain.proceed(any()) } throws error

        assertSame(error, runCatching { fixture.intercept() }.exceptionOrNull())
        val failure = fixture.logger.event("Core HTTP transport failed")
        assertEquals("dns", failure["failure"])
        assertEquals(1, failure["attempt"])
        assertTrue(failure["elapsed_ms"] is Long)
        fixture.logger.assertSafe()
    }

    @Test fun missingAccessIsBlockedWithoutSendingRequest() {
        val fixture = CoreDiagnosticsFixture()
        every { fixture.custody.access(any()) } returns null

        assertTrue(runCatching { fixture.intercept() }.exceptionOrNull() is SessionException)
        assertEquals("access_missing", fixture.logger.event("Core HTTP request blocked")["stage"])
        verify(exactly = 0) { fixture.chain.proceed(any()) }
        fixture.logger.assertSafe()
    }

    @Test fun protocolFailureRequiresManualRecoveryAndIsNotReportedAsTransportError() {
        val fixture = CoreDiagnosticsFixture()
        fixture.state.value = SessionState.Failure(SessionFailure.PROTOCOL)

        assertTrue(runCatching { fixture.intercept() }.exceptionOrNull() is SessionException)
        assertEquals("protocol", fixture.logger.event("Core HTTP request blocked")["failure"])
        assertFalse(fixture.logger.events.any { it.first == "Core HTTP transport failed" })
        coVerify(exactly = 0) { fixture.session.refresh(any()) }
        fixture.logger.assertSafe()
    }
}

private class CoreDiagnosticsFixture {
    val logger = RecordingCoreLogger()
    val session = mockk<SessionUseCase>()
    val custody = mockk<AuthCookieCustody>()
    val chain = mockk<Interceptor.Chain>()
    val state = MutableStateFlow<SessionState>(SessionState.Active)
    private val snapshot = SessionSnapshot(1, true)
    private val origin = "https://core.example.test/base/".toHttpUrl()
    private val request = Request.Builder()
        .url("https://core.example.test/base/profile?private-sentinel=private-sentinel")
        .header("Authorization", "Bearer private-sentinel")
        .header("Cookie", "refresh_token=private-sentinel")
        .build()

    init {
        every { session.state } returns state
        every { session.snapshot() } returns snapshot
        coEvery { session.requireAccess(any()) } coAnswers { firstArg<SessionSnapshot>() }
        coEvery { session.refresh(any()) } returns snapshot.copy(revision = 1)
        every { custody.access(any()) } returns "fixture-access-secret"
        every { chain.request() } returns request
    }

    fun intercept(): Response = CoreSessionInterceptor(origin, session, custody, logger).intercept(chain)

    fun response(code: Int): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("private-sentinel")
        .body("private-sentinel".toResponseBody()).build()
}

internal class RecordingCoreLogger : Logger {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun debug(msg: String, context: Map<String, Any>) {
        events += msg to context.toMap()
    }

    override fun info(msg: String, context: Map<String, Any>) {
        events += msg to context.toMap()
    }

    override fun warn(msg: String, throwable: Throwable?, context: Map<String, Any>) {
        throw AssertionError("Unexpected diagnostic level")
    }

    override fun error(msg: String, throwable: Throwable?, context: Map<String, Any>) {
        throw AssertionError("Unexpected diagnostic level")
    }

    fun event(message: String): Map<String, Any> = events.single { it.first == message }.second

    fun assertSafe() {
        val allowed = setOf(
            "operation", "https", "placeholder_host", "base_path_present", "method", "access_available",
            "attempt", "http_status", "elapsed_ms", "stage", "failure", "reason", "generation_changed",
            "result_discarded",
        )
        events.forEach { (message, context) ->
            assertTrue(context.keys.all { it in allowed })
            val rendered = message + context.toString()
            assertFalse(rendered.contains("private-sentinel"))
            assertFalse(rendered.contains("fixture-access-secret"))
            assertFalse(rendered.contains("example.test"))
            assertFalse(rendered.contains("refresh_token"))
        }
    }
}
