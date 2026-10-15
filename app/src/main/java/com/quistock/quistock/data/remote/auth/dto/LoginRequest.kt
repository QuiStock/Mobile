package com.quistock.quistock.data.remote.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String) {
    val platform = "mobile"

    override fun toString() = "LoginRequest(redacted)"
}
