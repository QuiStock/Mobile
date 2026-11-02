package com.quistock.quistock.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.quistock.quistock.domain.port.LocationPromptPreferences

class AndroidLocationPromptPreferences(context: Context) : LocationPromptPreferences {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun wasAsked(): Boolean = preferences.getBoolean(ASKED_KEY, false)

    override fun markAsked() = preferences.edit { putBoolean(ASKED_KEY, true) }

    private companion object {
        const val FILE_NAME = "quistock_location_prompt"
        const val ASKED_KEY = "location_permission_asked"
    }
}
