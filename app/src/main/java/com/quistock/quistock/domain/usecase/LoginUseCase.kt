package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.Logger
import kotlinx.coroutines.CancellationException

class LoginUseCase(private val session: SessionUseCase, private val logger: Logger) {
    private val logContext = mapOf("operation" to "login")

    fun retryDelayMillis() = session.retryDelayMillis

    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(email: String, password: String): LoginResult {
        logger.debug("Login started", logContext)
        return try {
            val result = session.login(email, password)
            logger.info("Login completed", logContext + ("result" to result.javaClass.simpleName))
            if (result == AuthResult.Success) {
                logger.debug("Authenticated session published", logContext)
            }
            result
        } catch (e: CancellationException) {
            logger.debug("Login cancelled", logContext)
            throw e
        } catch (e: Exception) {
            // Exception messages and stack traces may contain transport or credential data.
            logger.info("Login failed unexpectedly", logContext)
            throw e
        }
    }
}
