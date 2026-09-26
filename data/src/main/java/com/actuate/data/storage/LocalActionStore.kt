package com.actuate.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.actuate.data.store.actuateDataStore
import com.actuate.domain.model.Attendee
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.LocalActionStore
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray

class LocalActionStore(
    private val dataStore: DataStore<Preferences>,
) : com.actuate.domain.repository.LocalActionStore {

    constructor(context: Context) : this(context.actuateDataStore)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getListNames(): Set<String> = dataStore.data.first()[stringSetPreferencesKey("list_names")] ?: emptySet()
    override suspend fun saveListNames(names: Set<String>) { dataStore.edit { it[stringSetPreferencesKey("list_names")] = names } }
    override suspend fun getDeletedListItemIds(): Set<String> = dataStore.data.first()[stringSetPreferencesKey("deleted_list_item_ids")] ?: emptySet()
    override suspend fun saveDeletedListItemIds(ids: Set<String>) { dataStore.edit { it[stringSetPreferencesKey("deleted_list_item_ids")] = ids } }

    override suspend fun getActions(): List<ServerActionItem> {
        val prefs = dataStore.data.first()
        val raw = prefs[ACTIONS_JSON] ?: "[]"
        return runCatching {
            val element = json.parseToJsonElement(raw)
            element.jsonArray.map { json.decodeFromJsonElement<LocalActionItemDto>(it).toDomain() }
        }.getOrDefault(emptyList())
    }

    override suspend fun saveActions(actions: List<ServerActionItem>) {
        dataStore.edit { prefs ->
            val encoded = json.encodeToJsonElement(actions.take(MAX_ITEMS).map { it.toDto() })
            prefs[ACTIONS_JSON] = encoded.toString()
        }
    }

    override suspend fun addAction(action: ServerActionItem) {
        dataStore.edit { prefs ->
            val raw = prefs[ACTIONS_JSON] ?: "[]"
            val existing = runCatching {
                json.parseToJsonElement(raw).jsonArray
                    .map { json.decodeFromJsonElement<LocalActionItemDto>(it).toDomain() }
            }.getOrDefault(emptyList()).toMutableList()
            val index = existing.indexOfFirst { it.id == action.id }
            if (index >= 0) {
                existing[index] = action
            } else {
                existing.add(action)
            }
            val encoded = json.encodeToJsonElement(existing.takeLast(MAX_ITEMS).map { it.toDto() })
            prefs[ACTIONS_JSON] = encoded.toString()
        }
    }

    override suspend fun getListItems(): List<ServerListItem> {
        val prefs = dataStore.data.first()
        val raw = prefs[LIST_ITEMS_JSON] ?: "[]"
        return runCatching {
            val element = json.parseToJsonElement(raw)
            element.jsonArray.map { json.decodeFromJsonElement<LocalListItemDto>(it).toDomain() }
        }.getOrDefault(emptyList())
    }

    override suspend fun saveListItems(items: List<ServerListItem>) {
        dataStore.edit { prefs ->
            val encoded = json.encodeToJsonElement(items.take(MAX_ITEMS).map { it.toDto() })
            prefs[LIST_ITEMS_JSON] = encoded.toString()
        }
    }

    override suspend fun addListItem(item: ServerListItem) {
        dataStore.edit { prefs ->
            val raw = prefs[LIST_ITEMS_JSON] ?: "[]"
            val existing = runCatching {
                json.parseToJsonElement(raw).jsonArray
                    .map { json.decodeFromJsonElement<LocalListItemDto>(it).toDomain() }
            }.getOrDefault(emptyList()).toMutableList()
            val index = existing.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                existing[index] = item
            } else {
                existing.add(item)
            }
            val encoded = json.encodeToJsonElement(existing.takeLast(MAX_ITEMS).map { it.toDto() })
            prefs[LIST_ITEMS_JSON] = encoded.toString()
        }
    }

    override suspend fun updateListItemDone(id: String, done: Boolean) {
        dataStore.edit { prefs ->
            val raw = prefs[LIST_ITEMS_JSON] ?: "[]"
            val existing = runCatching {
                json.parseToJsonElement(raw).jsonArray
                    .map { json.decodeFromJsonElement<LocalListItemDto>(it).toDomain() }
            }.getOrDefault(emptyList()).map {
                if (it.id == id) it.copy(done = done) else it
            }
            val encoded = json.encodeToJsonElement(existing.map { it.toDto() })
            prefs[LIST_ITEMS_JSON] = encoded.toString()
        }
    }

    override suspend fun getPendingSyncActions(): List<ServerActionItem> {
        return getActions().filter { it.pendingSync }
    }

    override suspend fun markActionSynced(id: String) {
        dataStore.edit { prefs ->
            val raw = prefs[ACTIONS_JSON] ?: "[]"
            val existing = runCatching {
                json.parseToJsonElement(raw).jsonArray
                    .map { json.decodeFromJsonElement<LocalActionItemDto>(it).toDomain() }
            }.getOrDefault(emptyList()).map {
                if (it.id == id) it.copy(pendingSync = false) else it
            }
            val encoded = json.encodeToJsonElement(existing.map { it.toDto() })
            prefs[ACTIONS_JSON] = encoded.toString()
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(ACTIONS_JSON)
            prefs.remove(LIST_ITEMS_JSON)
        }
    }

    override suspend fun deleteAction(id: String) {
        dataStore.edit { prefs ->
            val raw = prefs[ACTIONS_JSON] ?: "[]"
            val existing = runCatching {
                json.parseToJsonElement(raw).jsonArray
                    .map { json.decodeFromJsonElement<LocalActionItemDto>(it).toDomain() }
            }.getOrDefault(emptyList()).filterNot { it.id == id }
            val encoded = json.encodeToJsonElement(existing.map { it.toDto() })
            prefs[ACTIONS_JSON] = encoded.toString()
        }
    }

    private fun ServerActionItem.toDto() = LocalActionItemDto(
        id = id,
        type = type,
        title = title,
        atEpochMilli = at?.toEpochMilli(),
        endEpochMilli = end?.toEpochMilli(),
        location = location,
        attendees = attendees.map { LocalAttendeeDto(name = it.name, email = it.email) },
        description = description,
        pendingSync = pendingSync,
        done = done,
        createdAt = createdAt,
    )

    private fun LocalActionItemDto.toDomain() = ServerActionItem(
        id = id,
        type = type,
        title = title,
        at = atEpochMilli?.let { Instant.ofEpochMilli(it) },
        end = endEpochMilli?.let { Instant.ofEpochMilli(it) },
        location = location,
        attendees = attendees.map { Attendee(name = it.name, email = it.email) },
        description = description,
        pendingSync = pendingSync,
        done = done,
        createdAt = createdAt,
    )

    private fun ServerListItem.toDto() = LocalListItemDto(
        id = id,
        text = text,
        list = list,
        done = done,
        createdAt = createdAt,
    )

    private fun LocalListItemDto.toDomain() = ServerListItem(
        id = id,
        text = text,
        list = list,
        done = done,
        createdAt = createdAt,
    )

    companion object {
        private const val MAX_ITEMS = 200
        private val ACTIONS_JSON = stringPreferencesKey("local_actions_json")
        private val LIST_ITEMS_JSON = stringPreferencesKey("local_list_items_json")
    }
}

typealias LocalActionStoreImpl = LocalActionStore

@Serializable
internal data class LocalAttendeeDto(
    val name: String,
    val email: String? = null,
)

@Serializable
internal data class LocalActionItemDto(
    val id: String,
    val type: String,
    val title: String,
    val atEpochMilli: Long? = null,
    val endEpochMilli: Long? = null,
    val location: String? = null,
    val attendees: List<LocalAttendeeDto> = emptyList(),
    val description: String? = null,
    val pendingSync: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long = 0L,
)

@Serializable
internal data class LocalListItemDto(
    val id: String,
    val text: String,
    val list: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
)
