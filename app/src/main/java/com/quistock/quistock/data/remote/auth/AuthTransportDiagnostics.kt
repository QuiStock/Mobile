package com.quistock.quistock.data.remote.auth

import com.quistock.quistock.domain.port.Logger
import okhttp3.HttpUrl
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

internal class AuthTransportDiagnostics(private val baseUrl: HttpUrl, private val logger: Logger?) {
    fun requestStarted(context: Map<String, String>): Long {
        val started = System.nanoTime()
        logger?.debug(
            "Auth HTTP request started",
            context + mapOf(
                "https" to baseUrl.isHttps,
                "placeholder_host" to baseUrl.host.endsWith(".invalid"),
                "base_path_discarded" to (baseUrl.encodedPath != "/"),
            ),
        )
        return started
    }

    fun transportFailed(error: IOException, started: Long, context: Map<String, String>) {
        logger?.info(
            "Auth HTTP transport failed",
            context + mapOf(
                "failure" to transportFailureCategory(error),
                "elapsed_ms" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
            ),
        )
    }
}

internal fun transportFailureCategory(error: IOException): String = when (error) {
    is UnknownHostException -> "dns"
    is ConnectException -> "connection"
    is InterruptedIOException -> "timeout"
    is SSLPeerUnverifiedException -> "tls_peer_verification"
    is SSLHandshakeException -> "tls_handshake"
    is SSLException -> "tls"
    is ProtocolException -> "protocol"
    else -> "io"
}
