package com.quistock.quistock.data.remote.firebase.auth

import com.google.firebase.auth.FirebaseAuth
import com.quistock.quistock.domain.port.AuthenticationPort
import kotlinx.coroutines.tasks.await

class FirebaseAuthenticationPort(val firebaseAuth: FirebaseAuth) : AuthenticationPort {
    override suspend fun authenticate(email: String, password: String): Boolean {
        try {
            val result = firebaseAuth
                .signInWithEmailAndPassword(email.trim(), password)
                .await()

            return result.user != null
        } catch (_: Exception) {
            return false
        }
    }
}
