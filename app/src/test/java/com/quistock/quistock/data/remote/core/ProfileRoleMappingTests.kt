package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileResult
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

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

        assertEquals(UserRole.EMPLOYEE, profile.role)
        assertEquals(UserStatus.ACTIVE, profile.status)
        assertEquals(1L, profile.storeId)
        assertEquals(1L, profile.regionId)
        assertNull(profile.rejection())
    }

    @Test fun employeeAliasWithoutStoreStillCannotEnterMobile() {
        val profile = response("EMPLOYEE", storeId = null, storeName = null).toDomain()

        assertEquals(UserRole.EMPLOYEE, profile.role)
        assertEquals(ProfileRejection.NO_STORE, profile.rejection())
    }

    @Test fun canonicalAndLegacyRolesMapToTypedRoles() {
        val expected = mapOf(
            "EMPLOYEE" to UserRole.EMPLOYEE,
            "FUNCIONARIO" to UserRole.EMPLOYEE,
            "MANAGER" to UserRole.MANAGER,
            "GERENTE" to UserRole.MANAGER,
            "ADMIN" to UserRole.ADMIN,
            "REGIONAL_MANAGER" to UserRole.REGIONAL_MANAGER,
            "GERENTE_REGIONAL" to UserRole.REGIONAL_MANAGER,
        )
        expected.forEach { (role, mappedRole) ->
            val profile = response(role).toDomain()
            assertEquals(mappedRole, profile.role)
            assertEquals(if (mappedRole.hasAccess) null else ProfileRejection.ROLE, profile.rejection())
        }
    }

    @Test fun unknownAndNoncanonicalRolesAreNotConvertedToEmployees() {
        listOf("UNKNOWN", "employee", "EMPLOYEE ").forEach { role ->
            val error = runCatching { response(role).toDomain() }.exceptionOrNull()
            assertTrue(error is SerializationException)
        }
    }

    @Test fun employeeAliasDoesNotBypassInactiveAccountValidation() {
        val profile = ProfileResponse(
            "1", "Fixture operator", "fixture@example.test", "EMPLOYEE", "INACTIVE",
            storeId = 1, storeName = "Fixture store",
        ).toDomain()

        assertEquals(UserStatus.INACTIVE, profile.status)
        assertEquals(ProfileRejection.INVALID, profile.rejection())
    }

    @Test fun unknownAndNoncanonicalStatusesFailMapping() {
        listOf("UNKNOWN", "active", "ACTIVE ").forEach { status ->
            val response = ProfileResponse("1", "Fixture", "fixture@example.test", "EMPLOYEE", status)
            assertTrue(runCatching { response.toDomain() }.exceptionOrNull() is SerializationException)
        }
    }

    @Test fun invalidRoleOrStatusProducesRecoverableRepositoryFailure() = runTest {
        val api = mockk<ProfileApi>()
        val repository = RetrofitProfileRepository(api)
        listOf("UNKNOWN" to "ACTIVE", "EMPLOYEE" to "UNKNOWN").forEach { (role, status) ->
            coEvery { api.read() } returns Response.success(
                ProfileResponse("1", "Fixture", "fixture@example.test", role, status),
            )
            assertTrue(repository.read() is ProfileResult.Failure)
        }
    }

    private fun response(role: String, storeId: Long? = 1, storeName: String? = "Fixture store") = ProfileResponse(
        "1", "Fixture operator", "fixture@example.test", role, "ACTIVE",
        storeId = storeId, storeName = storeName,
    )
}
