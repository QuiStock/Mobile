package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.LoginError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginUseCaseTests {
    @Test fun propagatesRepositoryFailureThroughSession() = runBlocking {
        val session = mockk<SessionUseCase>()
        coEvery { session.login("synthetic", "wrong") } returns LoginError.InvalidCredentials
        assertEquals(LoginError.InvalidCredentials, LoginUseCase(session)("synthetic", "wrong"))
    }
}
