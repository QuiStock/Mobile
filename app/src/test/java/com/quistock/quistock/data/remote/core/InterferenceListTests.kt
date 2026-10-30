package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.StockFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal

class InterferenceListTests {
    private val api = mockk<InterferenceApi>()
    private val repository = RetrofitInterferenceRepository(api)
    private val expected = SessionSnapshot(1, true)
    private val order = """
        {"id":"order","flow_id":"flow","product_id":"product","store_id":17,
        "action_type":"ORDER","status":"GENERATED","quantity":2,"desired_order_date":null}
    """.trimIndent()
    private val monitor = """
        {"id":"monitor","flow_id":"monitor-flow","product_id":"other","store_id":17,
        "action_type":"MONITOR","status":"GENERATED","quantity":null,"desired_order_date":null}
    """.trimIndent()
    private val promotion = """
        {"id":"promo","flow_id":"promo-flow","product_id":"promo-product","store_id":17,
        "action_type":"PROMOTION","status":"SENT_TO_MANAGER","quantity":null,
        "promotion_valid_from":"2026-10-14","promotion_valid_until":"2026-10-28",
        "discount_percentage":18,"reference_sale_price":48.5,"promotional_price":39.77}
    """.trimIndent()

    @Test
    fun `mixed HTTP 200 list retains orders without dates and promotions but excludes monitor`() = runTest {
        val rows = listOf(
            monitor,
            order.replace("GENERATED", "REJECTED"),
            monitor.replace("monitor", "second-monitor"),
            promotion,
            order.replace("\"id\":\"order\"", "\"id\":\"approved-order\"").replace("GENERATED", "APPROVED"),
            monitor.replace("monitor", "third-monitor"),
            promotion.replace("\"id\":\"promo\"", "\"id\":\"generated-promo\"").replace("SENT_TO_MANAGER", "GENERATED"),
            order.replace("\"id\":\"order\"", "\"id\":\"pending-order\""),
        )
        coEvery { api.list(17, null, expected) } returns response(rows.joinToString(","))
        val result = repository.list(17, null, expected) as ActionListResult.Loaded
        assertEquals(5, result.actions.size)
        assertEquals(3, result.actions.count { it.type == ActionType.ORDER })
        assertEquals(2, result.actions.count { it.type == ActionType.PROMOTION })
        val pending = result.actions.single { it.id == "pending-order" }
        assertEquals("2", pending.values.quantity)
        assertEquals("", pending.values.orderDate)
        assertTrue(pending.status.canForward)
        assertEquals(ActionStatus.REJECTED, result.actions.first().status)
    }

    @Test
    fun `missing persisted fields stay unknown while generation response stays strict`() = runTest {
        coEvery { api.list(17, null, expected) } returns response(
            order.replace("\"flow\"", "null").replace("\"quantity\":2", "\"quantity\":null"),
        )
        val result = repository.list(17, null, expected) as ActionListResult.Loaded
        assertNull(result.actions.single().flowId)
        assertEquals("", result.actions.single().values.quantity)
        coEvery { api.generate(any(), expected) } returns Response.success(
            201,
            Json.parseToJsonElement("""{"flow_id":"flow","generated_actions":[$order]}""").jsonObject,
        )
        val product = AlertProduct("product", "Product", "Category", BigDecimal.ONE, null, StockFlow.HIGH, "flow")
        val generated = repository.generate(
            product,
            17,
            ActionType.ORDER,
            ActionDraft(quantity = "2", orderDate = "2026-11-05"),
            expected,
        ) as ActionResult.Failed
        assertEquals(ActionFailure.UNCERTAIN, generated.reason)
    }

    @Test
    fun `foreign stores duplicates unknown types and invalid nonnull values still fail reading`() = runTest {
        listOf(
            order.replace(":17", ":18"),
            "$order,$order",
            order.replace("ORDER", "UNKNOWN"),
            order.replace("\"quantity\":2", "\"quantity\":\"invalid\""),
            order.replace("\"quantity\":2", "\"quantity\":-1"),
            order.replace("\"desired_order_date\":null", "\"desired_order_date\":\"not-a-date\""),
            monitor.replace(":17", ":18"),
        ).forEach { body ->
            coEvery { api.list(17, null, expected) } returns response(body)
            assertEquals(ActionListResult.Failed, repository.list(17, null, expected))
        }
    }

    @Test
    fun `nullable promotion values remain unknown but malformed present values fail`() = runTest {
        val incomplete = """{"id":"promo","flow_id":"flow","product_id":"product","store_id":17,
            "action_type":"PROMOTION","status":"GENERATED","discount_percentage":null,
            "promotion_valid_from":null,"promotion_valid_until":null,
            "reference_sale_price":null,"promotional_price":null}"""
        coEvery { api.list(17, null, expected) } returns response(incomplete)
        val result = repository.list(17, null, expected) as ActionListResult.Loaded
        assertEquals(ActionDraft(), result.actions.single().values)
        assertNull(result.actions.single().referencePrice)
        listOf(
            incomplete.replace("\"discount_percentage\":null", "\"discount_percentage\":100.01"),
            incomplete.replace("\"discount_percentage\":null", "\"discount_percentage\":1.001"),
            promotion.replace("2026-10-28", "2026-10-13"),
            incomplete.replace("\"reference_sale_price\":null", "\"reference_sale_price\":-1"),
        ).forEach { body ->
            coEvery { api.list(17, null, expected) } returns response(body)
            assertEquals(ActionListResult.Failed, repository.list(17, null, expected))
        }
    }

    @Test
    fun `filtered order list keeps pending legacy data and rejects unexpected monitor`() = runTest {
        coEvery { api.list(17, "ORDER", expected) } returns response(order)
        val result = repository.list(17, ActionType.ORDER, expected) as ActionListResult.Loaded
        val action = result.actions.single()
        assertTrue(action.status.pending)
        coEvery { api.forward("order", any(), expected) } returns Response.success(
            Json.parseToJsonElement("""{"id":"order","status":"SENT_TO_MANAGER"}""").jsonObject,
        )
        val forwarded = repository.forward(action, expected) as ActionResult.Found
        assertEquals(ActionStatus.SENT_TO_MANAGER, forwarded.action.status)
        assertEquals("", forwarded.action.values.orderDate)
        coVerify(exactly = 0) { api.generate(any(), any()) }
        coEvery { api.list(17, "ORDER", expected) } returns response(monitor)
        assertEquals(ActionListResult.Failed, repository.list(17, ActionType.ORDER, expected))
        coEvery { api.list(17, null, expected) } returns response(monitor)
        assertTrue((repository.list(17, null, expected) as ActionListResult.Loaded).actions.isEmpty())
    }

    private fun response(rows: String) = Response.success(Json.parseToJsonElement("[$rows]").jsonArray)
}
