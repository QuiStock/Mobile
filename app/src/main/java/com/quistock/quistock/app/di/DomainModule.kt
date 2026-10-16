package com.quistock.quistock.app.di

import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    single { SessionUseCase(get(), get()) }
    factoryOf(::LoginUseCase)
    factoryOf(::SendMessageToChatbotUseCase)
}
