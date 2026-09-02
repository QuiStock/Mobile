package com.quistock.quistock.app.di

import com.quistock.quistock.data.observability.CompositeErrorReporter
import com.quistock.quistock.data.observability.crashlytics.CrashlyticsErrorReporter
import com.quistock.quistock.data.observability.logcat.LogcatErrorReporter
import com.quistock.quistock.domain.port.ErrorReporter
import org.koin.dsl.module

val observabilityModule = module {
    single<ErrorReporter> {
        CompositeErrorReporter(
            reporters = listOf(
                LogcatErrorReporter(),
                CrashlyticsErrorReporter(get()),
            ),
        )
    }
}
