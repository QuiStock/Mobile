package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditRecoveryTests {
    private val fixture = ProfileEditScenario()

    @Test fun invalidChangesNeverReachTheWriter() = runTest {
        fixture.ready()
        val result = fixture.editor.save(ProfileChanges(name = " "), fixture.session.snapshot())
        assertTrue((result as ProfileEditResult.Rejected).invalidInput)
        coVerify(exactly = 0) { fixture.writer.update(any(), any()) }
    }

    @Test fun failedConfirmationRetainsRetryAfterWithoutRepeatingWrite() = runTest {
        fixture.ready()
        coEvery { fixture.reader.read() } returns ProfileResult.Failure(60_000)
        val result = fixture.editor.confirm(ProfileChanges(name = "New"), fixture.session.snapshot())
        assertEquals(60_000L, (result as ProfileEditResult.CheckLater).retryAfterMillis)
        coVerify(exactly = 0) { fixture.writer.update(any(), any()) }
    }

    @Test fun forbiddenConfirmationExpiresTheSession() = runTest {
        fixture.ready()
        coEvery { fixture.reader.read() } returns ProfileResult.Forbidden
        val result = fixture.editor.confirm(ProfileChanges(name = "New"), fixture.session.snapshot())
        assertSame(ProfileEditResult.Forbidden, result)
        assertSame(SessionState.Expired, fixture.session.state.value)
    }

    @Test fun newlyForbiddenRoleFromSuccessfulPatchBlocksTheSession() = runTest {
        fixture.ready()
        val regional = fixture.user(role = UserRole.REGIONAL_MANAGER)
        coEvery { fixture.writer.update(any(), any()) } returns ProfileEditResult.Saved(regional)
        assertSame(
            ProfileEditResult.Forbidden,
            fixture.editor.save(ProfileChanges(name = "New"), fixture.session.snapshot()),
        )
        assertSame(SessionState.Expired, fixture.session.state.value)
    }

    @Test fun responseForAnotherUserCannotReplaceSharedProfile() = runTest {
        fixture.ready()
        val original = fixture.profile.state.value
        coEvery { fixture.writer.update(any(), any()) } returns ProfileEditResult.Saved(fixture.user(id = "other"))
        val result = fixture.editor.save(ProfileChanges(name = "New"), fixture.session.snapshot())
        assertSame(ProfileEditResult.Uncertain, result)
        assertSame(original, fixture.profile.state.value)
    }

    @Test fun clearedProfileCannotAcceptALateEdit() = runTest {
        fixture.ready()
        val expected = fixture.session.snapshot()
        fixture.profile.clear()
        var invalidated = false
        val accepted = fixture.profile.publishEdit(fixture.user(), expected) { invalidated = true }
        assertFalse(accepted)
        assertFalse(invalidated)
    }
}
