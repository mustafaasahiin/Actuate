package com.actuate.app.session

import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.ServerRepository

sealed interface SessionResult {
    data object Connected : SessionResult
    data class Offline(val message: String?) : SessionResult
}

/**
 * Zero-configuration session bootstrap: the app never asks the user for a
 * name, email or server address. Every launch it validates the stored token
 * against /auth/me, and auto-registers (or re-registers) the device with a
 * stable per-install device id. The server upserts by device id, so the
 * same device always maps to the same user.
 */
class SessionBootstrapper(
    private val settingsRepository: AppSettingsRepository,
    private val serverRepository: ServerRepository,
) {

    suspend fun ensureSession(deviceId: String): SessionResult {
        val config = settingsRepository.readServerConfig()
        if (config.token.isNotBlank()) {
            val me = serverRepository.me()
            if (me.isSuccess) {
                val session = me.getOrThrow()
                settingsRepository.saveServerConfig(
                    config.copy(userId = session.userId, token = session.token),
                )
                return SessionResult.Connected
            }
        }

        return serverRepository.register(
            name = "",
            email = "",
            deviceId = deviceId,
        ).fold(
            onSuccess = { session ->
                settingsRepository.saveServerConfig(
                    ServerConfig(userId = session.userId, token = session.token),
                )
                SessionResult.Connected
            },
            onFailure = { SessionResult.Offline(it.message) },
        )
    }
}