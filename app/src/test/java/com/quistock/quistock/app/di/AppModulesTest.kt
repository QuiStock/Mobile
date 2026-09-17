package com.quistock.quistock.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.quistock.quistock.data.remote.internal.chatbot.ChatbotApi
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class AppModulesTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `internal dependency graph should be valid`() {
        appInternalModule.verify(
            extraTypes = listOf(
                Context::class,
                FirebaseAuth::class,
                FirebaseCrashlytics::class,
                ChatbotApi::class,
            ),
        )
    }
}
