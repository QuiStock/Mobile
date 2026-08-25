package com.quistock.quistock.data.remote.firebase.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.AuthenticationPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseAuthenticationPort(val firebaseAuth: FirebaseAuth) : AuthenticationPort {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun authenticate(email: String, password: String): LoginResult = try {
        val result = firebaseAuth
            .signInWithEmailAndPassword(email.trim(), password)
            .await()

        result.user?.email
            ?.let { LoginResult.Success(it) }
            ?: throw IllegalStateException("Login succeeded without an email")
    } catch (_: FirebaseAuthInvalidCredentialsException) {
        LoginError.InvalidCredentials
    } catch (_: FirebaseAuthInvalidUserException) {
        LoginError.UserDisabled
    } catch (_: FirebaseNetworkException) {
        LoginError.NetworkError
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        LoginError.UnexpectedError(exception)
    }
}
