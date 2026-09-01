package com.quistock.quistock.app.di

import com.quistock.quistock.domain.usecase.LoginUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::LoginUseCase)
}
