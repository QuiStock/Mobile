package com.quistock.quistock.app.di

import com.quistock.quistock.data.observability.crashlytics.CrashlyticsErrorReporter
import com.quistock.quistock.data.observability.logcat.LogcatLogger
import com.quistock.quistock.domain.port.ErrorReporter
import com.quistock.quistock.domain.port.Logger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val observabilityModule = module {
    singleOf(::LogcatLogger)
    single<Logger> { get<LogcatLogger>() }
    single<ErrorReporter> { CrashlyticsErrorReporter(get()) }
}
