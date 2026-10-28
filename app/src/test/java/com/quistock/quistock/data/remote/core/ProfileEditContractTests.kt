package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.ProfileEditResult
import com.quistock.quistock.domain.model.SessionSnapshot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ProfileEditContractTests {
    private val json = Json { explicitNulls = false }
    private val api = mockk<ProfileEditApi>()
    private val repository = RetrofitProfileEditRepository(api)
    private val expected = SessionSnapshot(1, true)

    @Test fun serializesOnlyChangedFieldsAndEmptyRemoval() {
        assertEquals("{\"name\":\"Fixture\"}", json.encodeToString(ProfileEditRequest(name = "Fixture")))
        assertEquals("{\"profile_photo_url\":\"\"}", json.encodeToString(ProfileEditRequest(photoUrl = "")))
        assertEquals("{}", json.encodeToString(ProfileEditRequest()))
    }

    @Test fun validatesOnlyAllowedChanges() {
        assertFalse(ProfileChanges().isValid())
        assertFalse(ProfileChanges(name = " ").isValid())
        assertFalse(ProfileChanges(photoUrl = "http://example.test/photo").isValid())
        assertFalse(ProfileChanges(photoUrl = "https://user:pass@example.test/photo").isValid())
        assertTrue(ProfileChanges(photoUrl = "").isValid())
        assertTrue(ProfileChanges(name = "Fixture", photoUrl = "https://example.test/photo").isValid())
    }

    @Test fun lostResponseAndInvalid200RequireConfirmation() = runTest {
        coEvery { api.update(any(), any()) } throws IOException("fixture")
        assertSame(ProfileEditResult.Uncertain, repository.update(ProfileChanges(name = "Fixture"), expected))
        coEvery { api.update(any(), any()) } returns Response.success(null)
        assertSame(ProfileEditResult.Uncertain, repository.update(ProfileChanges(name = "Fixture"), expected))
    }

    @Test fun incompatibleRoleDoesNotPublishSuccess() = runTest {
        coEvery { api.update(any(), any()) } returns Response.success(
            ProfileResponse("1", "Fixture", "fixture@example.test", "ADMIN", "ACTIVE"),
        )
        assertSame(ProfileEditResult.Uncertain, repository.update(ProfileChanges(name = "Fixture"), expected))
    }

    @Test fun forbiddenDoesNotBecomeUncertain() = runTest {
        coEvery { api.update(any(), any()) } returns Response.error(403, "".toResponseBody())
        assertSame(ProfileEditResult.Forbidden, repository.update(ProfileChanges(name = "Fixture"), expected))
    }
}
