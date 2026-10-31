package com.quistock.quistock.data.remote.internal.chatbot

import com.quistock.quistock.data.remote.internal.dto.ChatbotConversationHistoryDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotConversationListDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotEndDTO
import com.quistock.quistock.data.remote.internal.dto.ChatbotRequestDTO
import com.quistock.quistock.domain.model.SessionSnapshot
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Tag

interface ChatbotApi {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatbotRequestDTO, @Tag expected: SessionSnapshot): Response<ResponseBody>

    @GET("chat/conversations")
    suspend fun listEnded(@Tag expected: SessionSnapshot): Response<ChatbotConversationListDTO>

    @GET("chat/conversations/{conversation_id}/messages")
    suspend fun history(
        @Path("conversation_id") conversationId: String,
        @Tag expected: SessionSnapshot,
    ): Response<ChatbotConversationHistoryDTO>

    @POST("chat/conversations/{conversation_id}/end")
    suspend fun end(
        @Path("conversation_id") conversationId: String,
        @Tag expected: SessionSnapshot,
    ): Response<ChatbotEndDTO>
}
