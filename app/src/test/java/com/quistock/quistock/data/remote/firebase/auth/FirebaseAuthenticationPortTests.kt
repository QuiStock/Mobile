package com.quistock.quistock.data.remote.firebase.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.quistock.quistock.domain.model.LegacyLoginError
import com.quistock.quistock.domain.model.LegacyLoginResult
import com.quistock.quistock.domain.model.User
import com.quistock.quistock.domain.port.ErrorReporter
import com.quistock.quistock.domain.port.Logger
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirebaseAuthenticationPortTests {
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val errorReporter = mockk<ErrorReporter>(relaxUnitFun = true)
    private val logger = mockk<Logger>(relaxUnitFun = true)
    private lateinit var authenticationPort: FirebaseAuthenticationPort

    @Before
    fun setup() {
        authenticationPort = FirebaseAuthenticationPort(
            firebaseAuth = firebaseAuth,
            errorReporter = errorReporter,
            logger = logger,
        )
    }

    @Test
    fun `when authenticating, should trim the email`() = runTest {
        val email = "  example@email.com  "
        mockSuccessfulAuthentication(email.trim())

        authenticationPort.authenticate(email, "Abc@123!")

        verify(exactly = 1) {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), any())
        }
    }

    @Test
    fun `if Firebase returns a user with email, should return success`() = runTest {
        val email = "example@email.com"
        val userId = "user-123"
        mockSuccessfulAuthentication(email, userId)

        val result = authenticationPort.authenticate(email, "Abc@123!")

        result shouldBe LegacyLoginResult.Success(User(id = userId, email = email))
        verify(exactly = 1) {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        }
    }

    @Test
    fun `if Firebase returns no user, should return unexpected error and register it`() = runTest {
        val authResult = mockk<AuthResult>()
        every { authResult.user } returns null
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LegacyLoginError.UnexpectedError
        verify(exactly = 1) {
            errorReporter.record(
                match { it is IllegalStateException && it.message == "Login succeeded without an user" },
                mapOf("operation" to "login", "provider" to "firebase_auth"),
            )
        }
    }

    @Test
    fun `if Firebase returns a user without email, should return unexpected error and register it`() = runTest {
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>()
        every { authResult.user } returns firebaseUser
        every { firebaseUser.email } returns null
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LegacyLoginError.UnexpectedError
        verify(exactly = 1) {
            errorReporter.record(
                match { it is IllegalStateException && it.message == "Login succeeded without an email" },
                mapOf("operation" to "login", "provider" to "firebase_auth"),
            )
        }
    }

    @Test
    fun `if Firebase rejects the credentials, should return invalid credentials`() = runTest {
        val exception = mockk<FirebaseAuthInvalidCredentialsException>()
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "wrong-password")

        result shouldBe LegacyLoginError.InvalidCredentials
    }

    @Test
    fun `if Firebase rejects the user, should return user disabled`() = runTest {
        val exception = mockk<FirebaseAuthInvalidUserException>()
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LegacyLoginError.UserDisabled
    }

    @Test
    fun `if Firebase has a network failure, should return network error`() = runTest {
        val exception = mockk<FirebaseNetworkException>()
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LegacyLoginError.NetworkError
    }

    @Test
    fun `if Firebase throws an unknown exception, should preserve it as unexpected error and register it`() = runTest {
        val exception = Exception("Unexpected exception")
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LegacyLoginError.UnexpectedError
        verify(exactly = 1) {
            errorReporter.record(exception, mapOf("operation" to "login", "provider" to "firebase_auth"))
        }
    }

    private fun mockSuccessfulAuthentication(email: String, userId: String = "user-123") {
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>()
        every { authResult.user } returns firebaseUser
        every { firebaseUser.email } returns email
        every { firebaseUser.uid } returns userId
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)
    }
}
