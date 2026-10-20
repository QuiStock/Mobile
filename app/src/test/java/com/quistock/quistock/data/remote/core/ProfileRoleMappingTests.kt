package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileRejection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRoleMappingTests {
    @Test fun englishEmployeeResponseMapsToAnEligibleInternalEmployee() {
        val response = Json.decodeFromString<ProfileResponse>(
            """
            {
                "id": "1",
                "name": "Fixture operator",
                "email": "fixture@example.test",
                "role": "EMPLOYEE",
                "status": "ACTIVE",
                "profile_photo_url": null,
                "store_id": 1,
                "store_code": "FIXTURE-STORE",
                "store_name": "Fixture store",
                "region_id": 1,
                "region_code": "FIXTURE-REGION",
                "region_name": "Fixture region"
            }
            """.trimIndent(),
        )

        val profile = response.toDomain()

        assertEquals("FUNCIONARIO", profile.role)
        assertEquals(1L, profile.storeId)
        assertEquals(1L, profile.regionId)
        assertNull(profile.rejection())
    }

    @Test fun employeeAliasWithoutStoreStillCannotEnterMobile() {
        val profile = response("EMPLOYEE", storeId = null, storeName = null).toDomain()

        assertEquals("FUNCIONARIO", profile.role)
        assertEquals(ProfileRejection.NO_STORE, profile.rejection())
    }

    @Test fun documentedRolesPreserveTheirExistingAccessRules() {
        val expected = mapOf(
            "FUNCIONARIO" to null,
            "GERENTE" to null,
            "ADMIN" to ProfileRejection.ROLE,
            "GERENTE_REGIONAL" to ProfileRejection.ROLE,
        )
        expected.forEach { (role, rejection) ->
            val profile = response(role).toDomain()
            assertEquals(role, profile.role)
            assertEquals(rejection, profile.rejection())
        }
    }

    @Test fun unknownAndNoncanonicalRolesAreNotConvertedToEmployees() {
        listOf("UNKNOWN", "employee", "EMPLOYEE ").forEach { role ->
            val profile = response(role).toDomain()
            assertEquals(role, profile.role)
            assertEquals(ProfileRejection.INVALID, profile.rejection())
        }
    }

    @Test fun employeeAliasDoesNotBypassInactiveAccountValidation() {
        val profile = ProfileResponse(
            "1", "Fixture operator", "fixture@example.test", "EMPLOYEE", "INACTIVE",
            storeId = 1, storeName = "Fixture store",
        ).toDomain()

        assertEquals(ProfileRejection.INVALID, profile.rejection())
    }

    private fun response(role: String, storeId: Long? = 1, storeName: String? = "Fixture store") = ProfileResponse(
        "1", "Fixture operator", "fixture@example.test", role, "ACTIVE",
        storeId = storeId, storeName = storeName,
    )
}
