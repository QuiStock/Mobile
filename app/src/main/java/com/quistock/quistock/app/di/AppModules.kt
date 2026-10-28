package com.quistock.quistock.app.di

import org.koin.dsl.module

val externalSdksModule = module {
    includes(
        firebaseSdkModule,
        retrofitSdkModule,
    )
}

val appInternalModule = module {
    includes(
        domainModule,
        presentationModule,
        observabilityModule,
        preferencesModule,
        retrofitModule,
        profileEditModule,
    )
}

val appModule = module {
    includes(
        externalSdksModule,
        appInternalModule,
    )
}
