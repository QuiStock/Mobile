package com.quistock.quistock.presentation.profile

import com.quistock.quistock.domain.port.PreparedProfileImage
import com.quistock.quistock.domain.port.ProfileImageError
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorMediaTests {
    private val fixture = ProfileEditorFixture()

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After fun cleanup() {
        fixture.close()
        Dispatchers.resetMain()
    }

    @Test fun selectingPhotoPreparesPreviewWithoutUploading() = runTest {
        val jpeg = byteArrayOf(1, 2)
        coEvery { fixture.photos.prepare(any()) } returns PreparedProfileImage.Ready(jpeg)
        val model = fixture.model()
        runCurrent()
        model.prepare { byteArrayOf(3) }
        assertTrue(model.state.value.busy)
        runCurrent()
        assertSame(jpeg, model.state.value.jpeg)
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.removePhoto)
        coVerify(exactly = 0) { fixture.photos.upload(any()) }
    }

    @Test fun failedPreparationPreservesTheSelectedPhotoAndName() = runTest {
        val jpeg = byteArrayOf(1)
        coEvery { fixture.photos.prepare(any()) } returns PreparedProfileImage.Ready(jpeg)
        val model = fixture.model()
        runCurrent()
        model.rename("New fixture")
        model.prepare { byteArrayOf(1) }
        runCurrent()
        val messages = mapOf(
            ProfileImageError.FORMAT to ProfileEditMessage.FORMAT,
            ProfileImageError.TOO_LARGE to ProfileEditMessage.TOO_LARGE,
            ProfileImageError.PREPARATION to ProfileEditMessage.PREPARATION,
            ProfileImageError.ACCESS to ProfileEditMessage.ACCESS,
        )
        for ((error, message) in messages) {
            coEvery { fixture.photos.prepare(any()) } returns PreparedProfileImage.Failed(error)
            model.prepare { byteArrayOf(2) }
            runCurrent()
            assertSame(jpeg, model.state.value.jpeg)
            assertEquals("New fixture", model.state.value.name)
            assertFalse(model.state.value.busy)
            assertSame(message, model.state.value.message)
        }
    }

    @Test fun unavailableAndForbiddenSourcesHaveRecoverableErrors() = runTest {
        val model = fixture.model()
        runCurrent()
        model.prepare { null }
        runCurrent()
        assertSame(ProfileEditMessage.PREPARATION, model.state.value.message)
        model.prepare { throw SecurityException("fixture") }
        runCurrent()
        assertSame(ProfileEditMessage.ACCESS, model.state.value.message)
        coVerify(exactly = 0) { fixture.photos.prepare(any()) }
    }

    @Test fun concurrentSelectionIsIgnoredAndDiscardCancelsPendingRead() = runTest {
        val response = CompletableDeferred<ByteArray?>()
        val model = fixture.model()
        runCurrent()
        model.prepare { response.await() }
        runCurrent()
        model.prepare { error("A second selection must not be read") }
        model.removePhoto()
        assertFalse(model.state.value.removePhoto)
        model.discard()
        response.complete(byteArrayOf(1))
        runCurrent()
        assertNull(model.state.value.original)
        assertNull(model.state.value.jpeg)
        coVerify(exactly = 0) { fixture.photos.prepare(any()) }
    }

    @Test fun removingPhotoClearsPreviewAndProducesExplicitRemoval() = runTest {
        val model = fixture.model()
        runCurrent()
        model.removePhoto()
        assertTrue(model.state.value.removePhoto)
        assertEquals("", model.state.value.changes().photoUrl)
        assertNull(model.state.value.jpeg)
    }

    @Test fun clearingViewModelRemovesPrivateCameraTemporary() = runTest {
        val file = File.createTempFile("profile-fixture-", ".jpg")
        try {
            val model = fixture.model()
            runCurrent()
            model.cameraPath = file.absolutePath
            fixture.close()
            assertFalse(file.exists())
            assertNull(model.cameraPath)
        } finally {
            file.delete()
        }
    }
}
