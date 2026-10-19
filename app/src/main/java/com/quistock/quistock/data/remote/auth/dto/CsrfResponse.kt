package com.quistock.quistock.data.remote.auth.dto

import kotlinx.serialization.Serializable

@Serializable
class CsrfResponse(val headerName: String, val token: String) {
    override fun toString() = "CsrfResponse(redacted)"
}
