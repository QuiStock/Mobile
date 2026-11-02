package com.quistock.quistock.app.di

import com.quistock.quistock.data.location.AndroidDeviceLocationProvider
import com.quistock.quistock.data.preferences.AndroidLocationPromptPreferences
import com.quistock.quistock.domain.port.DeviceLocationProvider
import com.quistock.quistock.domain.port.LocationPromptPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val locationModule = module {
    single<DeviceLocationProvider> { AndroidDeviceLocationProvider(androidContext()) }
    single<LocationPromptPreferences> { AndroidLocationPromptPreferences(androidContext()) }
}
