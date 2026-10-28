package com.quistock.quistock.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileChangesTests {
    private val profile = UserProfile(
        "fixture",
        "Name",
        "fixture@example.test",
        UserRole.EMPLOYEE,
        UserStatus.ACTIVE,
        null,
        1,
        "Store",
    )

    @Test fun confirmationComparesOnlyFieldsSentAndNormalizesName() {
        assertTrue(ProfileChanges(photoUrl = "").matches(profile))
        assertTrue(ProfileChanges(name = " \tName\n").matches(profile))
        assertFalse(ProfileChanges(name = "Other").matches(profile))
        assertFalse(ProfileChanges(photoUrl = "https://images.example.test/a").matches(profile))
    }

    @Test fun urlValidationRejectsMissingHostSyntaxErrorsAndExcessLength() {
        for (url in listOf("https:///photo", "https://bad host/photo", "https://example.test/" + "a".repeat(500))) {
            assertFalse(ProfileChanges(photoUrl = url).isValid())
        }
        assertTrue(ProfileChanges(name = "Name").isValid())
        assertTrue(ProfileChanges(photoUrl = "").isValid())
    }
}
