package com.quistock.quistock.domain.port

/** Remembers that the notification permission request was already shown after sign-in. */
interface NotificationPromptPreferences {
    fun wasAsked(): Boolean
    fun markAsked()
}
