package com.quistock.quistock.app.di

import com.quistock.quistock.data.remote.mock.MockRemoteBigNumbersRepository
import com.quistock.quistock.data.time.SystemClock
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.port.RemoteBigNumbersRepository
import com.quistock.quistock.domain.usecase.LegacyLoginUseCase
import com.quistock.quistock.domain.usecase.RefreshBigNumbersUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    singleOf(::SystemClock) { bind<Clock>() }

    singleOf(::MockRemoteBigNumbersRepository) { bind<RemoteBigNumbersRepository>() }

    factoryOf(::LegacyLoginUseCase)
    factoryOf(::SendMessageToChatbotUseCase)
    factoryOf(::RefreshBigNumbersUseCase)
}
