package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.port.ProfilePhotoUploadRepository
import com.quistock.quistock.domain.port.ProfilePhotoUploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class CloudinaryPhotoRepository(private val cloud: String, private val preset: String) : ProfilePhotoUploadRepository {
    override val available: Boolean = cloud.matches(Regex("[A-Za-z0-9_-]+")) && preset.isNotBlank()
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .tag(ProfileWriteAttempt::class.java, ProfileWriteAttempt()).build()
            chain.proceed(request)
        }
        .addNetworkInterceptor { chain ->
            requireNotNull(chain.request().tag(ProfileWriteAttempt::class.java)).send(chain)
        }
        .build()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun upload(jpeg: ByteArray): ProfilePhotoUploadResult = withContext(Dispatchers.IO) {
        try {
            require(available)
            require(jpeg.size <= MAX_JPEG_BYTES)
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", preset)
                .addFormDataPart("public_id", UUID.randomUUID().toString())
                .addFormDataPart("file", "profile.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
                .build()
            val request = Request.Builder().url("https://api.cloudinary.com/v1_1/$cloud/image/upload")
                .post(body).build()
            send(request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ProfilePhotoUploadResult.Failed()
        }
    }

    private suspend fun send(request: Request): ProfilePhotoUploadResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                continuation.resume(ProfilePhotoUploadResult.Failed())
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    runCatching { readResponse(it) }.getOrElse { ProfilePhotoUploadResult.Failed() }
                }
                continuation.resume(result)
            }
        })
    }

    private fun readResponse(response: Response): ProfilePhotoUploadResult {
        val source = response.body?.source()
        source?.request(MAX_RESPONSE_BYTES + 1)
        val size = minOf(source?.buffer?.size ?: 0, MAX_RESPONSE_BYTES + 1)
        val text = source?.readByteArray(size) ?: byteArrayOf()
        val url = if (response.code != HttpURLConnection.HTTP_OK || text.size > MAX_RESPONSE_BYTES) {
            null
        } else {
            val fields = Json.parseToJsonElement(text.toString(Charsets.UTF_8)).jsonObject
            val sizeBytes = fields["bytes"]?.jsonPrimitive?.content?.toLongOrNull()
            val format = fields["format"]?.jsonPrimitive?.content
            val validSize = sizeBytes != null && sizeBytes in 1L..MAX_JPEG_BYTES.toLong()
            if (!validSize || format != "jpg") {
                null
            } else {
                validUrl(fields["secure_url"]?.jsonPrimitive?.content)
            }
        }
        return url?.let { ProfilePhotoUploadResult.Uploaded(it) } ?: ProfilePhotoUploadResult.Failed(
            retryAfterMillis = if (response.code == HTTP_TOO_MANY_REQUESTS) {
                retryAfterMillis(response.header("Retry-After"))
            } else {
                0
            },
        )
    }

    private fun validUrl(value: String?): String? {
        val url = value?.toHttpUrlOrNull()
        val safe = url?.isHttps == true && url.username.isEmpty() && url.password.isEmpty()
        return value?.takeIf { safe && it.length <= MAX_URL_LENGTH }
    }

    private companion object {
        const val MAX_JPEG_BYTES = 2 * 1024 * 1024
        const val MAX_RESPONSE_BYTES = 64 * 1024L
        const val MAX_URL_LENGTH = 500
        const val UPLOAD_TIMEOUT_SECONDS = 60L
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
