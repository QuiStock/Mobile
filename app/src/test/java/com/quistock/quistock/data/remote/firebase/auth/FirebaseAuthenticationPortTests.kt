package com.quistock.quistock.data.remote.firebase.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.ErrorReporter
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
    private lateinit var authenticationPort: FirebaseAuthenticationPort

    @Before
    fun setup() {
        authenticationPort = FirebaseAuthenticationPort(
            firebaseAuth = firebaseAuth,
            errorReporter = errorReporter,
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
        mockSuccessfulAuthentication(email)

        val result = authenticationPort.authenticate(email, "Abc@123!")

        result shouldBe LoginResult.Success(email)
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

        result shouldBe LoginError.UnexpectedError
        verify(exactly = 1) { errorReporter.record(any(), any()) }
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

        result shouldBe LoginError.UnexpectedError
        verify(exactly = 1) { errorReporter.record(any(), any()) }
    }

    @Test
    fun `if Firebase rejects the credentials, should return invalid credentials`() = runTest {
        val exception = mockk<FirebaseAuthInvalidCredentialsException>()
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "wrong-password")

        result shouldBe LoginError.InvalidCredentials
    }

    @Test
    fun `if Firebase rejects the user, should return user disabled`() = runTest {
        val exception = mockk<FirebaseAuthInvalidUserException>()
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LoginError.UserDisabled
    }

    @Test
    fun `if Firebase has a network failure, should return network error`() = runTest {
        val exception = mockk<FirebaseNetworkException>()
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LoginError.NetworkError
    }

    @Test
    fun `if Firebase throws an unknown exception, should preserve it as unexpected error and register it`() = runTest {
        val exception = Exception("Unexpected exception")
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forException(exception)

        val result = authenticationPort.authenticate("example@email.com", "Abc@123!")

        result shouldBe LoginError.UnexpectedError
        verify(exactly = 1) { errorReporter.record(exception, any()) }
    }

    private fun mockSuccessfulAuthentication(email: String) {
        val authResult = mockk<AuthResult>()
        val firebaseUser = mockk<FirebaseUser>()
        every { authResult.user } returns firebaseUser
        every { firebaseUser.email } returns email
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns Tasks.forResult(authResult)
    }
}
