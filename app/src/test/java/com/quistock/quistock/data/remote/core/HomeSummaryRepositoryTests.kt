package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.SummaryFailure
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit

class HomeSummaryRepositoryTests {
    @Test fun queryPreservesGatewayPrefixAndUsesTheGivenStore() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"near_expiry_products":4,"stockout_products":2,"overstock_products":3,"active_actions":5}""",
                ),
            )
            val api = Retrofit.Builder().baseUrl(server.url("/gateway/api/")).client(OkHttpClient()).build()
                .create(DashboardApi::class.java)
            assertTrue(RetrofitHomeSummaryRepository(api).read(51) is HomeSummaryResult.Success)
            assertEquals("/gateway/api/dashboard/summary?store_id=51", server.takeRequest().path)
        }
    }

    @Test fun terminalStoreLossIsDifferentFromAnOrdinaryForbiddenAnd429HasADeadline() = runBlocking {
        for ((status, body, reason) in listOf(
            Triple(403, """{"error":"NO_ACTIVE_STORE"}""", SummaryFailure.NO_STORE),
            Triple(403, """{"error":"FORBIDDEN"}""", SummaryFailure.FORBIDDEN),
            Triple(503, "{}", SummaryFailure.SERVER),
            Triple(429, "{}", SummaryFailure.RATE_LIMIT),
            Triple(404, "{}", SummaryFailure.CONTRACT),
        )) {
            val api = object : DashboardApi {
                override suspend fun read(storeId: Long) =
                    Response.error<okhttp3.ResponseBody>(status, body.toResponseBody())
            }
            assertEquals(reason, (RetrofitHomeSummaryRepository(api).read(51) as HomeSummaryResult.Failure).reason)
        }
    }

    @Test fun non200SuccessAndInvalidBodyAreNotValidZeroCounts() = runBlocking {
        val api = object : DashboardApi {
            override suspend fun read(storeId: Long) = Response.success(201, "{}".toResponseBody())
        }
        assertEquals(
            SummaryFailure.CONTRACT,
            (RetrofitHomeSummaryRepository(api).read(51) as HomeSummaryResult.Failure).reason,
        )
    }
}
