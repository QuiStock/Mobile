package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.port.Logger
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginUseCaseTests {
    @Test fun propagatesRepositoryFailureThroughSession() = runBlocking {
        val session = mockk<SessionUseCase>()
        coEvery { session.login("synthetic", "wrong") } returns LoginError.InvalidCredentials
        val logger = mockk<Logger>(relaxed = true)
        assertEquals(LoginError.InvalidCredentials, LoginUseCase(session, logger)("synthetic", "wrong"))
        verify { logger.debug("Login started", mapOf("operation" to "login")) }
        verify { logger.info("Login completed", mapOf("operation" to "login", "result" to "InvalidCredentials")) }
    }
}
