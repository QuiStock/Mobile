package com.quistock.quistock.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class AppModulesTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `internal dependency graph should be valid`() {
        appInternalModule.verify(
            extraTypes = listOf(
                FirebaseAuth::class,
                FirebaseCrashlytics::class,
            ),
        )
    }
}
