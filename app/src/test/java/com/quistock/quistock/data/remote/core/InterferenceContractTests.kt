package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.StockFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal

class InterferenceContractTests {
    private val api = mockk<InterferenceApi>()
    private val repository = RetrofitInterferenceRepository(api)
    private val expected = SessionSnapshot(1, true)
    private val product = AlertProduct("product", "Product", "Category", BigDecimal.ONE, null, StockFlow.MEDIUM, "flow")
    private val draft = ActionDraft(quantity = "20", orderDate = "2026-11-05")

    @Test
    fun `requests include explicit type and only fields for that type`() {
        val order = InterferenceMapper.request("flow", ActionType.ORDER, draft)
        assertEquals(setOf("flow_id", "action_type", "quantity", "desired_order_date"), order.keys)
        val promo = InterferenceMapper.request(
            "flow", ActionType.PROMOTION,
            ActionDraft(discount = "15,25", start = "2026-11-05", end = "2026-11-06"),
        )
        assertEquals("15.25", promo["discount_percentage"].toString())
        assertFalse(promo.containsKey("promotion_duration_days"))
        assertFalse(promo.containsKey("quantity"))
    }

    @Test
    fun `generation validates identity and empty collection does not forward`() = runTest {
        coEvery { api.generate(any(), expected) } returns response("""{"flow_id":"flow","generated_actions":[]}""")
        assertEquals(ActionResult.Empty, repository.generate(product, 17, ActionType.ORDER, draft, expected))
        coVerify(exactly = 0) { api.forward(any(), any(), any()) }
        coEvery { api.generate(any(), expected) } returns response("""{"flow_id":"other","generated_actions":[]}""")
        val invalid = repository.generate(product, 17, ActionType.ORDER, draft, expected) as ActionResult.Failed
        assertEquals(ActionFailure.UNCERTAIN, invalid.reason)
    }

    @Test
    fun `each write has an independent thirty second deadline and never retries`() = runTest {
        coEvery { api.generate(any(), expected) } coAnswers {
            delay(29_000)
            response("""{"flow_id":"flow","generated_actions":[$actionJson]}""")
        }
        val generated = repository.generate(product, 17, ActionType.ORDER, draft, expected) as ActionResult.Found
        coEvery { api.forward("action", any(), expected) } coAnswers {
            delay(29_000)
            Response.success(Json.parseToJsonElement("""{"id":"action","status":"SENT_TO_MANAGER"}""").jsonObject)
        }
        val sent = repository.forward(generated.action, expected) as ActionResult.Found
        assertEquals(ActionStatus.SENT_TO_MANAGER, sent.action.status)
        assertEquals(draft, sent.action.values)
        coEvery { api.forward("action", any(), expected) } coAnswers { delay(30_001); error("Unreachable") }
        assertEquals(ActionFailure.UNCERTAIN, (repository.forward(generated.action, expected) as ActionResult.Failed).reason)
        coVerify(exactly = 1) { api.generate(any(), expected) }
    }

    @Test
    fun `disconnect after write and incompatible action body remain uncertain`() = runTest {
        coEvery { api.generate(any(), expected) } throws IOException()
        assertEquals(ActionFailure.UNCERTAIN, (repository.generate(product, 17, ActionType.ORDER, draft, expected)
            as ActionResult.Failed).reason)
        coEvery { api.generate(any(), expected) } returns response(
            """{"flow_id":"flow","generated_actions":[${actionJson.replace("product", "other")}]}""",
        )
        assertTrue(repository.generate(product, 17, ActionType.ORDER, draft, expected) is ActionResult.Failed)
    }

    private fun response(body: String): Response<JsonObject> =
        Response.success(201, Json.parseToJsonElement(body).jsonObject)

    private val actionJson = """
        {"id":"action","action_type":"ORDER","status":"GENERATED","product_id":"product","store_id":17,
        "quantity":20,"desired_order_date":"2026-11-05"}
    """.trimIndent()
}
