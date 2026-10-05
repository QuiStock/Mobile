package com.quistock.quistock.app.di

import com.quistock.quistock.data.remote.mock.MockAuthRepository
import com.quistock.quistock.data.remote.mock.MockRemoteBigNumbersRepository
import com.quistock.quistock.data.time.SystemClock
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.port.RemoteBigNumbersRepository
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.RefreshBigNumbersUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    singleOf(::SystemClock) { bind<Clock>() }

    singleOf(::MockRemoteBigNumbersRepository) { bind<RemoteBigNumbersRepository>() }
    singleOf(::MockAuthRepository) { bind<AuthRepository>() }

    singleOf(::SessionUseCase)
    factoryOf(::LoginUseCase)
    factoryOf(::SendMessageToChatbotUseCase)
    factoryOf(::RefreshBigNumbersUseCase)
}
