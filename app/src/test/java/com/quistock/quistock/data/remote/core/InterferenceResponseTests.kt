package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.StockFlow
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class InterferenceResponseTests {
    private val api = mockk<InterferenceApi>()
    private val repository = RetrofitInterferenceRepository(api)
    private val expected = SessionSnapshot(1, true)
    private val product = AlertProduct(
        "product",
        "Product",
        "Category",
        java.math.BigDecimal.ONE,
        null,
        StockFlow.MEDIUM,
        "flow",
    )
    private val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")

    @Test
    fun `known HTTP refusals map to recoverable states without exposing technical messages`() = runTest {
        val cases = mapOf(
            400 to ActionFailure.INVALID,
            401 to ActionFailure.UNAUTHORIZED,
            403 to ActionFailure.FORBIDDEN,
            404 to ActionFailure.NOT_FOUND,
            409 to ActionFailure.CONFLICT,
            500 to ActionFailure.UNCERTAIN,
        )
        cases.forEach { (code, reason) ->
            coEvery { api.generate(any(), expected) } returns Response.error(code, "{}".toResponseBody())
            val result = repository.generate(product, 17, ActionType.ORDER, draft, expected) as ActionResult.Failed
            assertEquals(reason, result.reason)
        }
    }

    @Test
    fun `persisted parameters differing from request require review before forwarding`() = runTest {
        val body = Json.parseToJsonElement(
            """
            {"flow_id":"flow","generated_actions":[{"id":"action","action_type":"ORDER",
            "status":"GENERATED","product_id":"product","store_id":17,
            "quantity":99,"desired_order_date":"2026-11-05"}]}
            """.trimIndent(),
        ).jsonObject
        coEvery { api.generate(any(), expected) } returns Response.success(201, body)
        val result = repository.generate(product, 17, ActionType.ORDER, draft, expected) as ActionResult.Found
        assertTrue(result.existing)
        assertEquals("99", result.action.values.quantity)
    }

    @Test(expected = java.io.IOException::class)
    fun `network follow-up cannot repeat an action write`() {
        val attempt = InterferenceWriteAttempt()
        val chain = mockk<okhttp3.Interceptor.Chain>()
        val request = Request.Builder().url("https://core.example.test/actions/generate")
            .post("{}".toRequestBody()).build()
        io.mockk.every { chain.request() } returns request
        io.mockk.every { chain.proceed(any()) } returns okhttp3.Response.Builder().request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1).code(503).message("Fixture").body("".toResponseBody()).build()
        attempt.send(chain).close()
        attempt.send(chain)
    }
}
