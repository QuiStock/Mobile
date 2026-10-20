package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.port.ProfilePhotoRepository
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpProfilePhotoRepository : ProfilePhotoRepository {
    // No Core interceptor, cookies, redirects, diagnostics, or disk cache on external photo requests.
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS).build()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val target = url.toHttpUrlOrNull() ?: return@withContext null
            if (!target.isHttps || target.username.isNotEmpty() || target.password.isNotEmpty()) {
                return@withContext null
            }
            client.newCall(Request.Builder().url(target).build()).execute().use response@ { response ->
                if (!response.isSuccessful) return@response null
                val body = response.body ?: return@response null
                if (body.contentLength() > MAX_PHOTO_BYTES) return@response null
                body.byteStream().use photo@ { stream ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_PHOTO_BYTES) return@photo null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
    }
}
