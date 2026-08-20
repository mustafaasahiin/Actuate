package com.actuate.data.network

import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParserSource
import com.actuate.domain.model.Priority
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.model.ServerList
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.ServerParsedActions
import com.actuate.domain.repository.ServerRepository
import com.actuate.domain.repository.ServerSession
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * HTTP client for the Actuate server. The server holds the LLM key and the
 * destination API credentials; the app only stores a session token.
 *
 * The base URL is compiled in (BuildConfig), never user-editable. Returns
 * [Result.failure] with a user-friendly message for any HTTP error, so
 * callers can fall back to on-device parsing/execution.
 */
class ActuateServerApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val configProvider: () -> ServerConfig,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ServerRepository {

    private fun baseUrl(): String = baseUrl.trimEnd('/')

    private fun authHeader(config: ServerConfig): Map<String, String> =
        if (config.token.isNotBlank()) mapOf("Authorization" to "Bearer ${config.token}") else emptyMap()

    private suspend fun <T> call(
        method: String,
        path: String,
        body: String?,
        config: ServerConfig,
        transform: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url("${baseUrl()}$path")
                .method(method, body?.toRequestBody(JSON_MEDIA_TYPE))
            authHeader(config).forEach { (k, v) -> builder.header(k, v) }
            builder.build().let { request ->
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val serverMessage = runCatching {
                            json.decodeFromString<ErrorDto>(responseBody).error
                        }.getOrNull()
                        throw ServerException(
                            code = response.code,
                            message = serverMessage ?: "Server error (HTTP ${response.code})",
                        )
                    }
                    if (responseBody.isBlank()) {
                        throw ServerException(code = response.code, message = "Empty response from server")
                    }
                    transform(responseBody)
                }
            }
        }
    }

    override suspend fun register(name: String, email: String, deviceId: String): Result<ServerSession> {
        val config = configProvider()
        val body = json.encodeToString(
            RegisterRequest.serializer(),
            RegisterRequest(name = name, email = email, deviceId = deviceId),
        )
        return call("POST", "/api/v1/auth/register", body, config) { raw ->
            val dto = json.decodeFromString<SessionDto>(raw)
            ServerSession(
                userId = dto.userId,
                token = dto.token,
                isPro = dto.isPro,
                quotaRemaining = dto.quotaRemaining,
            )
        }
    }

    override suspend fun me(): Result<ServerSession> {
        val config = configProvider()
        return call("GET", "/api/v1/auth/me", null, config) { raw ->
            val dto = json.decodeFromString<SessionDto>(raw)
            ServerSession(
                userId = dto.userId,
                token = config.token,
                isPro = dto.isPro,
                quotaRemaining = dto.quotaRemaining,
            )
        }
    }

    override suspend fun parse(transcript: String): Result<ServerParsedActions> {
        val config = configProvider()
        val body = json.encodeToString(ParseRequest.serializer(), ParseRequest(transcript))
        return call("POST", "/api/v1/actions/parse", body, config) { raw ->
            val dto = json.decodeFromString<ParseResponseDto>(raw)
            ServerParsedActions(
                actions = dto.actions.map { it.toDomain() },
                source = if (dto.source == "llm") ParserSource.LLM else ParserSource.RULES,
                confidence = dto.confidence,
            )
        }
    }

    override suspend fun execute(actions: List<ParsedAction>): Result<List<ExecutionResult>> {
        val config = configProvider()
        val body = json.encodeToString(
            ExecuteRequest.serializer(),
            ExecuteRequest(actions.map { it.toDto() }),
        )
        return call("POST", "/api/v1/actions/execute", body, config) { raw ->
            json.decodeFromString<ExecuteResponseDto>(raw).results.map { dto ->
                ExecutionResult(
                    actionId = dto.actionId,
                    destination = destinationOf(dto.destination),
                    success = dto.success,
                    message = dto.message,
                )
            }
        }
    }

    override suspend fun fetchLists(): Result<List<ServerList>> {
        val config = configProvider()
        return call("GET", "/api/v1/actions/lists", null, config) { raw ->
            json.decodeFromString<ListsDto>(raw).lists.map { list ->
                ServerList(
                    name = list.name,
                    items = list.items.map { item ->
                        ServerListItem(
                            id = item.id,
                            text = item.text,
                            list = item.list,
                            done = item.done,
                            createdAt = item.createdAt,
                        )
                    },
                )
            }
        }
    }

    override suspend fun setItemDone(actionId: String, done: Boolean): Result<Boolean> {
        val config = configProvider()
        val body = json.encodeToString(ToggleDoneRequest.serializer(), ToggleDoneRequest(done))
        return call("POST", "/api/v1/actions/$actionId/done", body, config) { raw ->
            json.decodeFromString<ToggleDoneResponseDto>(raw).done
        }
    }

    override suspend fun fetchActions(): Result<List<ServerActionItem>> {
        val config = configProvider()
        return call("GET", "/api/v1/actions", null, config) { raw ->
            json.decodeFromString<HistoryDto>(raw).actions.map { dto ->
                ServerActionItem(
                    id = dto.id,
                    type = dto.type,
                    title = dto.title.orEmpty().ifBlank { dto.text.orEmpty() }.ifBlank { "Action" },
                    at = dto.at?.let(::parseInstant),
                    done = dto.done,
                    createdAt = dto.createdAt,
                )
            }
        }
    }

    private fun destinationOf(value: String): Destination = when (value) {
        "calendar" -> Destination.CALENDAR
        "notion" -> Destination.NOTION
        "reminders" -> Destination.REMINDERS
        "local" -> Destination.LOCAL
        else -> Destination.NONE
    }

    private fun ParsedAction.toDto(): ActionDto = when (this) {
        is ParsedAction.Calendar -> ActionDto(
            id = id,
            type = "calendar_event",
            title = title,
            start = start.toString(),
            end = end?.toString(),
            allDay = allDay.takeIf { it },
            location = location,
            attendees = attendees.takeIf { it.isNotEmpty() },
            description = description,
        )
        is ParsedAction.ListItem -> ActionDto(
            id = id,
            type = "list_item",
            text = text,
            list = list,
            priority = priority?.name?.lowercase(),
        )
        is ParsedAction.Reminder -> ActionDto(
            id = id,
            type = "reminder",
            title = title,
            dueAt = dueAt?.toString(),
            priority = priority?.name?.lowercase(),
        )
        is ParsedAction.Unknown -> ActionDto(id = id, type = "unknown")
    }

    private fun ActionDto.toDomain(): ParsedAction = when (type) {
        "calendar_event" -> ParsedAction.Calendar(
            title = title.orEmpty().ifBlank { "New event" },
            start = start?.let(::parseInstant) ?: Instant.now(),
            end = end?.let(::parseInstant),
            allDay = allDay == true,
            location = location,
            attendees = attendees ?: emptyList(),
            description = description,
        )
        "list_item" -> ParsedAction.ListItem(
            text = text.orEmpty().ifBlank { "Untitled item" },
            list = list,
            priority = priority?.toPriority(),
        )
        "reminder" -> ParsedAction.Reminder(
            title = title.orEmpty().ifBlank { "Reminder" },
            dueAt = dueAt?.let(::parseInstant),
            priority = priority?.toPriority(),
        )
        else -> ParsedAction.Unknown(reason = "Unsupported type: $type")
    }

    private fun String.toPriority(): Priority? = when (lowercase()) {
        "high" -> Priority.HIGH
        "medium" -> Priority.MEDIUM
        "low" -> Priority.LOW
        else -> null
    }

    private fun parseInstant(value: String): Instant? = runCatching {
        when {
            value.contains('+') -> OffsetDateTime.parse(value).toInstant()
            value.endsWith("Z") -> Instant.parse(value)
            else -> LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
        }
    }.getOrNull()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Serializable
        private data class RegisterRequest(
            val name: String,
            val email: String,
            @SerialName("deviceId") val deviceId: String,
        )

        @Serializable
        private data class ParseRequest(val transcript: String)

        @Serializable
        private data class SessionDto(
            @SerialName("userId") val userId: String,
            val token: String = "",
            @SerialName("isPro") val isPro: Boolean = false,
            @SerialName("quotaRemaining") val quotaRemaining: Int? = null,
        )

        @Serializable
        private data class ErrorDto(val error: String = "", val code: String = "")

        @Serializable
        private data class ParseResponseDto(
            val actions: List<ActionDto> = emptyList(),
            val source: String = "rules",
            val confidence: Float = 0f,
        )

        @Serializable
        private data class ExecuteRequest(val actions: List<ActionDto>)

        @Serializable
        private data class ExecuteResponseDto(val results: List<ExecutionResultDto>)

        @Serializable
        private data class ExecutionResultDto(
            @SerialName("actionId") val actionId: String,
            val destination: String,
            val success: Boolean,
            val message: String,
        )

        @Serializable
        private data class ListsDto(val lists: List<ListDto> = emptyList())

        @Serializable
        private data class ListDto(val name: String, val items: List<ListItemDto> = emptyList())

        @Serializable
        private data class ListItemDto(
            val id: String,
            val text: String,
            val list: String = "general",
            val done: Boolean = false,
            val createdAt: Long = 0,
        )

        @Serializable
        private data class ToggleDoneRequest(val done: Boolean)

        @Serializable
        private data class ToggleDoneResponseDto(val id: String, val done: Boolean)

        @Serializable
        private data class HistoryDto(val actions: List<ServerActionDto> = emptyList())

        @Serializable
        private data class ServerActionDto(
            val id: String,
            val type: String,
            val title: String? = null,
            val text: String? = null,
            val at: String? = null,
            val done: Boolean = false,
            val createdAt: Long = 0,
        )

        @Serializable
        private data class ActionDto(
            val id: String = "",
            val type: String,
            val title: String? = null,
            val start: String? = null,
            val end: String? = null,
            @SerialName("all_day") val allDay: Boolean? = null,
            val location: String? = null,
            val attendees: List<String>? = null,
            val description: String? = null,
            val text: String? = null,
            val list: String? = null,
            val priority: String? = null,
            @SerialName("due_at") val dueAt: String? = null,
        )
    }
}

class ServerException(val code: Int, message: String) : IOException(message)