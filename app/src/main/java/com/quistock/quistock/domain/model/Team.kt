package com.quistock.quistock.domain.model

class TeamMember(val id: String, val name: String, val status: String)

sealed interface TeamResult {
    class Success(val members: List<TeamMember>) : TeamResult
    data object Conflict : TeamResult
    data object Forbidden : TeamResult
    data object NotConfigured : TeamResult
    data object Failure : TeamResult
    data object Uncertain : TeamResult
}
