package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProfilePhotoCacheTests {
    private val fixture = ProfileEditScenario()

    @Test fun sameSessionAndUrlReusePhotoUntilProfileUpdateInvalidatesIt() = runTest {
        fixture.ready()
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        coEvery { fixture.photoReader.read(any()) } returnsMany listOf(first, second)
        assertSame(first, fixture.photos())
        assertSame(first, fixture.photos())
        coVerify(exactly = 1) { fixture.photoReader.read(any()) }
        coEvery { fixture.writer.update(any(), any()) } returns ProfileEditResult.Saved(fixture.user(name = "New"))
        fixture.editor.save(ProfileChanges(name = "New"), fixture.session.snapshot())
        assertSame(second, fixture.photos())
        coVerify(exactly = 2) { fixture.photoReader.read(any()) }
    }

    @Test fun missingOrBlankPhotoDoesNotDownload() = runTest {
        for (url in listOf(null, "", " ")) {
            fixture.ready(fixture.user(photoUrl = url))
            assertNull(fixture.photos())
        }
        fixture.profile.clear()
        assertNull(fixture.photos())
        coVerify(exactly = 0) { fixture.photoReader.read(any()) }
    }

    @Test fun unavailableImageKeepsNullCachedForTheCurrentProfile() = runTest {
        fixture.ready()
        coEvery { fixture.photoReader.read(any()) } returns null
        assertNull(fixture.photos())
        assertNull(fixture.photos())
        coVerify(exactly = 1) { fixture.photoReader.read(any()) }
    }

    @Test fun replacedProfileDiscardsAPendingDownload() = runTest {
        fixture.ready()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ByteArray?>()
        coEvery { fixture.photoReader.read(any()) } coAnswers {
            started.complete(Unit)
            response.await()
        }
        val download = async { fixture.photos() }
        started.await()
        fixture.profile.publishEdit(fixture.user(name = "New"), fixture.session.snapshot()) { fixture.photos.clear() }
        response.complete(byteArrayOf(1))
        assertNull(download.await())
    }
}
