package com.quistock.quistock.app.di

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.data.remote.core.AndroidProfileImagePreparation
import com.quistock.quistock.data.remote.core.CloudinaryPhotoRepository
import com.quistock.quistock.domain.port.ProfileImagePreparation
import com.quistock.quistock.domain.port.ProfilePhotoUploadRepository
import com.quistock.quistock.domain.usecase.EditProfilePhotoUseCase
import com.quistock.quistock.presentation.profile.EditProfileViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val profileEditModule = module {
    single<ProfileImagePreparation> { AndroidProfileImagePreparation() }
    single<ProfilePhotoUploadRepository> {
        CloudinaryPhotoRepository(
            get<Context>().getString(R.string.profile_cloudinary_cloud),
            get<Context>().getString(R.string.profile_cloudinary_preset),
        )
    }
    factoryOf(::EditProfilePhotoUseCase)
    viewModelOf(::EditProfileViewModel)
}
