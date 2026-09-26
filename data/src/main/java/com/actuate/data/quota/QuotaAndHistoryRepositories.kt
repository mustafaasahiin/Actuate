package com.actuate.data.quota

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.actuate.data.store.actuateDataStore
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.model.Destination
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import com.actuate.domain.usecase.QuotaPolicy
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

class QuotaRepositoryImpl(private val context: Context) : QuotaRepository {

    override fun observeRemaining(): Flow<Int> =
        context.actuateDataStore.data.map { prefs ->
            val raw = prefs[QUOTA_TIMESTAMPS] ?: ""
            QuotaPolicy.remaining(parseTimestamps(raw), Instant.now())
        }

    override suspend fun remaining(): Int {
        val prefs = context.actuateDataStore.data.first()
        val raw = prefs[QUOTA_TIMESTAMPS] ?: ""
        return QuotaPolicy.remaining(parseTimestamps(raw), Instant.now())
    }

    override suspend fun tryConsume(count: Int): Boolean {
        var consumed = false
        context.actuateDataStore.edit { prefs ->
            val raw = prefs[QUOTA_TIMESTAMPS] ?: ""
            val now = Instant.now()
            val windowStart = now.minusSeconds(7L * 24 * 60 * 60)
            val stamps = parseTimestamps(raw).filter { it.isAfter(windowStart) }.toMutableList()
            if (QuotaPolicy.canConsume(stamps, now, count)) {
                stamps.removeIf { it == now }
                repeat(count) { stamps += now }
                prefs[QUOTA_TIMESTAMPS] = stamps.joinToString(",") { it.toEpochMilli().toString() }
                consumed = true
            } else {
                prefs[QUOTA_TIMESTAMPS] = stamps.joinToString(",") { it.toEpochMilli().toString() }
            }
        }
        return consumed
    }

    private fun parseTimestamps(raw: String): List<Instant> =
        raw?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.map { Instant.ofEpochMilli(it) } ?: emptyList()

    companion object {
        private val QUOTA_TIMESTAMPS = stringPreferencesKey("quota_timestamps")
    }
}

/** History stored as a JSON array in DataStore, capped to avoid unbounded growth. */
class HistoryRepositoryImpl(private val context: Context) : HistoryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeHistory(): Flow<List<ActionRecord>> =
        context.actuateDataStore.data.map { prefs ->
            val raw = prefs[HISTORY_JSON] ?: "[]"
            runCatching {
                val element = json.parseToJsonElement(raw)
                element.jsonArray.map { json.decodeFromJsonElement<ActionRecordDto>(it).toDomain() }
            }.getOrDefault(emptyList())
        }

    override suspend fun record(record: ActionRecord) {
        context.actuateDataStore.edit { prefs ->
            val raw = prefs[HISTORY_JSON] ?: "[]"
            val existing = runCatching {
                json.parseToJsonElement(raw).jsonArray
                    .map { json.decodeFromJsonElement<ActionRecordDto>(it).toDomain() }
            }.getOrDefault(emptyList())
            val updated = (existing.filterNot { it.id == record.id } + record).takeLast(MAX_HISTORY)
            val encoded = json.encodeToJsonElement(updated.map { it.toDto() })
            prefs[HISTORY_JSON] = encoded.toString()
        }
    }

    override suspend fun clear() {
        context.actuateDataStore.edit { prefs ->
            prefs.remove(HISTORY_JSON)
            // Also reset quota timestamps for consistency
            prefs.remove(stringPreferencesKey("quota_timestamps"))
        }
    }

    private fun ActionRecord.toDto() = ActionRecordDto(
        id = id,
        timestamp = timestamp.toEpochMilli(),
        transcript = transcript,
        summary = summary,
        actionType = actionType,
        status = status.name,
        message = message,
        destination = destination.name,
        captureId = captureId,
    )

    private fun ActionRecordDto.toDomain() = ActionRecord(
        id = id,
        timestamp = Instant.ofEpochMilli(timestamp),
        transcript = transcript,
        summary = summary,
        actionType = actionType,
        status = runCatching { ActionStatus.valueOf(status) }.getOrDefault(ActionStatus.FAILED),
        message = message,
        destination = runCatching { Destination.valueOf(destination) }.getOrDefault(Destination.NONE),
        captureId = captureId,
    )

    companion object {
        private const val MAX_HISTORY = 100
        private val HISTORY_JSON = stringPreferencesKey("history_json")
    }
}

@kotlinx.serialization.Serializable
data class ActionRecordDto(
    val id: String,
    val timestamp: Long,
    val transcript: String,
    val summary: String,
    val actionType: String,
    val status: String,
    val message: String = "",
    val destination: String = "NONE",
    val captureId: String? = null,
)
