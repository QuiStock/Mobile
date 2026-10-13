package com.quistock.quistock.data.secrets

/** Either durable mechanism suffices; failure of both cannot promise revocation after a restart. */
internal suspend fun revokeCredential(destroyKey: () -> Unit, removeRecord: suspend () -> Unit) {
    val keyDestroyed = runCatching(destroyKey).isSuccess
    val removed = runCatching { removeRecord() }.isSuccess
    check(keyDestroyed || removed) { "Credential revocation unavailable" }
    check(removed) { "Credential removal unavailable; encryption key revoked" }
}
