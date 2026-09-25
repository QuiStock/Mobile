package com.quistock.quistock.app.di

import org.koin.dsl.module

val externalSdksModule = module {
    includes(
        firebaseSdkModule,
        retrofitSdkModule,
        roomSdkModule,
    )
}

val appInternalModule = module {
    includes(
        firebaseModule,
        domainModule,
        presentationModule,
        observabilityModule,
        preferencesModule,
        retrofitModule,
        roomModule,
    )
}

val appModule = module {
    includes(
        externalSdksModule,
        appInternalModule,
    )
}
