package com.quistock.quistock.app.di

import com.quistock.quistock.data.preferences.AndroidUserPreferences
import com.quistock.quistock.domain.port.UserPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val preferencesModule = module {
    single<UserPreferences> { AndroidUserPreferences(androidContext()) }
}
