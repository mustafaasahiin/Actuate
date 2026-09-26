package com.actuate.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.ServerRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CalendarState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val actionsByDate: Map<LocalDate, List<ServerActionItem>> = emptyMap(),
    val selectedDayActions: List<ServerActionItem> = emptyList(),
    val serverConfigured: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

class CalendarViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: AppSettingsRepository,
    private val localActionStore: LocalActionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state

    /** Set while a delete/send is in flight so the UI can show progress and ignore re-taps. */
    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting

    init {
        refresh()
    }

    fun selectDate(date: LocalDate) {
        val actions = _state.value.actionsByDate[date].orEmpty()
        _state.value = _state.value.copy(
            selectedDate = date,
            selectedDayActions = actions,
        )
    }

    /**
     * Cancels the event without sending any message. Removes it from the
     * server (best-effort) and always from the local cache, then reloads.
     */
    fun requestDelete(action: ServerActionItem) {
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            runCatching { serverRepository.deleteEvent(action.id) }
            localActionStore.deleteAction(action.id)
            removeFromSelection(action.id)
            _deleting.value = false
            refresh()
        }
    }

    /**
     * Cancels the event and sends the (user-edited) cancellation message to
     * the first attendee with an email address. Deletion always happens; a
     * send failure is surfaced in [CalendarState.error] but never blocks the
     * cancellation itself.
     */
    fun requestDeleteWithSend(action: ServerActionItem, message: String) {
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            val sendResult = sendCancellationMessage(action, message)
            runCatching { serverRepository.deleteEvent(action.id) }
            localActionStore.deleteAction(action.id)
            removeFromSelection(action.id)
            _deleting.value = false
            if (sendResult.isFailure) {
                _state.value = _state.value.copy(
                    error = "Cancellation message could not be sent. The event was still cancelled.",
                )
            }
            refresh()
        }
    }

    private suspend fun sendCancellationMessage(action: ServerActionItem, message: String): Result<Unit> {
        // Multiple attendees: send to the first one that has a usable email.
        val recipient = action.attendees.firstOrNull { !it.email.isNullOrBlank() }
        val email = recipient?.email
        if (email.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No contact info available for send"))
        }
        return serverRepository.sendCancellationMessage(action.id, email, message)
    }

    private fun removeFromSelection(actionId: String) {
        _state.value = _state.value.copy(
            selectedDayActions = _state.value.selectedDayActions.filterNot { it.id == actionId },
        )
    }

    fun previousMonth() {
        val prev = _state.value.currentMonth.minusMonths(1)
        _state.value = _state.value.copy(currentMonth = prev)
    }

    fun nextMonth() {
        val next = _state.value.currentMonth.plusMonths(1)
        _state.value = _state.value.copy(currentMonth = next)
    }

    fun goToToday() {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val actions = _state.value.actionsByDate[today].orEmpty()
        _state.value = _state.value.copy(
            currentMonth = month,
            selectedDate = today,
            selectedDayActions = actions,
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val cachedActions = runCatching { localActionStore.getActions() }.getOrDefault(emptyList())
            val zone = ZoneId.systemDefault()
            val initialGrouped = groupActionsByDate(cachedActions, zone)
            val selected = _state.value.selectedDate
            val configured = runCatching { settingsRepository.readServerConfig().isConfigured }.getOrDefault(false)

            _state.value = _state.value.copy(
                actionsByDate = initialGrouped,
                selectedDayActions = initialGrouped[selected].orEmpty(),
                serverConfigured = configured || cachedActions.isNotEmpty(),
                loading = configured,
                error = null,
            )

            if (!configured) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }

            val actionsResult = serverRepository.fetchActions()
            val serverActions = actionsResult.getOrNull()
            val finalActions = if (serverActions != null) {
                val serverIds = serverActions.map { it.id }.toSet()
                val merged = serverActions + cachedActions.filter { it.id !in serverIds }
                runCatching { localActionStore.saveActions(merged) }
                merged
            } else {
                cachedActions
            }

            val grouped = groupActionsByDate(finalActions, zone)
            _state.value = _state.value.copy(
                actionsByDate = grouped,
                selectedDayActions = grouped[_state.value.selectedDate].orEmpty(),
                serverConfigured = true,
                loading = false,
                error = actionsResult.exceptionOrNull()?.let { it.message ?: "Couldn't refresh calendar" },
            )
        }
    }

    private fun groupActionsByDate(
        actions: List<ServerActionItem>,
        zone: ZoneId,
    ): Map<LocalDate, List<ServerActionItem>> {
        return actions
            .filter { it.type == "calendar_event" || it.type == "reminder" || it.type == "task" }
            .filter { it.at != null }
            .groupBy { item ->
                item.at!!.atZone(zone).toLocalDate()
            }
            .mapValues { (_, items) ->
                items.sortedBy { it.at }
            }
    }

    companion object {
        /**
         * Short, polite, context-aware cancellation message including the
         * person's name and the original time. Shared by the preview dialog
         * so what the user edits is exactly what gets sent.
         */
        fun buildCancellationMessage(action: ServerActionItem): String {
            val attendee = action.attendees.firstOrNull { it.name.isNotBlank() }
            val attendeeName = attendee?.name?.trim()?.takeIf { it.isNotBlank() } ?: "there"
            val timeText = formatCancellableTime(action)
            return "Hi $attendeeName, I'm sorry but I need to cancel \"${
                action.title.take(60)
            }\" scheduled for $timeText. Let me know if we should reschedule."
        }

        fun formatCancellableTime(action: ServerActionItem): String {
            val at = action.at ?: return "the scheduled time"
            val zone = ZoneId.systemDefault()
            val ldt = LocalDateTime.ofInstant(at, zone)
            val today = LocalDate.now(zone)
            val day = when (ldt.toLocalDate()) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                else -> ldt.toLocalDate().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
            }
            return "$day at ${String.format(Locale.getDefault(), "%d:%02d %s",
                if (ldt.hour % 12 == 0) 12 else ldt.hour % 12,
                ldt.minute,
                if (ldt.hour < 12) "AM" else "PM",
            )}"
        }
    }
}
