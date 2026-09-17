package com.quistock.quistock.data.remote.internal.chatbot

import com.quistock.quistock.data.remote.internal.mapper.toDomain
import com.quistock.quistock.data.remote.internal.mapper.toDto
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.port.ChatbotRepository

class RetrofitChatbotRepository(private val chatbotApi: ChatbotApi) : ChatbotRepository {
    override suspend fun sendMessage(request: ChatbotRequest): ChatbotResponse {
        val requestDto = request.toDto()
        val response = chatbotApi.sendMessage(requestDto)
        return response.toDomain()
    }
}
