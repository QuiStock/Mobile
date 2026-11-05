package com.quistock.quistock.data.remote.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotRequestDTO(
    @SerialName("message") val message: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("is_resuming_conversation") val isResumingConversation: Boolean,
    @SerialName("product_card") val productCard: ChatbotProductCardDTO? = null,
)

@Serializable
data class ChatbotProductCardDTO(
    @SerialName("product_name") val productName: String,
    @SerialName("category_name") val categoryName: String,
)

@Serializable
data class ChatbotResponseDTO(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("response") val response: String,
    @SerialName("status") val status: String,
)

@Serializable
data class ChatbotConversationListDTO(@SerialName("conversations") val conversations: List<ChatbotConversationDTO>)

@Serializable
data class ChatbotConversationDTO(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ChatbotConversationHistoryDTO(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("status") val status: String,
    @SerialName("messages") val messages: List<ChatbotHistoryMessageDTO>,
)

@Serializable
data class ChatbotHistoryMessageDTO(
    @SerialName("message_id") val messageId: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ChatbotEndDTO(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("request_id") val requestId: String,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("status") val status: String,
    @SerialName("summary_status") val summaryStatus: String? = null,
)
