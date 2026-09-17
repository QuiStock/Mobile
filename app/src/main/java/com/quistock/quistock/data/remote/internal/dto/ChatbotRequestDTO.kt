package com.quistock.quistock.data.remote.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotRequestDTO(@SerialName("user_id") val userId: String, @SerialName("message") val message: String)
