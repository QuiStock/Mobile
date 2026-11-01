package com.quistock.quistock.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidChatbotConversationStoreIntegrationTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = AndroidChatbotConversationStore(context)
    private val userIds = mutableListOf<String>()

    @After
    fun clearTestConversations() {
        userIds.forEach(store::clear)
    }

    @Test
    fun getOrCreateGeneratesUuidAndPersistsAcrossStoreInstances() {
        val userId = userId()
        assertNull(store.current(userId))

        val generated = store.getOrCreate(userId)

        assertEquals(generated, UUID.fromString(generated).toString())
        assertEquals(generated, store.current(userId))
        assertEquals(generated, AndroidChatbotConversationStore(context).getOrCreate(userId))
    }

    @Test
    fun activeConversationIsIsolatedReplacedAndClearedPerUser() {
        val firstUser = userId()
        val secondUser = userId()
        store.setActive(firstUser, "first-conversation")
        store.setActive(secondUser, "second-conversation")

        store.setActive(firstUser, "replacement-conversation")
        val recreatedStore = AndroidChatbotConversationStore(context)
        assertEquals("replacement-conversation", recreatedStore.current(firstUser))
        assertEquals("second-conversation", recreatedStore.current(secondUser))

        recreatedStore.clear(firstUser)
        assertNull(store.current(firstUser))
        assertEquals("second-conversation", store.current(secondUser))
        val generated = store.getOrCreate(firstUser)
        assertEquals(generated, UUID.fromString(generated).toString())
    }

    @Test
    fun blankIdsAreRejectedWithoutPersistingAConversation() {
        val validUser = userId()

        assertThrows(IllegalArgumentException::class.java) { store.getOrCreate(" \t") }
        assertThrows(IllegalArgumentException::class.java) { store.setActive(" \t", "conversation") }
        assertThrows(IllegalArgumentException::class.java) { store.setActive(validUser, " \n") }
        assertNull(store.current(validUser))
    }

    @Test
    fun failedPreferenceCommitsAreReported() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val failingContext = mockk<Context>()
        every { failingContext.applicationContext } returns failingContext
        every {
            failingContext.getSharedPreferences("chatbot_conversations", Context.MODE_PRIVATE)
        } returns preferences
        every { preferences.edit() } returns editor
        every { preferences.getString(any(), null) } returns null
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.commit() } returns false

        val failingStore = AndroidChatbotConversationStore(failingContext)

        assertThrows(IllegalStateException::class.java) { failingStore.getOrCreate("user") }
        assertThrows(IllegalStateException::class.java) { failingStore.setActive("user", "conversation") }
        assertThrows(IllegalStateException::class.java) { failingStore.clear("user") }
    }

    private fun userId() = UUID.randomUUID().toString().also(userIds::add)
}
