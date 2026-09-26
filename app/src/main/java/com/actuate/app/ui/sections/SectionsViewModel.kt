package com.actuate.app.ui.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerList
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.ServerRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SectionsState(
    val lists: List<ServerList> = emptyList(),
    val today: List<ServerActionItem> = emptyList(),
    val tomorrow: List<ServerActionItem> = emptyList(),
    val serverConfigured: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class SectionsViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: AppSettingsRepository,
    private val localActionStore: LocalActionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SectionsState())
    val state: StateFlow<SectionsState> = _state
    private val listMutex = Mutex()
    private var undoLists: List<ServerList>? = null
    private var undoDeleted: Set<String> = emptySet()

    fun createList(name: String) = changeLists { lists ->
        if (name.isBlank() || lists.any { it.name.equals(name.trim(), true) }) lists else lists + ServerList(name.trim(), emptyList())
    }

    fun renameList(old: String, name: String) = changeLists { lists ->
        if (name.isBlank() || lists.any { it.name != old && it.name.equals(name.trim(), true) }) lists
        else lists.map { if (it.name == old) it.copy(name = name.trim(), items = it.items.map { item -> item.copy(list = name.trim()) }) else it }
    }

    fun deleteList(name: String) = changeLists { it.filterNot { list -> list.name == name } }
    fun editItem(item: ServerListItem, text: String) = changeLists { lists ->
        lists.map { list -> list.copy(items = list.items.map { if (it.id == item.id) it.copy(text = text.trim()) else it }) }
    }
    fun deleteItem(item: ServerListItem) = changeLists { lists -> lists.map { it.copy(items = it.items.filterNot { old -> old.id == item.id }) } }
    fun addItem(list: String, text: String) = changeLists { lists ->
        if (text.isBlank()) return@changeLists lists
        val targetName = list.ifBlank { "Todo" }
        val item = ServerListItem(java.util.UUID.randomUUID().toString(), text.trim(), targetName, false, System.currentTimeMillis())
        if (lists.any { it.name.equals(targetName, true) }) {
            lists.map { if (it.name.equals(targetName, true)) it.copy(items = it.items + item) else it }
        } else {
            lists + ServerList(targetName, listOf(item))
        }
    }

    private fun changeLists(transform: (List<ServerList>) -> List<ServerList>) {
        viewModelScope.launch { listMutex.withLock {
            val before = _state.value.lists
            val after = transform(before)
            if (before == after) return@withLock
            undoLists = before
            undoDeleted = localActionStore.getDeletedListItemIds()
            val removed = before.flatMap { it.items }.map { it.id }.toSet() - after.flatMap { it.items }.map { it.id }.toSet()
            localActionStore.saveDeletedListItemIds(undoDeleted + removed)
            localActionStore.saveListNames(after.map { it.name }.toSet())
            localActionStore.saveListItems(after.flatMap { it.items })
            _state.value = _state.value.copy(lists = after)
        } }
    }

    fun undoListChange() {
        viewModelScope.launch { listMutex.withLock {
            val previous = undoLists ?: return@withLock
            localActionStore.saveDeletedListItemIds(undoDeleted)
            localActionStore.saveListNames(previous.map { it.name }.toSet())
            localActionStore.saveListItems(previous.flatMap { it.items })
            _state.value = _state.value.copy(lists = previous)
            undoLists = null
        } }
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cachedActions = localActionStore.getActions()
            val cachedListItems = localActionStore.getListItems()
            val listNames = localActionStore.getListNames()
            val deletedIds = localActionStore.getDeletedListItemIds()
            val cachedLists = cachedListItems.groupBy { it.list }
                .map { (name, items) -> ServerList(name, items) }
                .let { lists -> lists + (listNames - lists.map { it.name }.toSet()).map { ServerList(it, emptyList()) } }
            val zone = ZoneId.systemDefault()
            val todayDate = LocalDate.now(zone)
            val tomorrowDate = todayDate.plusDays(1)
            val cachedToday = cachedActions.filterForDate(todayDate, zone)
            val cachedTomorrow = cachedActions.filterForDate(tomorrowDate, zone)
            val configured = settingsRepository.readServerConfig().isConfigured
            val hasLocalData = cachedToday.isNotEmpty() || cachedTomorrow.isNotEmpty() || cachedLists.isNotEmpty()

            _state.value = _state.value.copy(
                today = cachedToday,
                tomorrow = cachedTomorrow,
                lists = cachedLists,
                serverConfigured = configured || hasLocalData,
                loading = configured,
                error = null,
            )

            if (!configured) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }

            val listsResult = serverRepository.fetchLists()
            val actionsResult = serverRepository.fetchActions()

            val serverLists = listsResult.getOrNull()
            val serverActions = actionsResult.getOrNull()

            val finalActions = if (serverActions != null) {
                val serverIds = serverActions.map { it.id }.toSet()
                val merged = serverActions + cachedActions.filter { it.id !in serverIds }
                localActionStore.saveActions(merged)
                merged
            } else {
                cachedActions
            }

            val finalLists = if (serverLists != null) {
                val serverItems = serverLists.flatMap { it.items }.filterNot { it.id in deletedIds }
                val serverItemIds = serverItems.map { it.id }.toSet()
                val localOnlyItems = cachedListItems.filter { it.id !in serverItemIds }
                val mergedItems = serverItems.map { sItem ->
                    cachedListItems.find { it.id == sItem.id } ?: sItem
                } + localOnlyItems
                localActionStore.saveListItems(mergedItems)
                mergedItems.groupBy { it.list }.map { (name, items) -> ServerList(name, items) }
                    .let { lists -> lists + (listNames - lists.map { it.name }.toSet()).map { ServerList(it, emptyList()) } }
            } else {
                cachedLists
            }

            val finalToday = finalActions.filterForDate(todayDate, zone)
            val finalTomorrow = finalActions.filterForDate(tomorrowDate, zone)

            _state.value = SectionsState(
                lists = finalLists,
                today = finalToday,
                tomorrow = finalTomorrow,
                serverConfigured = true,
                loading = false,
                error = listOfNotNull(
                    listsResult.exceptionOrNull(),
                    actionsResult.exceptionOrNull(),
                ).firstOrNull()?.message,
            )
        }
    }

    fun toggleItem(item: ServerListItem) {
        val newDone = !item.done
        val current = _state.value
        val updated = current.lists.map { list ->
            if (list.items.any { it.id == item.id }) {
                list.copy(
                    items = list.items.map {
                        if (it.id == item.id) it.copy(done = newDone) else it
                    },
                )
            } else {
                list
            }
        }
        _state.value = current.copy(lists = updated)
        viewModelScope.launch {
            localActionStore.updateListItemDone(item.id, newDone)
            serverRepository.setItemDone(item.id, newDone)
        }
    }

    private fun List<ServerActionItem>.filterForDate(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ServerActionItem> {
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        return filter { it.type == "calendar_event" || it.type == "reminder" || it.type == "task" }
            .filter { item ->
                val at = item.at
                at != null && at >= startOfDay && at < endOfDay
            }
            .sortedBy { it.at }
    }
}
