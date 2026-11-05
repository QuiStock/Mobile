package com.quistock.quistock.data.remote.internal.chatbot

import com.quistock.quistock.data.remote.internal.dto.ChatbotConversationHistoryDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotConversationListDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotEndDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotProductCardDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotRequestDTO
import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.domain.model.ChatbotConversationHistory
import com.quistock.quistock.domain.model.ChatbotHistoryMessage
import com.quistock.quistock.domain.model.ChatbotHttpException
import com.quistock.quistock.domain.model.ChatbotReply
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.ChatbotRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

class RetrofitChatbotRepository(private val api: ChatbotApi, private val json: Json) : ChatbotRepository {
    override suspend fun sendMessage(request: ChatbotRequest, expected: SessionSnapshot): ChatbotReply {
        val dto = request.productCard?.let {
            ChatbotProductCardDTO(it.productName.take(200), it.categoryName.take(120))
        }
        val response = api.sendMessage(
            ChatbotRequestDTO(request.message, request.conversationId, request.isResumingConversation, dto),
            expected,
        )
        if (!response.isSuccessful && response.code() != HTTP_UNPROCESSABLE_ENTITY) {
            response.errorBody()?.close()
            throw ChatbotHttpException(response.code())
        }
        val raw = if (response.isSuccessful) {
            response.body()?.use { it.string() }
        } else {
            response.errorBody()?.use { it.string() }
        } ?: throw if (response.code() == HTTP_UNPROCESSABLE_ENTITY) {
            ChatbotHttpException(response.code())
        } else {
            IOException("Empty chatbot response")
        }
        val result = json.decodeFromString<com.quistock.quistock.data.remote.internal.dto.ChatbotResponseDTO>(raw)
        return ChatbotReply(result.conversationId, result.response, result.status)
    }

    override suspend fun listEnded(expected: SessionSnapshot): List<ChatbotConversation> = api.listEnded(expected)
        .bodyOrThrow<ChatbotConversationListDTO>().conversations.map {
            ChatbotConversation(it.conversationId, it.title.orEmpty(), it.updatedAt)
        }

    override suspend fun history(conversationId: String, expected: SessionSnapshot): ChatbotConversationHistory {
        val result = api.history(conversationId, expected).bodyOrThrow<ChatbotConversationHistoryDTO>()
        return ChatbotConversationHistory(
            result.conversationId,
            result.title.orEmpty(),
            result.status,
            result.messages.map { ChatbotHistoryMessage(it.messageId, it.role, it.content, it.createdAt) },
        )
    }

    override suspend fun end(conversationId: String, expected: SessionSnapshot) {
        api.end(conversationId, expected).bodyOrThrow<ChatbotEndDTO>()
    }
}

private fun <T> Response<T>.bodyOrThrow(): T = if (isSuccessful) {
    body() ?: throw IOException("Empty chatbot response")
} else {
    errorBody()?.close()
    throw ChatbotHttpException(code())
}

private const val HTTP_UNPROCESSABLE_ENTITY = 422
