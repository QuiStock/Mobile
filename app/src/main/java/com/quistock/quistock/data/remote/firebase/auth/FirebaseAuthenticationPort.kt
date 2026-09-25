package com.quistock.quistock.data.remote.firebase.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.quistock.quistock.domain.model.LegacyLoginError
import com.quistock.quistock.domain.model.LegacyLoginResult
import com.quistock.quistock.domain.model.User
import com.quistock.quistock.domain.port.AuthenticationPort
import com.quistock.quistock.domain.port.ErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseAuthenticationPort(val firebaseAuth: FirebaseAuth, val errorReporter: ErrorReporter) :
    AuthenticationPort {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun authenticate(email: String, password: String): LegacyLoginResult = try {
        val result = firebaseAuth
            .signInWithEmailAndPassword(email.trim(), password)
            .await()

        val firebaseUser = result.user ?: throw IllegalStateException("Login succeeded without an user")
        val email = firebaseUser.email ?: throw IllegalStateException("Login succeeded without an email")
        val userId = firebaseUser.uid

        val user = User(id = userId, email = email)
        LegacyLoginResult.Success(user = user)
    } catch (_: FirebaseAuthInvalidCredentialsException) {
        LegacyLoginError.InvalidCredentials
    } catch (_: FirebaseAuthInvalidUserException) {
        LegacyLoginError.UserDisabled
    } catch (_: FirebaseNetworkException) {
        LegacyLoginError.NetworkError
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        errorReporter.record(
            exception = exception,
            context = mapOf(
                "operation" to "login",
                "provider" to "firebase_auth",
            ),
        )

        LegacyLoginError.UnexpectedError
    }
}
