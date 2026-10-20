package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.data.remote.auth.dto.CsrfResponse
import com.quistock.quistock.data.remote.auth.dto.LoginRequest
import com.quistock.quistock.domain.port.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resumeWithException

internal enum class AuthOutcome { SUCCESS, INVALID, PLATFORM, NETWORK, TIMEOUT, SERVER, LIMIT, PROTOCOL, UNEXPECTED }

internal class AuthRequestContext(
    val origin: HttpUrl,
    val custody: AuthCookieCustody,
    val logger: Logger?,
    val rateLimited: (Response<*>) -> Unit,
)

/** Owns one isolated GET/POST exchange; the repository owns session-wide coordination. */
internal class AuthRequest(
    private val generation: Long,
    private val login: LoginRequest?,
    private val jar: StagedAuthCookieJar,
    private val api: AuthApi,
    private val context: AuthRequestContext,
) {
    private val refreshUrl = requireNotNull(context.origin.resolve(AuthCookieCustody.REFRESH_PATH))
    private val logContext = mapOf("operation" to if (login == null) "auth_refresh" else "login")

    suspend fun execute(): AuthOutcome {
        logStarted()
        return try {
            val preparation = if (login == null) prepareCsrf() else AuthOutcome.SUCCESS to null
            coroutineContext.ensureActive()
            val ready = when {
                preparation.first != AuthOutcome.SUCCESS -> preparation.first
                login != null -> AuthOutcome.SUCCESS
                else -> refreshEligibility()
            }
            if (ready == AuthOutcome.SUCCESS) post(preparation.second) else ready
        } finally {
            jar.discardCsrf()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepareCsrf(): Pair<AuthOutcome, String?> {
        val started = System.nanoTime()
        return try {
            val response = api.csrf().awaitResponse()
            try {
                prepareResponse(response)
            } finally {
                response.errorBody()?.close()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AuthFollowUpBlocked) {
            blockedOutcome(error) to null
        } catch (error: IOException) {
            logFailure(error, started, logContext + ("stage" to "csrf")) to null
        } catch (_: Exception) {
            AuthOutcome.PROTOCOL to null
        }
    }

    private fun prepareResponse(response: Response<CsrfResponse>): Pair<AuthOutcome, String?> {
        context.logger?.debug(
            "Auth HTTP response received",
            logContext + mapOf("stage" to "csrf", "http_status" to response.code()),
        )
        return when {
            !context.custody.isCurrent(generation) -> AuthOutcome.PROTOCOL to null

            response.code() == HTTP_TOO_MANY_REQUESTS -> {
                context.rateLimited(response)
                AuthOutcome.LIMIT to null
            }

            response.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR -> AuthOutcome.SERVER to null

            response.code() != HttpURLConnection.HTTP_OK -> AuthOutcome.PROTOCOL to null

            else -> validatePreparation(response.body())
        }
    }

    private fun validatePreparation(body: CsrfResponse?): Pair<AuthOutcome, String?> = when {
        body == null -> AuthOutcome.PROTOCOL to null
        !body.validHeaderToken() -> AuthOutcome.PROTOCOL to null
        else -> refreshEligibility() to body.token
    }

    private fun refreshEligibility(): AuthOutcome = when {
        !context.custody.isCurrent(generation) -> AuthOutcome.PROTOCOL
        !jar.hasRefresh(refreshUrl) -> AuthOutcome.INVALID
        !jar.hasCsrf(refreshUrl) -> AuthOutcome.PROTOCOL
        else -> AuthOutcome.SUCCESS
    }

    private suspend fun post(token: String?): AuthOutcome {
        val call = if (login == null) api.refresh(requireNotNull(token)) else api.login(login)
        val started = System.nanoTime()
        return try {
            // Keep the response and durable commit together after the server consumes refresh.
            val response = call.execute()
            try {
                commitResponse(response)
            } finally {
                response.errorBody()?.close()
            }
        } catch (error: AuthFollowUpBlocked) {
            commitBlocked(error)
        } catch (error: IOException) {
            logFailure(error, started, logContext)
        } finally {
            call.cancel()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitResponse(response: Response<Unit>): AuthOutcome = try {
        context.logger?.debug("Auth HTTP response received", logContext + ("http_status" to response.code()))
        val outcome = classify(response)
        if (!context.custody.commit(generation, jar.received(), outcome == AuthOutcome.SUCCESS)) {
            AuthOutcome.UNEXPECTED
        } else {
            if (outcome == AuthOutcome.LIMIT) context.rateLimited(response)
            outcome
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        AuthOutcome.UNEXPECTED
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitBlocked(error: AuthFollowUpBlocked): AuthOutcome = try {
        if (context.custody.commit(generation, jar.received(), false)) {
            blockedOutcome(error)
        } else {
            AuthOutcome.PROTOCOL
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AuthOutcome.UNEXPECTED
    }

    private fun logFailure(error: IOException, started: Long, fields: Map<String, String>): AuthOutcome {
        context.logger?.info(
            "Auth HTTP transport failed",
            fields + mapOf(
                "failure" to transportFailureCategory(error),
                "elapsed_ms" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            ),
        )
        return if (error is SocketTimeoutException) AuthOutcome.TIMEOUT else AuthOutcome.NETWORK
    }

    private fun classify(response: Response<Unit>): AuthOutcome {
        val code = runCatching {
            Json.parseToJsonElement(response.errorBody()?.string().orEmpty()).jsonObject["code"]?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            response.code() == HttpURLConnection.HTTP_OK -> AuthOutcome.SUCCESS

            response.code() == HTTP_TOO_MANY_REQUESTS -> AuthOutcome.LIMIT

            response.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR -> AuthOutcome.SERVER

            response.code() == HttpURLConnection.HTTP_UNAUTHORIZED &&
                code == (if (login != null) "invalid_credentials" else "invalid_refresh_token") -> AuthOutcome.INVALID

            login != null && response.code() == HttpURLConnection.HTTP_FORBIDDEN &&
                code == "platform_not_allowed" -> AuthOutcome.PLATFORM

            login == null -> AuthOutcome.PROTOCOL

            else -> AuthOutcome.UNEXPECTED
        }
    }

    private fun logStarted() {
        context.logger?.debug(
            "Auth HTTP request started",
            logContext + mapOf(
                "https" to context.origin.isHttps,
                "placeholder_host" to context.origin.host.endsWith(".invalid"),
                "base_path_discarded" to (context.origin.encodedPath != "/"),
            ),
        )
    }
}

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val FIRST_HEADER_CHARACTER = '!'
private const val LAST_HEADER_CHARACTER = '~'

internal fun CsrfResponse.validHeaderToken() = headerName == "X-CSRF-TOKEN" && token.isNotEmpty() &&
    token.all { it in FIRST_HEADER_CHARACTER..LAST_HEADER_CHARACTER }

private fun blockedOutcome(error: AuthFollowUpBlocked): AuthOutcome =
    if (error.status >= HttpURLConnection.HTTP_INTERNAL_ERROR) AuthOutcome.SERVER else AuthOutcome.PROTOCOL

private suspend fun <T> Call<T>.awaitResponse(): Response<T> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            if (continuation.isActive) {
                continuation.resume(response) { _, value, _ -> value.errorBody()?.close() }
            } else {
                response.errorBody()?.close()
            }
        }
        override fun onFailure(call: Call<T>, error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    })
}
