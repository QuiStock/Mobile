package com.quistock.quistock.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.quistock.quistock.domain.port.NotificationPromptPreferences

class AndroidNotificationPromptPreferences(context: Context) : NotificationPromptPreferences {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun wasAsked(): Boolean = preferences.getBoolean(ASKED_KEY, false)

    override fun markAsked() = preferences.edit { putBoolean(ASKED_KEY, true) }

    private companion object {
        const val FILE_NAME = "quistock_notification_prompt"
        const val ASKED_KEY = "notification_permission_asked"
    }
}
