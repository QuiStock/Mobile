package com.quistock.quistock.data.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.quistock.quistock.domain.model.AlertMemory
import com.quistock.quistock.domain.model.RoleAlertType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAlertMemoryStoreTests {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `memory round trips per user and keeps owned request order`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = DataStoreAlertMemoryStore(
            PreferenceDataStoreFactory.create(scope = scope) { java.io.File(folder.root, "alerts.preferences_pb") },
        )
        assertEquals(AlertMemory(), store.read("7"))
        val written = store.update("7") {
            AlertMemory(setOf(RoleAlertType.NEAR_EXPIRY), setOf("P:1"), linkedSetOf("b", "a"))
        }
        assertEquals(written, store.read("7"))
        assertEquals(listOf("b", "a"), store.read("7").ownRequests.toList())
        assertEquals(AlertMemory(), store.read("8"))
        scope.cancel()
    }
}
