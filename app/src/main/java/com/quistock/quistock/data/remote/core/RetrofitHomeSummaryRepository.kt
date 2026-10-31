package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.retryAfterAt
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
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

interface DashboardApi {
    @GET("dashboard/summary")
    suspend fun read(@Query("store_id") storeId: Long): Response<ResponseBody>
}

class RetrofitHomeSummaryRepository(private val api: DashboardApi) : HomeSummaryRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(storeId: Long): HomeSummaryResult = try {
        mapResponse(api.read(storeId))
    } catch (error: CancellationException) {
        throw error
    } catch (error: SessionException) {
        sessionFailure(error)
    } catch (_: SocketTimeoutException) {
        HomeSummaryResult.Failure(SummaryFailure.TIMEOUT)
    } catch (_: IOException) {
        HomeSummaryResult.Failure(SummaryFailure.NETWORK)
    } catch (_: Exception) {
        HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
    }

    private fun mapResponse(response: Response<ResponseBody>): HomeSummaryResult {
        val body = response.body()
        return when {
            response.code() == HttpURLConnection.HTTP_OK && body != null -> body.use {
                HomeSummaryResult.Success(DashboardSummaryMapper.parse(it.string()))
            }

            response.isSuccessful -> {
                body?.close()
                HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
            }

            else -> response.errorBody().use {
                httpFailure(response.code(), it?.string().orEmpty(), response.headers()["Retry-After"])
            }
        }
    }

    private fun httpFailure(status: Int, body: String, retryAfter: String?): HomeSummaryResult.Failure = when {
        status == HttpURLConnection.HTTP_FORBIDDEN -> {
            val code = runCatching {
                Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
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

        status == HTTP_TOO_MANY_REQUESTS ->
            HomeSummaryResult.Failure(SummaryFailure.RATE_LIMIT, retryAfterAt(retryAfter, System.currentTimeMillis()))

        status >= HttpURLConnection.HTTP_INTERNAL_ERROR -> HomeSummaryResult.Failure(SummaryFailure.SERVER)

        else -> HomeSummaryResult.Failure(SummaryFailure.CONTRACT)
    }

    private fun sessionFailure(error: SessionException) = HomeSummaryResult.Failure(
        when (error.reason) {
            SessionFailure.NETWORK -> SummaryFailure.NETWORK
            SessionFailure.TIMEOUT -> SummaryFailure.TIMEOUT
            SessionFailure.SERVER -> SummaryFailure.SERVER
            SessionFailure.RATE_LIMIT -> SummaryFailure.RATE_LIMIT
            else -> SummaryFailure.CONTRACT
        },
    )
}

private const val HTTP_TOO_MANY_REQUESTS = 429
