package com.actuate.app.session

import com.actuate.data.network.ServerException
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.repository.ServerSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBootstrapperTest {

    private val settings: AppSettingsRepository = mockk()
    private val server: ServerRepository = mockk()
    private val bootstrapper = SessionBootstrapper(settings, server)

    @Test
    fun `valid stored token keeps the session via me()`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "tok-1")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { settings.saveServerConfig(any()) } returns Unit
        coEvery { server.me() } returns
            Result.success(ServerSession("u1", "tok-1", isPro = false, quotaRemaining = 7))

        val result = bootstrapper.ensureSession("device-1")

        assertTrue(result is SessionResult.Connected)
        coVerify { settings.saveServerConfig(stored.copy(userId = "u1", token = "tok-1")) }
    }

    @Test
    fun `expired or invalid token re-registers the device`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "stale-token")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { settings.saveServerConfig(any()) } returns Unit
        coEvery { server.me() } returns
            Result.failure(ServerException(401, "Invalid or expired token"))
        coEvery { server.register(any(), any(), any()) } returns
            Result.success(ServerSession("u1", "fresh-token", isPro = false, quotaRemaining = 10))

        val result = bootstrapper.ensureSession("device-1")

        assertTrue(result is SessionResult.Connected)
        coVerify { server.register(name = "", email = "", deviceId = "device-1") }
        coVerify { settings.saveServerConfig(ServerConfig(userId = "u1", token = "fresh-token")) }
    }

    @Test
    fun `no stored token registers a fresh session`() = runTest {
        coEvery { settings.readServerConfig() } returns ServerConfig()
        coEvery { settings.saveServerConfig(any()) } returns Unit
        coEvery { server.register(any(), any(), any()) } returns
            Result.success(ServerSession("u9", "tok-9", isPro = true, quotaRemaining = null))

        val result = bootstrapper.ensureSession("device-9")

        assertTrue(result is SessionResult.Connected)
        coVerify { settings.saveServerConfig(ServerConfig(userId = "u9", token = "tok-9")) }
    }

    @Test
    fun `server unreachable reports offline without losing the token`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "tok-1")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { server.me() } returns Result.failure(IOException("connection refused"))
        coEvery { server.register(any(), any(), any()) } returns
            Result.failure(IOException("connection refused"))

        val result = bootstrapper.ensureSession("device-1")

        assertEquals(SessionResult.Offline("connection refused"), result)
        coVerify(exactly = 0) { settings.saveServerConfig(any()) }
    }
}