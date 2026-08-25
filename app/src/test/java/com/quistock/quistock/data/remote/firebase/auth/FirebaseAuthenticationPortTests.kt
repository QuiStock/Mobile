package com.quistock.quistock.data.remote.firebase.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirebaseAuthenticationPortTests {
    private val firebaseAuth = mockk<FirebaseAuth>()
    private lateinit var authenticationPort: FirebaseAuthenticationPort

    @Before
    fun setup() {
        authenticationPort = FirebaseAuthenticationPort(firebaseAuth)
    }

    @Test
    fun `when authenticating, should trim the email`() = runTest {
        val email = "  example@email.com  "
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>()
        every { authResult.user } returns firebaseUser
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)

        authenticationPort.authenticate(email, "Abc@123!")

        verify(exactly = 1) {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), any())
        }
    }

    @Test
    fun `if Firebase returns an user, should return true`() = runTest {
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>()
        every { authResult.user } returns firebaseUser
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe true
        verify(exactly = 1) {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        }
    }

    @Test
    fun `if Firebase returns null, should return false`() = runTest {
        val authResult = mockk<AuthResult>()
        every { authResult.user } returns null
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe false
        verify(exactly = 1) {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        }
    }

    @Test
    fun `if Firebase throws exception, should return false`() = runTest {
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(Exception())

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe false
        verify(exactly = 1) {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        }
    }
}
