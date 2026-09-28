package com.quistock.quistock.data.secrets

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.port.delete
import com.quistock.quistock.domain.port.read
import com.quistock.quistock.domain.port.save
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecretStorageIntegrationTests {
    private lateinit var context: Context
    private lateinit var storage: SecretStorageImpl

    @Before
    fun setup() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = SecretStorageImpl(context)
        storage.delete<RefreshToken>()
    }

    @After
    fun tearDown() = runBlocking {
        storage.delete<RefreshToken>()
    }

    @Test
    fun accessTokenStaysOnlyInTheStorageInstance() = runBlocking {
        val token = AccessToken("short-lived-token")
        storage.save(token)

        assertEquals(token, storage.read<AccessToken>())
        assertNull(SecretStorageImpl(context).read<AccessToken>())

        storage.delete<AccessToken>()
        assertNull(storage.read<AccessToken>())
    }

    @Test
    fun refreshTokenSurvivesNewStorageInstanceAndCanBeReplacedAndDeleted() = runBlocking {
        val original = RefreshToken("first-refresh-token")
        val rotated = RefreshToken("rotated-refresh-token")

        storage.save(original)
        assertEquals(original, SecretStorageImpl(context).read<RefreshToken>())

        storage.save(rotated)
        assertEquals(rotated, SecretStorageImpl(context).read<RefreshToken>())

        storage.delete<RefreshToken>()
        assertNull(SecretStorageImpl(context).read<RefreshToken>())
    }

    @Test
    fun refreshTokenIsNotWrittenAsPlaintext() = runBlocking {
        val token = RefreshToken("distinctive-secret-token-value")
        storage.save(token)

        val file = File(context.filesDir, "datastore/secrets.preferences_pb")
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(token.token))
    }

    @Test
    fun unregisteredTypeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { storage.read(String::class) }
        }
    }
}
