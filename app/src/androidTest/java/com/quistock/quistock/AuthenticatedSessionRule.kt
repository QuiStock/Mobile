package com.quistock.quistock

import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.StoredSession
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.GlobalContext

/** Feature UI tests own their session instead of restoring credentials from the device. */
class AuthenticatedSessionRule : TestWatcher() {
    private lateinit var previous: SessionUseCase

    override fun starting(description: Description) {
        val koin = GlobalContext.get()
        previous = koin.get()
        val auth = mockk<AuthRepository>(relaxed = true)
        every { auth.hasAccess(any()) } returns true
        coEvery { auth.restore(any()) } returns StoredSession.AVAILABLE
        coEvery { auth.refresh(any()) } returns AuthResult.Success
        val session = SessionUseCase(auth)
        runBlocking { session.restore() }
        koin.declare(session, allowOverride = true)
    }

    override fun finished(description: Description) {
        GlobalContext.get().declare(previous, allowOverride = true)
    }
}
