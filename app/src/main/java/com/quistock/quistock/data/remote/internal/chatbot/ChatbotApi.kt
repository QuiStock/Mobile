package com.quistock.quistock.data.remote.internal.chatbot

import com.quistock.quistock.data.remote.internal.dto.ChatbotRequestDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotResponseDTO
import retrofit2.http.Body
import retrofit2.http.POST

interface ChatbotApi {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatbotRequestDTO): ChatbotResponseDTO
}
