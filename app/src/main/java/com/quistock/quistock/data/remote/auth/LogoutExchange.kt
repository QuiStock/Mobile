package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.domain.port.Logger
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Private, single-use capture. Never reads or commits the live session after local revocation. */
internal class LogoutExchange(
    private val origin: HttpUrl,
    private val jar: StagedAuthCookieJar,
    private val api: AuthApi,
    private val logger: Logger?,
    private val now: () -> Long,
    private val requiresCsrf: Boolean,
) {
    private val diagnostics = AuthTransportDiagnostics(origin, logger)
    private val logoutUrl = requireNotNull(origin.resolve(AuthCookieCustody.LOGOUT_PATH))
    private var stage = "logout"

    @Suppress("TooGenericExceptionCaught")
    fun execute(): Boolean {
        val started = diagnostics.requestStarted(mapOf("operation" to "auth_logout"))
        val deadline = started + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        return try {
            val token = if (requiresCsrf) prepare(deadline) else null
            if (requiresCsrf && !hasValidCredentials(token)) {
                false
            } else {
                stage = "logout"
                exchange(api.logout(token), deadline) { response ->
                    val confirmed = response.code() == HttpURLConnection.HTTP_NO_CONTENT
                    if (confirmed) logMissingExpiry()
                    confirmed
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            diagnostics.transportFailed(error, started, fields())
            false
        } catch (_: Exception) {
            logger?.info("Auth protocol preparation failed", fields())
            false
        } finally {
            jar.discardCsrf()
        }
    }

    private fun hasValidCredentials(token: String?): Boolean =
        token != null && jar.hasRefresh(logoutUrl) && jar.hasCsrf(logoutUrl)

    private fun prepare(deadline: Long): String? {
        stage = "csrf"
        return exchange(api.csrf(), deadline) { response ->
            response.body()?.takeIf {
                response.code() == HttpURLConnection.HTTP_OK && it.validHeaderToken() && jar.hasCsrf(logoutUrl)
            }?.token
        }
    }

    private fun <T, R> exchange(call: Call<T>, deadline: Long, consume: (Response<T>) -> R): R {
        try {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw SocketTimeoutException("Logout deadline exceeded")
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS).deadlineNanoTime(deadline)
            val response = call.execute()
            try {
                logger?.debug("Auth HTTP response received", fields() + ("http_status" to response.code()))
                if (System.nanoTime() >= deadline) throw SocketTimeoutException("Logout deadline exceeded")
                return consume(response)
            } finally {
                response.errorBody()?.close()
            }
        } finally {
            call.cancel()
        }
    }

    private fun logMissingExpiry() {
        if (listOf(AuthCookieCustody.ACCESS, AuthCookieCustody.REFRESH).any { name ->
                jar.received().none { it.name == name && it.expiresAt <= now() }
            }
        ) {
            logger?.info("Logout response missing cookie expiry", fields())
        }
    }

    private fun fields() = mapOf("operation" to "auth_logout", "stage" to stage)

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
