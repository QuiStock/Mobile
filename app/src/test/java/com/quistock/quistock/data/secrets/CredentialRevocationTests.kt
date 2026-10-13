package com.quistock.quistock.data.secrets

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRevocationTests {
    @Test fun destroysKeyBeforeDiskRemovalAndAttemptsBothWhenKeyFails() = runBlocking {
        val order = mutableListOf<String>()
        revokeCredential({
            order.add("key")
            error("synthetic")
        }, { order.add("disk") })
        assertEquals(listOf("key", "disk"), order)
    }

    @Test fun diskFailureReportsLocalErrorAfterDurableKeyRevocation() = runBlocking {
        var destroyed = false
        assertTrue(runCatching { revokeCredential({ destroyed = true }, { error("synthetic") }) }.isFailure)
        assertTrue(destroyed)
    }

    @Test fun simultaneousFailuresAreExplicitRatherThanClaimingDurableRevocation() = runBlocking {
        assertTrue(
            runCatching {
                revokeCredential({ error("synthetic") }, { error("synthetic") })
            }.isFailure,
        )
    }
}
