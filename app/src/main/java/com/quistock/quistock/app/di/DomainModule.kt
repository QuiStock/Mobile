package com.quistock.quistock.app.di

import com.quistock.quistock.domain.usecase.EditProfileUseCase
import com.quistock.quistock.domain.usecase.LoadPriorityAlertsUseCase
import com.quistock.quistock.domain.usecase.LoadProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::EditProfileUseCase)
    factoryOf(::LoadPriorityAlertsUseCase)
    single { SessionUseCase(get(), get()) }
    single { ProfileUseCase(get(), get(), get()) }
    singleOf(::LoadProfilePhotoUseCase)
    factoryOf(::LoginUseCase)
    factoryOf(::SendMessageToChatbotUseCase)
}
