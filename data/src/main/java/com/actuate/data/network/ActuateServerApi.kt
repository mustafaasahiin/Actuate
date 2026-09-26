package com.actuate.data.network

import com.actuate.domain.model.Attendee
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ChannelConnection
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.IntegrationsStatus
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
import java.time.ZoneId
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
    private val configProvider: suspend () -> ServerConfig,
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

    override suspend fun register(email: String, password: String, name: String, deviceId: String): Result<ServerSession> {
        val config = configProvider()
        val body = json.encodeToString(
            RegisterRequest.serializer(),
            RegisterRequest(email = email.trim().lowercase(), password = password, name = name, deviceId = deviceId),
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

    override suspend fun registerAnonymous(installationId: String): Result<ServerSession> {
        val config = configProvider()
        val body = json.encodeToString(
            AnonymousRequest.serializer(),
            AnonymousRequest(installationId = installationId, deviceId = installationId),
        )
        return call("POST", "/api/v1/auth/anonymous", body, config) { raw ->
            val dto = json.decodeFromString<SessionDto>(raw)
            ServerSession(
                userId = dto.userId,
                token = dto.token,
                isPro = dto.isPro,
                quotaRemaining = dto.quotaRemaining,
            )
        }
    }

    override suspend fun syncEntitlement(isPro: Boolean, promoCode: String): Result<Boolean> {
        val config = configProvider()
        val body = json.encodeToString(
            SyncEntitlementRequest.serializer(),
            SyncEntitlementRequest(isPro = isPro, promoCode = promoCode),
        )
        return call("POST", "/api/v1/auth/sync-entitlement", body, config) {
            true
        }
    }

    override suspend fun login(email: String, password: String, deviceId: String): Result<ServerSession> {
        val config = configProvider()
        val body = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = email.trim().lowercase(), password = password, deviceId = deviceId),
        )
        return call("POST", "/api/v1/auth/login", body, config) { raw ->
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
        val body = json.encodeToString(
            ParseRequest.serializer(),
            ParseRequest(
                transcript = transcript,
                nowIso = Instant.now().toString(),
                timeZone = ZoneId.systemDefault().id,
            ),
        )
        return call("POST", "/api/v1/actions/parse", body, config) { raw ->
            val dto = json.decodeFromString<ParseResponseDto>(raw)
            val parsedActions = if (dto.actions.isNotEmpty()) {
                dto.actions.map { it.toDomain() }
            } else {
                dto.intents.map { it.toDomain() }
            }
            ServerParsedActions(
                actions = parsedActions,
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

    override suspend fun deleteEvent(id: String): Result<Unit> {
        val config = configProvider()
        return call("POST", "/api/v1/calendar/$id/delete", null, config) { _ -> Unit }
    }

    override suspend fun sendCancellationMessage(eventId: String, email: String, message: String): Result<Unit> {
        val config = configProvider()
        val body = json.encodeToString(
            CancelMessageRequest.serializer(),
            CancelMessageRequest(eventId = eventId, email = email, message = message),
        )
        return call("POST", "/api/v1/calendar/$eventId/cancel-message", body, config) { _ -> Unit }
    }

    override suspend fun integrationsStatus(): Result<IntegrationsStatus> {
        val config = configProvider()
        return call("GET", "/api/v1/integrations", null, config) { raw ->
            val dto = json.decodeFromString<IntegrationsStatusDto>(raw)
            IntegrationsStatus(
                email = dto.email.toDomain("email"),
                whatsapp = dto.whatsapp.toDomain("whatsapp"),
            )
        }
    }

    override suspend fun connectEmail(host: String, port: Int, user: String, password: String): Result<Unit> {
        val config = configProvider()
        val body = json.encodeToString(
            ConnectEmailRequest.serializer(),
            ConnectEmailRequest(host = host.trim(), port = port, user = user.trim(), password = password),
        )
        return call("POST", "/api/v1/integrations/email", body, config) { _ -> Unit }
    }

    override suspend fun connectWhatsApp(phoneNumberId: String, accessToken: String): Result<Unit> {
        val config = configProvider()
        val body = json.encodeToString(
            ConnectWhatsAppRequest.serializer(),
            ConnectWhatsAppRequest(phoneNumberId = phoneNumberId.trim(), accessToken = accessToken.trim()),
        )
        return call("POST", "/api/v1/integrations/whatsapp", body, config) { _ -> Unit }
    }

    override suspend fun testChannel(channel: String, recipient: String): Result<Unit> {
        val config = configProvider()
        val body = json.encodeToString(
            TestChannelRequest.serializer(),
            TestChannelRequest(recipient = recipient.trim()),
        )
        return call("POST", "/api/v1/integrations/$channel/test", body, config) { _ -> Unit }
    }

    override suspend fun disconnectChannel(channel: String): Result<Unit> {
        val config = configProvider()
        return call("DELETE", "/api/v1/integrations/$channel", null, config) { _ -> Unit }
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
                    attendees = dto.attendees.map { Attendee(name = it, email = if (it.contains('@')) it else null) },
                    done = dto.done,
                    createdAt = dto.createdAt,
                )
            }
        }
    }

    private fun ChannelStatusDto.toDomain(channel: String) = ChannelConnection(
        channel = channel,
        connected = connected,
        verified = verified,
        connectedAt = connectedAt,
        hint = hint,
        corrupted = corrupted,
    )

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
            attendees = attendees.map { it.email ?: it.name }.takeIf { it.isNotEmpty() },
            description = description,
        )
        is ParsedAction.ListItem -> ActionDto(
            id = id,
            type = "list_item",
            text = text,
            list = list,
            priority = priority?.name?.lowercase(),
        )
        is ParsedAction.ListAction -> ActionDto(
            id = id,
            type = "list",
            list = listName,
            items = items,
            isNewList = isNewList,
        )
        is ParsedAction.Task -> ActionDto(
            id = id,
            type = "task",
            title = title,
            priority = priority?.name?.lowercase(),
            dueAt = dueDate?.toString(),
        )
        is ParsedAction.Note -> ActionDto(
            id = id,
            type = "note",
            text = content,
            destination = destination,
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

    private fun IntentDto.toDomain(): ParsedAction = when (type) {
        "calendar_event" -> {
            val datePart = date ?: java.time.LocalDate.now().toString()
            val timePart = time ?: "09:00"
            val allDay = time == null
            val startIso = "${datePart}T${if (timePart.length == 5) "$timePart:00" else timePart}Z"
            val startInstant = parseInstant(startIso) ?: Instant.now()
            val durationSeconds = (duration ?: 60) * 60L
            val rawTitle = title.orEmpty().trim()
            val isGeneric = rawTitle.isBlank() ||
                rawTitle.equals("new meeting", ignoreCase = true) ||
                rawTitle.equals("meeting", ignoreCase = true) ||
                rawTitle.equals("new event", ignoreCase = true) ||
                rawTitle.equals("event", ignoreCase = true) ||
                rawTitle.equals("appointment", ignoreCase = true)
            val resolvedTitle = if (isGeneric) {
                if (attendees.isNotEmpty()) "Meeting with ${attendees.joinToString(", ")}" else (rawTitle.ifBlank { "Meeting" })
            } else {
                rawTitle
            }
            ParsedAction.Calendar(
                title = resolvedTitle,
                start = startInstant,
                end = if (allDay) null else startInstant.plusSeconds(durationSeconds),
                allDay = allDay,
                location = location,
                attendees = attendees.map { Attendee(name = it, email = if (it.contains('@')) it else null) },
            )
        }
        "task" -> {
            if (time != null || dueDate != null || title?.startsWith("remind", ignoreCase = true) == true) {
                val datePart = dueDate ?: date ?: java.time.LocalDate.now().toString()
                val timePart = time ?: "09:00"
                val iso = "${datePart}T${if (timePart.length == 5) "$timePart:00" else timePart}Z"
                ParsedAction.Reminder(
                    title = title.orEmpty().ifBlank { "Reminder" },
                    dueAt = parseInstant(iso) ?: Instant.now(),
                    priority = priority?.toPriority(),
                )
            } else {
                ParsedAction.Task(
                    title = title.orEmpty().ifBlank { "Untitled task" },
                    priority = priority?.toPriority(),
                    project = project,
                    dueDate = null,
                )
            }
        }
        "list" -> {
            var rawListName = (listName ?: list).orEmpty().trim()
            if (rawListName.endsWith(" list", ignoreCase = true)) {
                rawListName = rawListName.dropLast(5).trim()
            }
            val lower = rawListName.lowercase(java.util.Locale.ROOT)
            val isGroceryName = lower == "groceries" || lower == "grocery" ||
                lower == "new" || lower == "new list" || lower == "untitled list" || lower == "list" || lower.isBlank()
            val resolvedListName = when {
                isGroceryName -> "Groceries"
                lower == "shopping" -> "Shopping"
                lower == "work" -> "Work"
                lower == "todo" || lower == "to-do" -> "Todo"
                else -> rawListName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
            }
            val resolvedItems = when {
                items.isNotEmpty() -> items.filter { it.isNotBlank() }
                !item.isNullOrBlank() -> listOf(item.trim())
                !text.isNullOrBlank() -> listOf(text.trim())
                else -> listOf("Item")
            }
            ParsedAction.ListAction(
                listName = resolvedListName,
                isNewList = isNewList,
                items = resolvedItems,
            )
        }
        "note" -> ParsedAction.Note(
            content = content.orEmpty(),
            destination = destination,
        )
        else -> ParsedAction.Unknown(reason = "Unsupported intent type: $type")
    }

    private fun ActionDto.toDomain(): ParsedAction = when (type) {
        "calendar_event" -> {
            val rawTitle = title.orEmpty().trim()
            val attendeeList = attendees?.map { Attendee(name = it, email = if (it.contains('@')) it else null) } ?: emptyList()
            val isGeneric = rawTitle.isBlank() ||
                rawTitle.equals("new meeting", ignoreCase = true) ||
                rawTitle.equals("meeting", ignoreCase = true) ||
                rawTitle.equals("new event", ignoreCase = true) ||
                rawTitle.equals("event", ignoreCase = true) ||
                rawTitle.equals("appointment", ignoreCase = true)
            val resolvedTitle = if (isGeneric) {
                if (attendeeList.isNotEmpty()) "Meeting with ${attendeeList.joinToString(", ") { it.name }}" else (rawTitle.ifBlank { "Meeting" })
            } else {
                rawTitle
            }
            ParsedAction.Calendar(
                title = resolvedTitle,
                start = start?.let(::parseInstant) ?: Instant.now(),
                end = end?.let(::parseInstant),
                allDay = allDay == true,
                location = location,
                attendees = attendeeList,
                description = description,
            )
        }
        "list_item" -> {
            var rawList = list.orEmpty().trim()
            if (rawList.endsWith(" list", ignoreCase = true)) {
                rawList = rawList.dropLast(5).trim()
            }
            val lower = rawList.lowercase(java.util.Locale.ROOT)
            val isGroceryName = lower == "groceries" || lower == "grocery" ||
                lower == "new" || lower == "new list" || lower == "untitled list" || lower == "list"
            val resolvedList = when {
                isGroceryName -> "groceries"
                lower == "shopping" -> "shopping"
                lower == "work" -> "work"
                lower == "todo" || lower == "to-do" -> "todo"
                rawList.isNotBlank() -> lower
                else -> "general"
            }
            ParsedAction.ListItem(
                text = text.orEmpty().ifBlank { "Untitled item" },
                list = resolvedList,
                priority = priority?.toPriority(),
            )
        }
        "list" -> {
            var rawListName = list.orEmpty().trim()
            if (rawListName.endsWith(" list", ignoreCase = true)) {
                rawListName = rawListName.dropLast(5).trim()
            }
            val lower = rawListName.lowercase(java.util.Locale.ROOT)
            val isGroceryName = lower == "groceries" || lower == "grocery" ||
                lower == "new" || lower == "new list" || lower == "untitled list" || lower == "list" || lower.isBlank()
            val resolvedListName = when {
                isGroceryName -> "Groceries"
                lower == "shopping" -> "Shopping"
                lower == "work" -> "Work"
                lower == "todo" || lower == "to-do" -> "Todo"
                else -> rawListName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
            }
            ParsedAction.ListAction(
                listName = resolvedListName,
                isNewList = isNewList ?: false,
                items = items?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf(text.orEmpty().ifBlank { "Item" }),
            )
        }
        "task" -> ParsedAction.Task(
            title = title.orEmpty().ifBlank { "Untitled task" },
            priority = priority?.toPriority(),
            dueDate = dueAt?.let(::parseInstant),
        )
        "note" -> ParsedAction.Note(
            content = text.orEmpty(),
            destination = destination,
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
            val email: String,
            val password: String,
            val name: String = "",
            @SerialName("deviceId") val deviceId: String = "",
        )

        @Serializable
        private data class AnonymousRequest(
            @SerialName("installationId") val installationId: String = "",
            @SerialName("deviceId") val deviceId: String = "",
        )

        @Serializable
        private data class SyncEntitlementRequest(
            @SerialName("isPro") val isPro: Boolean = false,
            @SerialName("promoCode") val promoCode: String = "",
        )

        @Serializable
        private data class LoginRequest(
            val email: String,
            val password: String,
            @SerialName("deviceId") val deviceId: String = "",
        )

        @Serializable
        internal data class ParseRequest(
            val transcript: String,
            val nowIso: String? = null,
            val timeZone: String? = null,
        )

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
            val intents: List<IntentDto> = emptyList(),
            val actions: List<ActionDto> = emptyList(),
            val source: String = "rules",
            val confidence: Float = 0f,
        )

        @Serializable
        private data class IntentDto(
            val type: String,
            val title: String? = null,
            val date: String? = null,
            val time: String? = null,
            val duration: Int? = null,
            val attendees: List<String> = emptyList(),
            val location: String? = null,
            val priority: String? = null,
            val project: String? = null,
            @SerialName("due_date") val dueDate: String? = null,
            @SerialName("list_name") val listName: String? = null,
            val list: String? = null,
            @SerialName("is_new_list") val isNewList: Boolean = false,
            val items: List<String> = emptyList(),
            val item: String? = null,
            val text: String? = null,
            val content: String? = null,
            val destination: String? = null,
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
        private data class CancelMessageRequest(
            @SerialName("eventId") val eventId: String,
            @SerialName("email") val email: String,
            @SerialName("message") val message: String,
        )

        @Serializable
        private data class ToggleDoneRequest(val done: Boolean)

        @Serializable
        private data class ToggleDoneResponseDto(val id: String, val done: Boolean)

        @Serializable
        private data class IntegrationsStatusDto(
            val email: ChannelStatusDto = ChannelStatusDto(),
            val whatsapp: ChannelStatusDto = ChannelStatusDto(),
        )

        @Serializable
        private data class ChannelStatusDto(
            val connected: Boolean = false,
            val verified: Boolean = false,
            @SerialName("connectedAt") val connectedAt: Long? = null,
            val hint: String = "",
            val corrupted: Boolean = false,
        )

        @Serializable
        private data class ConnectEmailRequest(
            val host: String,
            val port: Int,
            val user: String,
            val password: String,
        )

        @Serializable
        private data class ConnectWhatsAppRequest(
            @SerialName("phoneNumberId") val phoneNumberId: String,
            @SerialName("accessToken") val accessToken: String,
        )

        @Serializable
        private data class TestChannelRequest(val recipient: String)

        @Serializable
        private data class HistoryDto(val actions: List<ServerActionDto> = emptyList())

        @Serializable
        private data class ServerActionDto(
            val id: String,
            val type: String,
            val title: String? = null,
            val text: String? = null,
            val at: String? = null,
            val attendees: List<String> = emptyList(),
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
            val items: List<String>? = null,
            @SerialName("is_new_list") val isNewList: Boolean? = null,
            val destination: String? = null,
        )
    }
}

class ServerException(val code: Int, message: String) : IOException(message)