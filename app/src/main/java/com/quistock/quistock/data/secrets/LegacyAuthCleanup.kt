package com.quistock.quistock.data.secrets

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

internal val Context.legacyAuthDataStore by preferencesDataStore(name = "secrets")

/** Delete the pre-cookie credential without decrypting it or inventing HTTP metadata. */
internal suspend fun removeLegacyAuthCredential(context: Context) {
    context.applicationContext.legacyAuthDataStore.edit { it.remove(stringPreferencesKey("refresh_token")) }
}
