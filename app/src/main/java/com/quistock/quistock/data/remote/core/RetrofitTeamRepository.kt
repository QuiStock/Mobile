package com.quistock.quistock.data.remote.core

import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.TeamMember
import com.quistock.quistock.domain.model.TeamResult
import com.quistock.quistock.domain.port.TeamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Tag

interface TeamApi {
    @GET("team-members")
    suspend fun list(@Tag expected: SessionSnapshot): Response<List<TeamResponse>>

    @POST("team-members")
    suspend fun create(@Body request: TeamRequest, @Tag expected: SessionSnapshot): Response<TeamResponse>
}

@Serializable
class TeamResponse(val id: String, val name: String, val status: String)

@Serializable
class TeamRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    @SerialName("store_id") val storeId: Long,
)

class RetrofitTeamRepository(private val api: TeamApi, private val password: String) : TeamRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun list(expected: SessionSnapshot): TeamResult = try {
        val response = api.list(expected)
        response.errorBody()?.close()
        when {
            response.isSuccessful && response.body() != null -> TeamResult.Success(
                requireNotNull(response.body()).map { TeamMember(it.id, it.name, it.status) },
            )
            response.code() == 403 -> TeamResult.Forbidden
            else -> TeamResult.Failure
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        TeamResult.Failure
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun create(name: String, email: String, storeId: Long, expected: SessionSnapshot): TeamResult {
        if (password.isBlank()) return TeamResult.NotConfigured
        return try {
            val response = api.create(TeamRequest(name, email, password, "EMPLOYEE", storeId), expected)
            response.errorBody()?.close()
            when (response.code()) {
                201 -> TeamResult.Success(emptyList())
                403 -> TeamResult.Forbidden
                409 -> TeamResult.Conflict
                in 400..499 -> TeamResult.Failure
                else -> TeamResult.Uncertain
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            TeamResult.Uncertain
        }
    }
}
