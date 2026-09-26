package com.actuate.domain.repository

import com.actuate.domain.model.ParsedAction
import kotlinx.coroutines.flow.Flow

/** Rolling 7-day free-tier quota. */
interface QuotaRepository {
    fun observeRemaining(): Flow<Int>
    suspend fun remaining(): Int
    suspend fun tryConsume(count: Int = 1): Boolean
}

/** Local history of executed actions. */
interface HistoryRepository {
    fun observeHistory(): Flow<List<com.actuate.domain.model.ActionRecord>>
    suspend fun record(record: com.actuate.domain.model.ActionRecord)
    suspend fun clear()
}

/**
 * App settings. The server URL is compiled in via BuildConfig (per build
 * type); only the session token/userId live in storage, encrypted.
 */
interface AppSettingsRepository {
    val serverConfig: Flow<com.actuate.domain.model.ServerConfig>
    suspend fun readServerConfig(): com.actuate.domain.model.ServerConfig
    suspend fun saveServerConfig(config: com.actuate.domain.model.ServerConfig)
    val hasSeenOnboarding: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setHasSeenOnboarding(hasSeen: Boolean) {}
}

/**
 * The Actuate dev server: registers the user, parses transcripts with the
 * server-side LLM and executes actions against the destination APIs.
 */
interface ServerRepository {
    suspend fun register(
        email: String,
        password: String,
        name: String = "",
        deviceId: String = "",
    ): Result<ServerSession>

    suspend fun registerAnonymous(
        installationId: String,
    ): Result<ServerSession> = Result.failure(UnsupportedOperationException("Anonymous registration not implemented"))

    suspend fun syncEntitlement(
        isPro: Boolean,
        promoCode: String = "",
    ): Result<Boolean> = Result.success(isPro)

    /**
     * Authenticates an account with email and password.
     */
    suspend fun login(
        email: String,
        password: String,
        deviceId: String = "",
    ): Result<ServerSession>

    suspend fun me(): Result<ServerSession>

    suspend fun parse(transcript: String): Result<ServerParsedActions>

    suspend fun execute(actions: List<com.actuate.domain.model.ParsedAction>): Result<List<com.actuate.domain.model.ExecutionResult>>

    /** Grouped list items for the Lists section. */
    suspend fun fetchLists(): Result<List<com.actuate.domain.model.ServerList>>

    /** Marks a list item done/undone server-side. Returns the new state. */
    suspend fun setItemDone(actionId: String, done: Boolean): Result<Boolean>

    /** Full executed-action history (events, list items, reminders). */
    suspend fun fetchActions(): Result<List<com.actuate.domain.model.ServerActionItem>>

    /**
     * Deletes a calendar event server-side. Best-effort: the local cache is
     * cleaned regardless of the server outcome.
     */
    suspend fun deleteEvent(eventId: String): Result<Unit>

    /**
     * Sends a cancellation message for a calendar event. The server dispatches
     * it through the outbound channels the user connected in Settings
     * (WhatsApp first, then email) and records the delivery outcome.
     */
    suspend fun sendCancellationMessage(eventId: String, email: String, message: String): Result<Unit>

    /** Connection status of the user's outbound messaging channels. */
    suspend fun integrationsStatus(): Result<com.actuate.domain.model.IntegrationsStatus>

    /** Connects the user's SMTP account. The server verifies before storing. */
    suspend fun connectEmail(host: String, port: Int, user: String, password: String): Result<Unit>

    /** Connects WhatsApp Cloud API credentials. The server verifies before storing. */
    suspend fun connectWhatsApp(phoneNumberId: String, accessToken: String): Result<Unit>

    /** Sends a test message through a connected channel. */
    suspend fun testChannel(channel: String, recipient: String): Result<Unit>

    /** Disconnects a channel and wipes its server-side credentials. */
    suspend fun disconnectChannel(channel: String): Result<Unit>
}

data class ServerSession(
    val userId: String,
    val token: String,
    val isPro: Boolean,
    val quotaRemaining: Int?,
)

data class ServerParsedActions(
    val actions: List<com.actuate.domain.model.ParsedAction>,
    val source: com.actuate.domain.model.ParserSource,
    val confidence: Float,
)

/** Encrypted storage for secrets (Keystore-backed AES/GCM on Android). */
interface SecretStore {
    fun save(key: String, value: String)
    fun read(key: String): String?
    fun delete(key: String)
}

