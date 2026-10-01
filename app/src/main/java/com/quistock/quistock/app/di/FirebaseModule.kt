package com.quistock.quistock.app.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.dsl.module

val firebaseSdkModule = module {
    single { FirebaseCrashlytics.getInstance() }
}
