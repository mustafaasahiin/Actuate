package com.actuate.domain.model

/**
 * Session with the Actuate server. The server URL is compiled into the app
 * via BuildConfig, so the app stores only the (encrypted) user id + token.
 */
data class ServerConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val userId: String = "",
    val token: String = "",
) {
    val isConfigured: Boolean
        get() = token.isNotBlank()

    companion object {
        /** Debug default — emulator loopback to the host machine. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8787"
    }
}