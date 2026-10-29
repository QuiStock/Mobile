package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.data.remote.core.CoreTransportDiagnostics
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection

/** Origin must be explicitly configured; legacy internal integrations are not implicitly Core. */
class CoreSessionInterceptor(
    private val origin: HttpUrl,
    private val session: SessionUseCase,
    private val custody: AuthCookieCustody,
    private val logger: Logger? = null,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        if (!isCoreOrigin(request.url)) {
            return@runBlocking chain.proceed(request)
        }
        val diagnostics = CoreTransportDiagnostics(origin, request, logger)
        diagnostics.started()
        try {
            interceptCore(chain, request, diagnostics)
        } catch (error: SessionException) {
            diagnostics.blocked(error)
            throw error
        }
    }

    private suspend fun interceptCore(
        chain: Interceptor.Chain,
        request: Request,
        diagnostics: CoreTransportDiagnostics,
    ): Response {
        // A failed renewal waits for explicit recovery; background calls cannot retry it.
        requireHealthySession()
        val initial = session.snapshot()
        val expected = request.tag(SessionSnapshot::class.java)
        if (expected != null && expected.generation != initial.generation) throw SessionException()
        diagnostics.sessionInspected(initial)
        val current = if (!initial.hasAccess || session.state.value is SessionState.Failure) {
            renew(initial, "access_unavailable", diagnostics)
        } else {
            session.requireAccess(initial)
        }
        val response = diagnostics.send(chain, authorized(request, current.generation, diagnostics), 1)
        requireCurrentSession(current, response, diagnostics)
        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return enforceStoreEligibility(response, current)
        response.close()
        val valid = renew(current, "unauthorized", diagnostics)
        val retried = diagnostics.send(chain, authorized(request, valid.generation, diagnostics), 2)
        requireCurrentSession(valid, retried, diagnostics)
        return checkRetriedResponse(retried, valid, diagnostics)
    }

    private fun requireHealthySession() {
        val reason = when (val state = session.state.value) {
            is SessionState.Failure -> state.reason
            is SessionState.OfflineReadOnly -> state.reason
            else -> null
        }
        if (reason != null) throw SessionException(reason)
    }

    private fun requireCurrentSession(
        expected: SessionSnapshot,
        response: Response,
        diagnostics: CoreTransportDiagnostics,
    ) {
        if (session.snapshot().generation != expected.generation) {
            response.close()
            diagnostics.stage = "session_changed"
            throw SessionException()
        }
    }

    private suspend fun checkRetriedResponse(
        response: Response,
        expected: SessionSnapshot,
        diagnostics: CoreTransportDiagnostics,
    ): Response {
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            response.close()
            session.expire(expected)
            diagnostics.stage = "unauthorized_after_refresh"
            throw SessionException()
        }
        return enforceStoreEligibility(response, expected)
    }

    private suspend fun enforceStoreEligibility(response: Response, expected: SessionSnapshot): Response {
        if (response.code == HttpURLConnection.HTTP_FORBIDDEN) {
            val code = response.peekBody(MAX_ERROR_BODY_BYTES).use { body ->
                runCatching {
                    Json.parseToJsonElement(body.string()).jsonObject["error"]?.jsonPrimitive?.content
                }.getOrNull()
            }
            if (code == "NO_ACTIVE_STORE") session.expire(expected)
        }
        return response
    }

    private suspend fun renew(
        expected: SessionSnapshot,
        reason: String,
        diagnostics: CoreTransportDiagnostics,
    ): SessionSnapshot {
        diagnostics.renewalStarted(reason)
        val updated = session.refresh(expected)
        diagnostics.renewalCompleted(reason, expected, updated)
        if (updated.generation != expected.generation) {
            diagnostics.stage = "session_changed"
            throw SessionException()
        }
        return session.requireAccess(updated)
    }

    private fun isCoreOrigin(url: HttpUrl): Boolean =
        url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port

    private fun authorized(request: Request, generation: Long, diagnostics: CoreTransportDiagnostics): Request {
        diagnostics.stage = "authorization"
        val token = custody.access(generation) ?: run {
            diagnostics.stage = "access_missing"
            throw SessionException()
        }
        return request.newBuilder()
            .removeHeader("Cookie")
            .removeHeader("X-CSRF-TOKEN")
            .removeHeader("X-XSRF-TOKEN")
            .header("Authorization", "Bearer $token")
            .build()
    }
}

private const val MAX_ERROR_BODY_BYTES = 8192L
