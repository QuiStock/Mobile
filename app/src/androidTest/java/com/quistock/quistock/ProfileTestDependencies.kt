package com.quistock.quistock

import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.ProfilePhotoRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.usecase.LoadProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.Deferred
import org.koin.core.context.GlobalContext

/** Keeps UI fixtures independent of Core credentials, device storage, and external images. */
class ProfileTestDependencies(session: SessionUseCase) {
    private val koin = GlobalContext.get()
    private val previousProfile = koin.get<ProfileUseCase>()
    private val previousPhoto = koin.get<LoadProfilePhotoUseCase>()
    private var beforeRead: suspend () -> Unit = {}
    private val repository = object : ProfileRepository {
        override suspend fun read(): ProfileResult {
            beforeRead()
            return ProfileResult.Success(
                UserProfile(
                    id = "1",
                    name = "Fixture operator",
                    email = "fixture@example.test",
                    role = UserRole.EMPLOYEE,
                    status = UserStatus.ACTIVE,
                    photoUrl = null,
                    storeId = 1,
                    storeName = "Fixture store",
                ),
            )
        }
    }
    private val profile = ProfileUseCase(repository, session)
    private val photo = LoadProfilePhotoUseCase(
        object : ProfilePhotoRepository {
            override suspend fun read(url: String): ByteArray? = null
        },
        profile,
        session,
    )

    fun holdReadsUntil(release: Deferred<Unit>) {
        beforeRead = { release.await() }
    }

    fun install() {
        koin.declare(profile, allowOverride = true)
        koin.declare(photo, allowOverride = true)
    }

    fun restore() {
        koin.declare(previousProfile, allowOverride = true)
        koin.declare(previousPhoto, allowOverride = true)
    }
}
