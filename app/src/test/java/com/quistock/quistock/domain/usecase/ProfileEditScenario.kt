package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ProfileEditRepository
import com.quistock.quistock.domain.port.ProfilePhotoRepository
import com.quistock.quistock.domain.port.ProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

internal class ProfileEditScenario {
    private val auth = mockk<AuthRepository>(relaxed = true)
    val session = SessionUseCase(auth)
    val reader = mockk<ProfileRepository>()
    val writer = mockk<ProfileEditRepository>()
    val photoReader = mockk<ProfilePhotoRepository>()
    val profile = ProfileUseCase(reader, session)
    val photos = LoadProfilePhotoUseCase(photoReader, profile, session)
    val editor = EditProfileUseCase(writer, reader, profile, photos, session)

    suspend fun ready(value: UserProfile = user()) {
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        session.login("fixture@example.test", "fixture")
        coEvery { reader.read() } returns ProfileResult.Success(value)
        profile.load()
    }

    fun user(
        name: String = "Fixture",
        photoUrl: String? = "https://images.example.test/old.jpg",
        id: String = "fixture",
        role: UserRole = UserRole.EMPLOYEE,
    ) = UserProfile(id, name, "fixture@example.test", role, UserStatus.ACTIVE, photoUrl, 1, "Store")
}
