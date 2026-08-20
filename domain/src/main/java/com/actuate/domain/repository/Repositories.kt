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
}

/**
 * The Actuate dev server: registers the user, parses transcripts with the
 * server-side LLM and executes actions against the destination APIs.
 */
interface ServerRepository {
    suspend fun register(
        name: String,
        email: String,
        deviceId: String,
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