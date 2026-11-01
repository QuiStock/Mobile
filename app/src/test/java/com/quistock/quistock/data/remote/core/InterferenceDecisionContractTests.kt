package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ActionDecision
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.SessionSnapshot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class InterferenceDecisionContractTests {
    private val api = mockk<InterferenceApi>()
    private val repository = RetrofitInterferenceRepository(api)
    private val expected = SessionSnapshot(1, true)
    private val action = InterferenceAction(
        "action",
        "flow",
        "product",
        17,
        ActionType.ORDER,
        ActionStatus.SENT_TO_MANAGER,
        ActionDraft(quantity = "20", orderDate = "2026-11-05"),
    )

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `approval sends only the status and author or instant are never sent`() = runTest {
        val body = slot<JsonObject>()
        coEvery { api.decide("action", capture(body), expected) } returns
            Response.success(200, json("""{"id":"action","status":"APPROVED"}"""))
        val result = repository.decide(action, ActionDecision.APPROVE, null, expected) as ActionResult.Found
        assertEquals(setOf("status"), body.captured.keys)
        assertEquals(JsonPrimitive("APPROVED"), body.captured["status"])
        assertEquals(ActionStatus.APPROVED, result.action.status)
    }

    @Test
    fun `rejection sends the justification and keeps it for the locked view`() = runTest {
        val body = slot<JsonObject>()
        coEvery { api.decide("action", capture(body), expected) } returns
            Response.success(200, json("""{"id":"action","status":"REJECTED"}"""))
        val result = repository.decide(action, ActionDecision.REJECT, "Sem verba", expected) as ActionResult.Found
        assertEquals(JsonPrimitive("Sem verba"), body.captured["justification"])
        assertEquals(ActionStatus.REJECTED, result.action.status)
        assertEquals("Sem verba", result.action.decisionJustification)
    }

    @Test
    fun `unconfirmed transition is uncertain and refusals map to recoverable failures`() = runTest {
        coEvery { api.decide(any(), any(), expected) } returns
            Response.success(200, json("""{"id":"action","status":"SENT_TO_MANAGER"}"""))
        val mismatch = repository.decide(action, ActionDecision.APPROVE, null, expected) as ActionResult.Failed
        assertEquals(ActionFailure.UNCERTAIN, mismatch.reason)
        mapOf(
            400 to ActionFailure.INVALID,
            403 to ActionFailure.FORBIDDEN,
            404 to ActionFailure.NOT_FOUND,
            409 to ActionFailure.CONFLICT,
            500 to ActionFailure.UNCERTAIN,
        ).forEach { (code, reason) ->
            coEvery { api.decide(any(), any(), expected) } returns Response.error(code, "{}".toResponseBody())
            val result = repository.decide(action, ActionDecision.REJECT, "x", expected) as ActionResult.Failed
            assertEquals(reason, result.reason)
        }
    }

    @Test
    fun `list rows expose product name and justifications, treating blank as unknown`() {
        val row = json(
            """
            {"id":"a","flow_id":"f","product_id":"p","store_id":17,"action_type":"ORDER",
            "status":"REJECTED","quantity":5,"desired_order_date":"2026-11-05",
            "product_name":" Leite ","justification":"","decision_justification":"Sem verba"}
            """.trimIndent(),
        )
        val action = requireNotNull(InterferenceListMapper.action(row))
        assertEquals("Leite", action.productName)
        assertNull(action.justification)
        assertEquals("Sem verba", action.decisionJustification)
    }
}
