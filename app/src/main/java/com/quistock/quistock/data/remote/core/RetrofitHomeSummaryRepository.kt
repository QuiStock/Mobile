package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionFailure
import com.quistock.quistock.domain.model.SummaryFailure
import com.quistock.quistock.domain.port.HomeSummaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

interface DashboardApi {
    @GET("dashboard/summary")
    suspend fun read(@Query("store_id") storeId: Long): Response<ResponseBody>
}

class RetrofitHomeSummaryRepository(private val api: DashboardApi) : HomeSummaryRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(storeId: Long): HomeSummaryResult = try {
        val response = api.read(storeId)
        response.body()?.use { body ->
            if (response.code() == 200) {
                HomeSummaryResult.Success(DashboardSummaryMapper.parse(body.string()))
            } else {
                HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
            }
        } ?: response.errorBody().use { error ->
            val text = error?.string().orEmpty()
            when {
                response.code() == 403 -> {
                    val code = runCatching {
                        Json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
                    }.getOrNull()
                    HomeSummaryResult.Failure(
                        if (code ==
                            "NO_ACTIVE_STORE"
                        ) {
                            SummaryFailure.NO_STORE
                        } else {
                            SummaryFailure.FORBIDDEN
                        },
                    )
                }

                response.code() == 429 -> HomeSummaryResult.Failure(
                    SummaryFailure.RATE_LIMIT,
                    retryAt(response.headers()["Retry-After"]),
                )

                response.code() >= 500 -> HomeSummaryResult.Failure(SummaryFailure.SERVER)

                else -> HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: SessionException) {
        HomeSummaryResult.Failure(
            when (error.reason) {
                SessionFailure.NETWORK -> SummaryFailure.NETWORK
                SessionFailure.TIMEOUT -> SummaryFailure.TIMEOUT
                SessionFailure.SERVER -> SummaryFailure.SERVER
                SessionFailure.RATE_LIMIT -> SummaryFailure.RATE_LIMIT
                else -> SummaryFailure.CONTRACT
            },
        )
    } catch (_: SocketTimeoutException) {
        HomeSummaryResult.Failure(SummaryFailure.TIMEOUT)
    } catch (_: IOException) {
        HomeSummaryResult.Failure(SummaryFailure.NETWORK)
    } catch (_: Exception) {
        HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
    }

    private fun retryAt(value: String?): Long {
        val now = System.currentTimeMillis()
        val seconds = value?.toLongOrNull()?.takeIf { it >= 0 }
        return if (seconds != null) {
            now + seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000) * 1000
        } else {
            runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
                .getOrDefault(now).coerceAtLeast(now)
        }
    }
}
