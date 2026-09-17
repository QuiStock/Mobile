package com.quistock.quistock.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.quistock.quistock.R
import com.quistock.quistock.domain.port.UserPreferences

class AndroidUserPreferences(private val context: Context) : UserPreferences {
    private val userIdKey = context.getString(R.string.shared_preferences_user_id_key)
    private val preferences = context.getSharedPreferences(
        context.getString(R.string.shared_preferences_name),
        Context.MODE_PRIVATE,
    )

    override fun saveUserId(userId: String) {
        preferences.edit {
            putString(userIdKey, userId)
        }
    }

    override fun getUserId(): String? = preferences.getString(userIdKey, null)
}
