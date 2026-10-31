package com.quistock.quistock.data.preferences

import android.content.Context
import com.quistock.quistock.domain.port.ChatbotConversationStore
import java.security.MessageDigest
import java.util.UUID

class AndroidChatbotConversationStore(context: Context) : ChatbotConversationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "chatbot_conversations",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun current(userId: String): String? = preferences.getString(key(userId), null)

    @Synchronized
    override fun getOrCreate(userId: String): String {
        require(userId.isNotBlank())
        val key = key(userId)
        return preferences.getString(key, null) ?: UUID.randomUUID().toString().also {
            check(preferences.edit().putString(key, it).commit()) { "Chat conversation id unavailable" }
        }
    }

    @Synchronized
    override fun setActive(userId: String, conversationId: String) {
        require(userId.isNotBlank() && conversationId.isNotBlank())
        check(preferences.edit().putString(key(userId), conversationId).commit()) { "Chat conversation id unavailable" }
    }

    @Synchronized
    override fun clear(userId: String) {
        check(preferences.edit().remove(key(userId)).commit()) { "Chat conversation id removal unavailable" }
    }

    private fun key(userId: String) =
        "conversation_${MessageDigest.getInstance("SHA-256").digest(userId.toByteArray()).joinToString("") {
            "%02x".format(it)
        }}"
}
