package com.actuate.app.session

import com.actuate.data.network.ServerException
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.repository.ServerSession
import java.io.IOException

sealed interface SessionResult {
    data object Connected : SessionResult
    data class Offline(val message: String?) : SessionResult
    data object NeedsLogin : SessionResult
    data object NeedsSignup : SessionResult
}

class SessionBootstrapper(
    private val settingsRepository: AppSettingsRepository,
    private val serverRepository: ServerRepository,
) {

    suspend fun ensureSession(deviceId: String = ""): SessionResult {
        return runCatching {
            val config = settingsRepository.readServerConfig()

            if (config.token.isNotBlank() && config.userId.isNotBlank()) {
                val meResult = serverRepository.me()
                if (meResult.isSuccess) {
                    val session = meResult.getOrThrow()
                    settingsRepository.saveServerConfig(
                        config.copy(userId = session.userId, token = session.token),
                    )
                    return@runCatching SessionResult.Connected
                }

                val exception = meResult.exceptionOrNull()
                if (exception !is ServerException && exception is IOException) {
                    return@runCatching SessionResult.Offline(exception.message)
                }

                if (exception is ServerException && (exception.code == 401 || exception.code == 403)) {
                    val effectiveDeviceId = deviceId.trim().ifBlank { java.util.UUID.randomUUID().toString() }
                    val anonResult = serverRepository.registerAnonymous(effectiveDeviceId)
                    if (anonResult.isSuccess) {
                        val session = anonResult.getOrThrow()
                        persistSession(session)
                        return@runCatching SessionResult.Connected
                    }
                    return@runCatching SessionResult.NeedsLogin
                }

                if (exception is ServerException) {
                    return@runCatching SessionResult.Offline(exception.message)
                }
            }

            val effectiveDeviceId = deviceId.trim().ifBlank { java.util.UUID.randomUUID().toString() }
            val anonResult = serverRepository.registerAnonymous(effectiveDeviceId)
            if (anonResult.isSuccess) {
                val session = anonResult.getOrThrow()
                persistSession(session)
                return@runCatching SessionResult.Connected
            }

            val anonException = anonResult.exceptionOrNull()
            if (anonException !is ServerException && anonException is IOException) {
                return@runCatching SessionResult.Offline(anonException.message)
            }

            SessionResult.NeedsLogin
        }.getOrElse { throwable ->
            SessionResult.Offline(throwable.message ?: "Failed to initialize session")
        }
    }

    suspend fun signUp(email: String, password: String, name: String = "", deviceId: String = ""): Result<ServerSession> {
        return runCatching {
            val result = serverRepository.register(
                email = email.trim(),
                password = password,
                name = name.trim(),
                deviceId = deviceId,
            )
            result.onSuccess { persistSession(it) }
            result.getOrThrow()
        }
    }

    suspend fun logIn(email: String, password: String, deviceId: String = ""): Result<ServerSession> {
        return runCatching {
            val result = serverRepository.login(
                email = email.trim(),
                password = password,
                deviceId = deviceId,
            )
            result.onSuccess { persistSession(it) }
            result.getOrThrow()
        }
    }

    suspend fun logOut() {
        settingsRepository.saveServerConfig(ServerConfig())
    }

    private suspend fun persistSession(session: ServerSession) {
        settingsRepository.saveServerConfig(
            ServerConfig(userId = session.userId, token = session.token),
        )
    }
}
