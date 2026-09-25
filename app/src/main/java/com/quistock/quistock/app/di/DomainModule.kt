package com.quistock.quistock.app.di

import com.quistock.quistock.domain.usecase.LegacyLoginUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::LegacyLoginUseCase)
    factoryOf(::SendMessageToChatbotUseCase)
}
