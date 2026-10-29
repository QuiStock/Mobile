package com.quistock.quistock.data.remote.core

import com.quistock.quistock.data.remote.core.InterferenceMapper.text
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionFailure
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.InterferenceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import retrofit2.Response
import java.net.HttpURLConnection

class RetrofitInterferenceRepository(private val api: InterferenceApi) : InterferenceRepository {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun list(storeId: Long, type: ActionType?, expected: SessionSnapshot): ActionListResult = try {
        val response = api.list(storeId, type?.name, expected)
        response.errorBody()?.close()
        require(response.code() == HttpURLConnection.HTTP_OK)
        val actions = requireNotNull(response.body()).map { InterferenceMapper.action(it.jsonObject) }
        require(actions.all { it.storeId == storeId && (type == null || it.type == type) })
        require(actions.map { it.id }.distinct().size == actions.size)
        ActionListResult.Loaded(actions)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ActionListResult.Failed
    }

    override suspend fun generate(
        product: AlertProduct,
        storeId: Long,
        type: ActionType,
        draft: ActionDraft,
        expected: SessionSnapshot,
    ): ActionResult = write {
        val flowId = requireNotNull(product.analysisId)
        val response = api.generate(InterferenceMapper.request(flowId, type, draft), expected)
        failure(response)?.let { return@write it }
        require(response.code() == HttpURLConnection.HTTP_CREATED)
        val body = requireNotNull(response.body())
        require(body.text("flow_id") == flowId)
        val actions = body["generated_actions"] as? JsonArray ?: error("Invalid action collection")
        if (actions.isEmpty()) return@write ActionResult.Empty
        val action = InterferenceMapper.action(actions.single().jsonObject, flowId)
        require(action.productId == product.id && action.storeId == storeId && action.type == type)
        ActionResult.Found(action)
    }

    override suspend fun forward(action: InterferenceAction, expected: SessionSnapshot): ActionResult = write {
        val response = api.forward(action.id, buildJsonObject { put("status", "SENT_TO_MANAGER") }, expected)
        failure(response)?.let { return@write it }
        require(response.code() == HttpURLConnection.HTTP_OK)
        val body = requireNotNull(response.body())
        require(body.text("id") == action.id && body.text("status") == ActionStatus.SENT_TO_MANAGER.name)
        ActionResult.Found(
            InterferenceAction(
                action.id, action.flowId, action.productId, action.storeId, action.type,
                ActionStatus.SENT_TO_MANAGER, action.values, action.referencePrice, action.promotionalPrice,
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun write(block: suspend () -> ActionResult): ActionResult = try {
        withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { block() } ?: ActionResult.Failed(ActionFailure.UNCERTAIN)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ActionResult.Failed(ActionFailure.UNCERTAIN)
    }

    private fun failure(response: Response<JsonObject>): ActionResult.Failed? {
        response.errorBody()?.close()
        val reason = when (response.code()) {
            HttpURLConnection.HTTP_BAD_REQUEST -> ActionFailure.INVALID
            HttpURLConnection.HTTP_UNAUTHORIZED -> ActionFailure.UNAUTHORIZED
            HttpURLConnection.HTTP_FORBIDDEN -> ActionFailure.FORBIDDEN
            HttpURLConnection.HTTP_NOT_FOUND -> ActionFailure.NOT_FOUND
            HttpURLConnection.HTTP_CONFLICT -> ActionFailure.CONFLICT
            in HttpURLConnection.HTTP_BAD_REQUEST until HttpURLConnection.HTTP_INTERNAL_ERROR -> ActionFailure.REJECTED
            else -> null
        }
        return reason?.let { ActionResult.Failed(it) }
    }

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 30_000L
    }
}
