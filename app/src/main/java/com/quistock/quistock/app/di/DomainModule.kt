package com.quistock.quistock.app.di

import com.quistock.quistock.data.time.SystemClock
import com.quistock.quistock.domain.time.Clock
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    single<Clock> { SystemClock() }

    factoryOf(::LoginUseCase)
    factoryOf(::SendMessageToChatbotUseCase)
}
