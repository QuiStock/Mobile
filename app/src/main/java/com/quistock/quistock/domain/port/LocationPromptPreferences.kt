package com.quistock.quistock.domain.port

/** Remembers that the first-launch location permission request was already shown. */
interface LocationPromptPreferences {
    fun wasAsked(): Boolean
    fun markAsked()
}
