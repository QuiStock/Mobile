package com.quistock.quistock.app.di

import com.quistock.quistock.data.notifications.DataStoreAlertMemoryStore
import com.quistock.quistock.data.notifications.WorkManagerAlertScheduler
import com.quistock.quistock.data.preferences.AndroidNotificationPromptPreferences
import com.quistock.quistock.domain.port.AlertMemoryStore
import com.quistock.quistock.domain.port.AlertNotifier
import com.quistock.quistock.domain.port.AlertScheduler
import com.quistock.quistock.domain.port.NotificationPromptPreferences
import com.quistock.quistock.domain.port.RequestOwnership
import com.quistock.quistock.domain.usecase.RoleAlertsUseCase
import com.quistock.quistock.presentation.notifications.AndroidAlertNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val roleAlertsModule = module {
    single<AlertMemoryStore> { DataStoreAlertMemoryStore(androidContext()) }
    single<AlertNotifier> { AndroidAlertNotifier(androidContext()) }
    single<AlertScheduler> { WorkManagerAlertScheduler(androidContext()) }
    single { RoleAlertsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single<RequestOwnership> { get<RoleAlertsUseCase>() }
    single<NotificationPromptPreferences> { AndroidNotificationPromptPreferences(androidContext()) }
}
