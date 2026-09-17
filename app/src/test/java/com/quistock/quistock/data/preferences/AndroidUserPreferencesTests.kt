package com.quistock.quistock.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.quistock.quistock.R
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class AndroidUserPreferencesTests {
    private val context = mockk<Context>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private lateinit var userPreferences: AndroidUserPreferences

    @Before
    fun setup() {
        every { context.getString(R.string.shared_preferences_name) } returns PREFERENCES_NAME
        every { context.getString(R.string.shared_preferences_user_id_key) } returns USER_ID_KEY
        every {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        } returns preferences
        every { preferences.edit() } returns editor

        userPreferences = AndroidUserPreferences(context)
    }

    @Test
    fun `should open configured private preferences`() {
        verify(exactly = 1) {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }
    }

    @Test
    fun `should save user id using configured key`() {
        userPreferences.saveUserId("user-123")

        verify(exactly = 1) { editor.putString(USER_ID_KEY, "user-123") }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `should return stored user id using configured key`() {
        every { preferences.getString(USER_ID_KEY, null) } returns "user-123"

        val result = userPreferences.getUserId()

        result shouldBe "user-123"
        verify(exactly = 1) { preferences.getString(USER_ID_KEY, null) }
    }

    @Test
    fun `should return null when user id is not stored`() {
        every { preferences.getString(USER_ID_KEY, null) } returns null

        userPreferences.getUserId() shouldBe null
    }

    private companion object {
        const val PREFERENCES_NAME = "quistock_preferences"
        const val USER_ID_KEY = "user_id"
    }
}
