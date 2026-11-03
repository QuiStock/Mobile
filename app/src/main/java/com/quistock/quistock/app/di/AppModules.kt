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
        geocodingModule,
        locationModule,
        presentationModule,
        observabilityModule,
        preferencesModule,
        retrofitModule,
        profileEditModule,
        interferenceModule,
        roleAlertsModule,
    )
}

val appModule = module {
    includes(
        externalSdksModule,
        appInternalModule,
    )
}
