package com.actuate.data.calendar

import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.domain.model.ParsedAction
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class GoogleCalendarInsertResult(
    val eventId: String,
    val htmlLink: String? = null,
) {
    val id: String get() = eventId
}

open class GoogleCalendarException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GoogleCalendarAuthException(
    message: String = "Google Calendar reconnection required",
    cause: Throwable? = null,
) : GoogleCalendarException(message, cause)

class GoogleCalendarApiException(
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : GoogleCalendarException(message, cause)

class GoogleCalendarSyncService(
    private val client: OkHttpClient,
    private val authManager: GoogleCalendarAuthManager,
    private val apiBaseUrl: String = "https://www.googleapis.com",
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun insertEvent(action: ParsedAction.Calendar): Result<GoogleCalendarInsertResult> = withContext(ioDispatcher) {
        val initialToken = authManager.getAccessToken(forceRefresh = false).getOrElse {
            authManager.getAccessToken(forceRefresh = true).getOrElse { refreshError ->
                return@withContext Result.failure(
                    GoogleCalendarAuthException("Google Calendar authentication failed: reconnection required", refreshError)
                )
            }
        }

        val payload = buildEventPayload(action)

        val firstResponse = runCatching {
            executeInsert(initialToken, payload)
        }.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        if (firstResponse.code == 401 || firstResponse.code == 403) {
            val refreshedToken = authManager.getAccessToken(forceRefresh = true).getOrElse { refreshError ->
                return@withContext Result.failure(
                    GoogleCalendarAuthException("Google Calendar authentication failed: reconnection required", refreshError)
                )
            }

            val retryResponse = runCatching {
                executeInsert(refreshedToken, payload)
            }.getOrElse { error ->
                return@withContext Result.failure(error)
            }

            if (retryResponse.isSuccessful) {
                return@withContext parseSuccessResponse(retryResponse.body)
            } else if (retryResponse.code == 401 || retryResponse.code == 403) {
                return@withContext Result.failure(
                    GoogleCalendarAuthException("Google Calendar authentication failed: reconnection required")
                )
            } else {
                return@withContext Result.failure(
                    GoogleCalendarApiException(
                        code = retryResponse.code,
                        message = "Google Calendar request failed: HTTP ${retryResponse.code}",
                    )
                )
            }
        }

        if (firstResponse.isSuccessful) {
            parseSuccessResponse(firstResponse.body)
        } else {
            Result.failure(
                GoogleCalendarApiException(
                    code = firstResponse.code,
                    message = "Google Calendar request failed: HTTP ${firstResponse.code}",
                )
            )
        }
    }

    internal fun buildEventPayload(action: ParsedAction.Calendar): String {
        val payload = buildJsonObject {
            put("summary", action.title)
            if (!action.description.isNullOrBlank()) {
                put("description", action.description)
            }
            if (!action.location.isNullOrBlank()) {
                put("location", action.location)
            }
            if (action.allDay) {
                val zone = ZoneId.systemDefault()
                val startDate = action.start.atZone(zone).toLocalDate()
                val endLocalDate = action.end?.atZone(zone)?.toLocalDate()
                val endDate = if (endLocalDate != null && endLocalDate.isAfter(startDate)) {
                    endLocalDate
                } else {
                    startDate.plusDays(1)
                }
                put("start", buildJsonObject { put("date", startDate.toString()) })
                put("end", buildJsonObject { put("date", endDate.toString()) })
            } else {
                val endInstant = action.end ?: action.start.plusSeconds(3600)
                put("start", buildJsonObject { put("dateTime", action.start.toString()) })
                put("end", buildJsonObject { put("dateTime", endInstant.toString()) })
            }
            put("attendees", buildJsonArray {
                for (attendee in action.attendees) {
                    val email = attendee.email
                        ?: if (attendee.name.contains('@')) attendee.name
                        else "${attendee.name.lowercase().replace(Regex("[^a-z0-9]"), ".")}@actuate.local"
                    add(
                        buildJsonObject {
                            put("email", email)
                            put("displayName", attendee.name)
                        }
                    )
                }
            })
        }
        return payload.toString()
    }

    private fun executeInsert(token: String, payload: String): HttpResponse {
        val url = "${apiBaseUrl.trimEnd('/')}/calendar/v3/calendars/primary/events"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return HttpResponse(
                code = response.code,
                isSuccessful = response.isSuccessful,
                body = body,
            )
        }
    }

    private fun parseSuccessResponse(body: String): Result<GoogleCalendarInsertResult> = runCatching {
        val element = json.parseToJsonElement(body).jsonObject
        val eventId = element["id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Google Calendar response missing event id")
        val htmlLink = runCatching { element["htmlLink"]?.jsonPrimitive?.content }.getOrNull()
        GoogleCalendarInsertResult(eventId = eventId, htmlLink = htmlLink)
    }

    private data class HttpResponse(val code: Int, val isSuccessful: Boolean, val body: String)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
