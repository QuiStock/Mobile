package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.port.ProfilePhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class HttpProfilePhotoRepository internal constructor(private val client: OkHttpClient) : ProfilePhotoRepository {
    // No Core interceptor, cookies, redirects, diagnostics, or disk cache on external photo requests.
    constructor() : this(
        OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
    )

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val target = url.toHttpUrlOrNull()?.takeIf { it.isPublicPhotoUrl() }
            target?.let { download(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun HttpUrl.isPublicPhotoUrl(): Boolean = isHttps && username.isEmpty() && password.isEmpty()

    private fun download(target: HttpUrl): ByteArray? =
        client.newCall(Request.Builder().url(target).build()).execute().use { response ->
            readPhoto(response)
        }

    private fun readPhoto(response: Response): ByteArray? {
        val body = response.body
        return if (response.isSuccessful && body != null && body.contentLength() <= MAX_PHOTO_BYTES) {
            body.byteStream().use { readLimitedBytes(it) }
        } else {
            null
        }
    }

    private fun readLimitedBytes(stream: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_PHOTO_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
        const val DOWNLOAD_TIMEOUT_SECONDS = 15L
    }
}
