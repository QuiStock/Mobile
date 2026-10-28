package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorRecoveryTests {
    private val fixture = ProfileEditorFixture()

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun cleanup() {
        fixture.close()
        Dispatchers.resetMain()
    }

    @Test fun unchangedAndInvalidNamesNeverSubmit() = runTest {
        val model = fixture.model()
        runCurrent()
        model.save()
        model.rename("")
        model.save()
        model.rename("x".repeat(151))
        model.save()
        runCurrent()
        coVerify(exactly = 0) { fixture.editor.save(any(), any()) }
        assertFalse(model.state.value.busy)
    }

    @Test fun absentOrDifferentGenerationProfileCannotBeEdited() = runTest {
        for (state in listOf(ProfileState.Empty, ProfileState.Ready(2, fixture.user))) {
            fixture.profiles.value = state
            val model = fixture.model()
            runCurrent()
            model.save()
            model.prepare { error("No source may be read without a profile") }
            runCurrent()
            assertNull(model.state.value.original)
            assertFalse(model.state.value.busy)
        }
        coVerify(exactly = 0) { fixture.editor.save(any(), any()) }
    }

    @Test fun uncertainResultLocksDraftUntilExplicitConfirmation() = runTest {
        coEvery { fixture.editor.save(any(), any()) } returns ProfileEditResult.Uncertain
        coEvery { fixture.editor.confirm(any(), any()) } returns ProfileEditResult.CheckLater(0)
        val model = fixture.model()
        runCurrent()
        model.rename("Desired name")
        model.save()
        runCurrent()
        model.rename("Ignored")
        model.removePhoto()
        model.prepare { error("Pending confirmation must block new media") }
        assertEquals("Desired name", model.state.value.name)
        assertFalse(model.state.value.removePhoto)
        coEvery { fixture.editor.confirm(any(), any()) } returns ProfileEditResult.Rejected()
        model.save()
        runCurrent()
        assertNull(model.state.value.pending)
        coVerify(exactly = 1) { fixture.editor.save(any(), any()) }
        coVerify(exactly = 2) { fixture.editor.confirm(any(), any()) }
    }

    @Test fun rateLimitBlocksManualSaveWithoutSchedulingAnotherRequest() = runTest {
        coEvery { fixture.editor.save(any(), any()) } returns ProfileEditResult.Rejected(retryAfterMillis = 60_000)
        val model = fixture.model()
        runCurrent()
        model.rename("Desired name")
        model.save()
        runCurrent()
        model.save()
        runCurrent()
        coVerify(exactly = 1) { fixture.editor.save(any(), any()) }
        assertTrue(model.state.value.retryAt > profileEditNowMillis())
    }

    @Test fun unavailableSessionEndsProgressWithoutClaimingSuccess() = runTest {
        coEvery { fixture.session.requireAccess(any()) } throws SessionException()
        val model = fixture.model()
        runCurrent()
        model.rename("Desired name")
        model.save()
        runCurrent()
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.saved)
        assertEquals("Desired name", model.state.value.name)
        coVerify(exactly = 0) { fixture.editor.save(any(), any()) }
    }

    @Test fun changedGenerationClearsDraftEvenWhenSessionIsActive() = runTest {
        val model = fixture.model()
        model.rename("Old account")
        every { fixture.session.snapshot() } returns SessionSnapshot(2, true)
        runCurrent()
        assertNull(model.state.value.original)
        assertEquals("", model.state.value.name)
    }
}
