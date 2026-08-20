package com.actuate.app.ui.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerList
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.ServerRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SectionsState(
    val lists: List<ServerList> = emptyList(),
    val today: List<ServerActionItem> = emptyList(),
    val serverConfigured: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Powers the Today / Lists sections under the voice bubble. Data is
 * server-backed so it persists across devices and reinstalls; checkoffs sync
 * back to Notion best-effort.
 */
class SectionsViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SectionsState())
    val state: StateFlow<SectionsState> = _state

    fun refresh() {
        viewModelScope.launch {
            val configured = settingsRepository.readServerConfig().isConfigured
            if (!configured) {
                _state.value = _state.value.copy(
                    serverConfigured = false,
                    lists = emptyList(),
                    today = emptyList(),
                )
                return@launch
            }
            _state.value = _state.value.copy(loading = true, error = null)
            val listsResult = serverRepository.fetchLists()
            val actionsResult = serverRepository.fetchActions()
            val lists = listsResult.getOrNull().orEmpty()
            val actions = actionsResult.getOrNull().orEmpty()
            _state.value = SectionsState(
                lists = lists,
                today = actions.filterToday(),
                serverConfigured = true,
                loading = false,
                error = listOfNotNull(
                    listsResult.exceptionOrNull(),
                    actionsResult.exceptionOrNull(),
                ).firstOrNull()?.message,
            )
        }
    }

    /** Optimistic toggle: update locally, revert if the server disagrees. */
    fun toggleItem(item: ServerListItem) {
        val current = _state.value
        val updated = current.lists.map { list ->
            if (list.items.any { it.id == item.id }) {
                list.copy(
                    items = list.items.map {
                        if (it.id == item.id) it.copy(done = !it.done) else it
                    },
                )
            } else {
                list
            }
        }
        _state.value = current.copy(lists = updated)
        viewModelScope.launch {
            val result = serverRepository.setItemDone(item.id, !item.done)
            if (result.isFailure) {
                _state.value = current // revert on failure
            }
        }
    }

    private fun List<ServerActionItem>.filterToday(): List<ServerActionItem> {
        val startOfToday = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
        return filter { it.type == "calendar_event" || it.type == "reminder" }
            .filter { item ->
                val at = item.at
                at != null && at >= startOfToday
            }
            .sortedBy { it.at }
    }
}