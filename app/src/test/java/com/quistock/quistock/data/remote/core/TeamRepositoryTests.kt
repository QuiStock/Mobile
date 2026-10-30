package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.TeamResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class TeamRepositoryTests {
    private val api = mockk<TeamApi>()
    private val snapshot = SessionSnapshot(3, true)

    @Test fun createUsesEmployeeRoleAndManagerStore() = runBlocking {
        val repository = RetrofitTeamRepository(api, "initial-password")
        coEvery { api.create(any(), snapshot) } returns Response.success(201, TeamResponse("1", "Ana", "ACTIVE"))
        assertTrue(repository.create("Ana", "ana@example.test", 12, snapshot) is TeamResult.Success)
        coVerify { api.create(match { it.role == "EMPLOYEE" && it.storeId == 12L && it.password == "initial-password" }, snapshot) }
    }

    @Test fun duplicateEmailIsReported() = runBlocking {
        coEvery { api.create(any(), snapshot) } returns Response.error(409, "".toResponseBody())
        assertEquals(TeamResult.Conflict, RetrofitTeamRepository(api, "initial-password").create("Ana", "a@b.test", 12, snapshot))
    }

    @Test fun missingPasswordDoesNotSendARequest() = runBlocking {
        assertEquals(TeamResult.NotConfigured, RetrofitTeamRepository(api, "").create("Ana", "a@b.test", 12, snapshot))
        coVerify(exactly = 0) { api.create(any(), any()) }
    }
}
