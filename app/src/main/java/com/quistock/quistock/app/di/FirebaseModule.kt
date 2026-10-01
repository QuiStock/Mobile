package com.quistock.quistock.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.dsl.module

val firebaseSdkModule = module {
    single { FirebaseAuth.getInstance() }
    single { FirebaseCrashlytics.getInstance() }
}
