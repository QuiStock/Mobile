package com.quistock.quistock.presentation.profile

import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEditProfileBinding
import com.quistock.quistock.domain.model.ProfileChanges
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.presentation.activity.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileEditorRenderingTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()
    private val user = UserProfile(
        "fixture",
        "Fixture",
        "fixture@example.test",
        UserRole.EMPLOYEE,
        UserStatus.ACTIVE,
        null,
        1,
        "Store",
    )

    @Test fun progressBlocksEditingAndEveryRecoverableErrorHasVisibleFeedback() = withEditor { renderer, binding ->
        for (stage in ProfileEditStage.entries.filter { it != ProfileEditStage.IDLE }) {
            renderer.render(EditProfileState(user, name = "Changed", stage = stage))
            assertFalse(binding.profileFullNameInput.isEnabled)
            assertFalse(binding.profileCancelButton.isEnabled)
            assertFalse(binding.profileSaveChangesButton.isEnabled)
            assertTrue(binding.profileEditStatus.text.isNotEmpty())
        }
        for (message in ProfileEditMessage.entries) {
            renderer.render(EditProfileState(user, name = "Changed", message = message))
            assertTrue(binding.profileFullNameInput.isEnabled)
            assertEquals(View.VISIBLE, binding.profileEditStatus.visibility)
            assertTrue(binding.profileEditStatus.text.isNotEmpty())
        }
    }

    @Test fun invalidNamesExposeControlErrorsAndConfirmationLocksDraft() = withEditor { renderer, binding ->
        for (name in listOf("", "x".repeat(151))) {
            renderer.render(EditProfileState(user, name = name))
            assertNotNull(binding.profileFullNameInput.error)
            assertFalse(binding.profileSaveChangesButton.isEnabled)
        }
        renderer.render(EditProfileState(user, name = "Changed", pending = ProfileChanges(name = "Changed")))
        assertFalse(binding.profileFullNameInput.isEnabled)
        assertTrue(binding.profileSaveChangesButton.isEnabled)
        assertEquals(
            binding.root.context.getString(R.string.profile_check_again),
            binding.profileSaveChangesButton.text.toString(),
        )
        renderer.render(EditProfileState(user, name = "Changed", retryAt = Long.MAX_VALUE))
        assertFalse(binding.profileSaveChangesButton.isEnabled)
    }

    @Test fun missingUploadConfigurationLeavesNameEditingAvailable() = withEditor { _, binding ->
        val renderer = EditProfileRenderer(binding, uploadAvailable = false) { null }
        renderer.render(EditProfileState(user, name = "Changed"))
        assertTrue(binding.profileFullNameInput.isEnabled)
        assertTrue(binding.profileSaveChangesButton.isEnabled)
        assertEquals(
            binding.root.context.getString(R.string.profile_upload_unavailable),
            binding.profileEditStatus.text.toString(),
        )
        renderer.render(EditProfileState())
        assertFalse(binding.profileFullNameInput.isEnabled)
    }

    @Test fun unavailablePhotoNeverPreventsRenderingName() = withEditor { _, binding ->
        val renderer = EditProfileRenderer(binding, uploadAvailable = true) { throw SessionException() }
        renderer.render(EditProfileState(user, name = "Changed"))
        assertEquals("Changed", binding.profileFullNameInput.text.toString())
        assertNotNull(binding.profileEditPhoto.drawable)
        renderer.render(EditProfileState(user, removePhoto = true))
        assertNotNull(binding.profileEditPhoto.drawable)
    }

    private fun withEditor(test: suspend (EditProfileRenderer, FragmentEditProfileBinding) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = FragmentEditProfileBinding.inflate(LayoutInflater.from(activity))
                val renderer = EditProfileRenderer(binding, uploadAvailable = true) { null }
                runBlocking { test(renderer, binding) }
            }
        }
    }
}
