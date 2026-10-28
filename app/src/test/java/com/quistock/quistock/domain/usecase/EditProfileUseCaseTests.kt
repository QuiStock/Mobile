package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.profileNameError
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ProfileEditRepository
import com.quistock.quistock.domain.port.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditProfileUseCaseTests {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val session = SessionUseCase(auth)
    private val reader = mockk<ProfileRepository>()
    private val writer = mockk<ProfileEditRepository>()
    private val profile = ProfileUseCase(reader, session)
    private val photos = LoadProfilePhotoUseCase(mockk(), profile, session)
    private val edit = EditProfileUseCase(writer, reader, profile, photos, session)

    private fun user(name: String = "Fixture") = UserProfile(
        "fixture", name, "fixture@example.test", UserRole.EMPLOYEE, UserStatus.ACTIVE, null, 1, "Store",
    )

    private suspend fun ready() {
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.login(any(), any(), any()) } returns AuthResult.Success
        session.login("fixture@example.test", "fixture")
        coEvery { reader.read() } returns ProfileResult.Success(user())
        profile.load()
    }

    @Test fun validatesBeforeServerNormalization() {
        assertNotNull(profileNameError(" "))
        assertNotNull(profileNameError(" ".repeat(150) + "A"))
        assertNull(profileNameError("A".repeat(150)))
    }

    @Test fun confirmedResponseUpdatesSharedProfile() = runTest {
        ready()
        val updated = user("Updated")
        coEvery { writer.update(any(), any()) } returns ProfileEditResult.Saved(updated)
        edit.save(ProfileChanges(name = "Updated"), session.snapshot())
        assertSame(updated, (profile.state.value as ProfileState.Ready).profile)
        coVerify(exactly = 1) { reader.read() }
    }

    @Test fun uncertainWriteRequiresExplicitReadAndNeverRepeatsPatch() = runTest {
        ready()
        coEvery { writer.update(any(), any()) } returns ProfileEditResult.Uncertain
        val changes = ProfileChanges(name = " Updated ")
        assertSame(ProfileEditResult.Uncertain, edit.save(changes, session.snapshot()))
        coEvery { reader.read() } returns ProfileResult.Success(user("Updated"))
        assertTrue(edit.confirm(changes, session.snapshot()) is ProfileEditResult.Saved)
        coVerify(exactly = 1) { writer.update(any(), any()) }
    }

    @Test fun divergingReadPreservesSharedProfile() = runTest {
        ready()
        val original = profile.state.value
        assertTrue(edit.confirm(ProfileChanges(name = "Other"), session.snapshot()) is ProfileEditResult.Rejected)
        assertSame(original, profile.state.value)
        coVerify(exactly = 0) { writer.update(any(), any()) }
    }

    @Test fun lateWriteCannotPublishAfterSessionChange() = runTest {
        ready()
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ProfileEditResult>()
        coEvery { writer.update(any(), any()) } coAnswers {
            entered.complete(Unit)
            response.await()
        }
        val pending = async {
            runCatching { edit.save(ProfileChanges(name = "Old"), session.snapshot()) }
        }
        entered.await()
        session.expire(session.snapshot())
        profile.clear()
        response.complete(ProfileEditResult.Saved(user("Old")))
        assertTrue(pending.await().isFailure)
        assertSame(ProfileState.Empty, profile.state.value)
    }
}
