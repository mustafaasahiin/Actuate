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
    fun `expired or 401 token returns NeedsLogin when re-registration fails`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "stale-token")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { server.me() } returns
            Result.failure(ServerException(401, "Invalid or expired token"))
        coEvery { server.registerAnonymous("device-1") } returns
            Result.failure(ServerException(401, "Invalid"))

        val result = bootstrapper.ensureSession("device-1")

        assertTrue(result is SessionResult.NeedsLogin)
    }

    @Test
    fun `no stored token auto-registers isolated anonymous installation on first launch`() = runTest {
        coEvery { settings.readServerConfig() } returns ServerConfig()
        coEvery { settings.saveServerConfig(any()) } returns Unit
        coEvery { server.registerAnonymous("device-9") } returns
            Result.success(ServerSession("anon-1", "tok-anon", isPro = false, quotaRemaining = 20))

        val result = bootstrapper.ensureSession("device-9")

        assertTrue(result is SessionResult.Connected)
        coVerify { settings.saveServerConfig(ServerConfig(userId = "anon-1", token = "tok-anon")) }
    }

    @Test
    fun `no stored token returns NeedsLogin when anonymous registration fails`() = runTest {
        coEvery { settings.readServerConfig() } returns ServerConfig()
        coEvery { server.registerAnonymous("device-9") } returns
            Result.failure(ServerException(400, "Disabled"))

        val result = bootstrapper.ensureSession("device-9")

        assertTrue(result is SessionResult.NeedsLogin)
    }

    @Test
    fun `server unreachable reports offline without losing the token`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "tok-1")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { server.me() } returns Result.failure(IOException("connection refused"))

        val result = bootstrapper.ensureSession("device-1")

        assertEquals(SessionResult.Offline("connection refused"), result)
        coVerify(exactly = 0) { settings.saveServerConfig(any()) }
    }

    @Test
    fun `signUp registers with email and password and persists the session`() = runTest {
        coEvery { settings.saveServerConfig(any()) } returns Unit
        coEvery {
            server.register(email = "ada@test.dev", password = "Password123!", name = "Ada Lovelace", deviceId = "device-1")
        } returns Result.success(ServerSession("u1", "tok-1", isPro = false, quotaRemaining = 20))

        val result = bootstrapper.signUp(
            email = "  ada@test.dev ",
            password = "Password123!",
            name = "  Ada Lovelace  ",
            deviceId = "device-1",
        )

        assertTrue(result.isSuccess)
        coVerify {
            server.register(email = "ada@test.dev", password = "Password123!", name = "Ada Lovelace", deviceId = "device-1")
        }
        coVerify { settings.saveServerConfig(ServerConfig(userId = "u1", token = "tok-1")) }
    }

    @Test
    fun `logIn claims the account by email and password and persists the session`() = runTest {
        coEvery { settings.saveServerConfig(any()) } returns Unit
        coEvery { server.login(email = "grace@test.dev", password = "Password123!", deviceId = "device-2") } returns
            Result.success(ServerSession("u7", "tok-7", isPro = true, quotaRemaining = null))

        val result = bootstrapper.logIn(email = " grace@test.dev ", password = "Password123!", deviceId = "device-2")

        assertTrue(result.isSuccess)
        assertEquals("u7", result.getOrThrow().userId)
        coVerify { settings.saveServerConfig(ServerConfig(userId = "u7", token = "tok-7")) }
    }

    @Test
    fun `logIn failure does not overwrite the stored session`() = runTest {
        coEvery { server.login(any(), any(), any()) } returns
            Result.failure(ServerException(401, "Invalid email or password"))

        val result = bootstrapper.logIn(email = "ghost@test.dev", password = "WrongPassword!", deviceId = "device-1")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { settings.saveServerConfig(any()) }
    }

    @Test
    fun `network error on me() goes offline without modifying config`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "tok-1")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { server.me() } returns Result.failure(IOException("no internet"))

        val result = bootstrapper.ensureSession("device-1")

        assertTrue(result is SessionResult.Offline)
        assertEquals("no internet", (result as SessionResult.Offline).message)
        coVerify(exactly = 0) { settings.saveServerConfig(any()) }
    }

    @Test
    fun `server error 500 on me() preserves cached token and reports offline`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "tok-1")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { server.me() } returns
            Result.failure(ServerException(500, "Internal Server Error"))

        val result = bootstrapper.ensureSession("device-1")

        assertTrue(result is SessionResult.Offline)
        assertEquals("Internal Server Error", (result as SessionResult.Offline).message)
        coVerify(exactly = 0) { settings.saveServerConfig(any()) }
    }

    @Test
    fun `server 503 service unavailable on me() preserves cached token and reports offline`() = runTest {
        val stored = ServerConfig(userId = "u1", token = "tok-1")
        coEvery { settings.readServerConfig() } returns stored
        coEvery { server.me() } returns
            Result.failure(ServerException(503, "Service Unavailable"))

        val result = bootstrapper.ensureSession("device-1")

        assertTrue(result is SessionResult.Offline)
        assertEquals("Service Unavailable", (result as SessionResult.Offline).message)
        coVerify(exactly = 0) { settings.saveServerConfig(any()) }
    }

    @Test
    fun `exception during readServerConfig returns offline cleanly without throwing`() = runTest {
        coEvery { settings.readServerConfig() } throws RuntimeException("DataStore disk read error")

        val result = bootstrapper.ensureSession("device-err")

        assertTrue(result is SessionResult.Offline)
        assertEquals("DataStore disk read error", (result as SessionResult.Offline).message)
    }
}
