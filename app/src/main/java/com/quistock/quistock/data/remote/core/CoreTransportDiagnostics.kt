package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.auth.transportFailureCategory
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.Logger
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fixed diagnostic fields only; request credentials, URL and response contents never reach Logger. */
internal class CoreTransportDiagnostics(origin: HttpUrl, request: Request, private val logger: Logger?) {
    private val started = System.nanoTime()
    private val context = mapOf(
        "operation" to if (request.url.encodedPath == origin.resolve("profile")?.encodedPath) {
            "core_profile"
        } else {
            "core_request"
        },
    )
    private val configuration = mapOf(
        "https" to origin.isHttps,
        "placeholder_host" to origin.host.endsWith(".invalid"),
        "base_path_present" to (origin.encodedPath != "/"),
        "method" to request.method,
    )
    var stage: String = "session"

    fun started() {
        logger?.debug("Core HTTP request started", context + configuration)
    }

    fun sessionInspected(snapshot: SessionSnapshot) {
        logger?.debug("Core session inspected", context + ("access_available" to snapshot.hasAccess))
    }

    fun renewalStarted(reason: String) {
        stage = "refresh"
        logger?.debug("Core session renewal started", context + ("reason" to reason))
    }

    fun renewalCompleted(reason: String, initial: SessionSnapshot, updated: SessionSnapshot) {
        logger?.debug(
            "Core session renewal completed",
            context + mapOf(
                "reason" to reason,
                "access_available" to updated.hasAccess,
                "generation_changed" to (initial.generation != updated.generation),
            ),
        )
    }

    fun blocked(error: SessionException) {
        logger?.info(
            "Core HTTP request blocked",
            context + mapOf(
                "stage" to stage,
                "failure" to (error.reason?.name?.lowercase() ?: "unavailable"),
                "elapsed_ms" to elapsed(started),
            ),
        )
    }

    fun send(chain: Interceptor.Chain, request: Request, attempt: Int): Response {
        stage = "transport"
        val attemptStarted = System.nanoTime()
        val fields = context + ("attempt" to attempt)
        logger?.debug("Core HTTP attempt started", fields)
        return try {
            chain.proceed(request).also { response ->
                logger?.debug(
                    "Core HTTP response received",
                    fields + mapOf("http_status" to response.code, "elapsed_ms" to elapsed(attemptStarted)),
                )
            }
        } catch (error: SessionException) {
            throw error
        } catch (error: IOException) {
            logger?.info(
                "Core HTTP transport failed",
                fields + mapOf("failure" to transportFailureCategory(error), "elapsed_ms" to elapsed(attemptStarted)),
            )
            throw error
        }
    }

    private fun elapsed(start: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
}
