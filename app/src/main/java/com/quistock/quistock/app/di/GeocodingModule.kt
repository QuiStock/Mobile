package com.quistock.quistock.app.di

import com.quistock.quistock.data.geocoding.AndroidAddressGeocoder
import com.quistock.quistock.domain.port.AddressGeocoder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val geocodingModule = module {
    single<AddressGeocoder> { AndroidAddressGeocoder(androidContext()) }
}
