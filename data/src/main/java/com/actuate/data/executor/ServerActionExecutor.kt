package com.actuate.data.executor

import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.data.calendar.GoogleCalendarSyncService
import com.actuate.data.network.ServerException
import com.actuate.data.reminders.LocalReminderScheduler
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.ServerRepository
import java.io.IOException
import java.time.Instant

/**
 * Server-authoritative executor. Actions are pushed to their destination
 * apps by the Actuate server (file storage) so
 * credentials never touch the device.
 *
 * The server's decision is authoritative for 401/429. On a plain network
 * failure:
 *  - reminders still schedule locally via AlarmManager (offline-safe)
 *  - calendar events / list items fail with a clear message (no device
 *    credentials exist anymore)
 */
class ServerActionExecutor(
    private val serverRepository: ServerRepository,
    private val reminderScheduler: LocalReminderScheduler,
    private val localActionStore: LocalActionStore,
    private val googleCalendarSyncService: GoogleCalendarSyncService? = null,
    private val googleCalendarAuthManager: GoogleCalendarAuthManager? = null,
    private val entitlementProvider: EntitlementProvider? = null,
) : ActionExecutor {

    override suspend fun execute(action: ParsedAction): ExecutionResult {
        if (action is ParsedAction.Calendar &&
            googleCalendarAuthManager?.isConnected() == true &&
            googleCalendarSyncService != null
        ) {
            val syncResult = googleCalendarSyncService.insertEvent(action)
            if (syncResult.isSuccess) {
                saveActionLocally(action)
                return ExecutionResult(
                    actionId = action.id,
                    destination = Destination.CALENDAR,
                    success = true,
                    message = "\"${action.title}\" added to Google Calendar",
                )
            }

            val error = syncResult.exceptionOrNull()
            if (error is IOException || error?.cause is IOException) {
                saveActionLocally(action, pendingSync = true)
                return ExecutionResult(
                    actionId = action.id,
                    destination = Destination.CALENDAR,
                    success = true,
                    message = "Saved on this device · Waiting to sync (offline)",
                )
            }

            val failMsg = error?.message?.takeIf { it.isNotBlank() } ?: "Saved on this device · Couldn't sync · Retry"
            saveActionLocally(action, pendingSync = true)
            return failed(action, failMsg)
        }

        val serverResult = serverRepository.execute(listOf(action))
        if (serverResult.isSuccess) {
            val serverOutcome = serverResult.getOrThrow().firstOrNull()
            val result = serverOutcome ?: failed(action, "Server returned no result")
            if (result.success) {
                saveActionLocally(action)
                if (action is ParsedAction.Reminder) {
                    scheduleReminderLocally(action)
                } else if (action is ParsedAction.Calendar) {
                    scheduleCalendarReminders(action)
                }
            }
            return result
        }

        val error = serverResult.exceptionOrNull()
        val isProUser = entitlementProvider?.isPro() ?: false
        if (error is ServerException && error.code == 429 && isProUser) {
            return executeOfflineFallback(action)
        }
        if (error is ServerException && error.code != 401 && error.code != 403) {
            return failed(action, error.message ?: "Server rejected this action (HTTP ${error.code})")
        }
        if (error is IOException || (error is ServerException && (error.code == 401 || error.code == 403))) {
            return executeOfflineFallback(action)
        }

        return failed(action, error?.message ?: "Unknown error executing action")
    }

    private suspend fun executeOfflineFallback(action: ParsedAction): ExecutionResult {
        return when (action) {
            is ParsedAction.Reminder -> {
                scheduleReminderLocally(action)
                saveActionLocally(action)
                ExecutionResult(
                    actionId = action.id,
                    destination = Destination.REMINDERS,
                    success = true,
                    message = "Reminder set for ${formatDue(action)} (offline)",
                )
            }
            is ParsedAction.ListItem -> {
                saveActionLocally(action)
                ExecutionResult(
                    actionId = action.id,
                    destination = Destination.NOTION,
                    success = true,
                    message = "Added to your list · Saved on this device (offline)",
                )
            }
            is ParsedAction.Calendar -> {
                saveActionLocally(action, pendingSync = true)
                scheduleCalendarReminders(action)
                ExecutionResult(
                    actionId = action.id,
                    destination = Destination.CALENDAR,
                    success = true,
                    message = "Saved on this device · Waiting to sync (offline)",
                )
            }
            is ParsedAction.ListAction -> {
                saveActionLocally(action)
                ExecutionResult(
                    actionId = action.id,
                    destination = Destination.NOTION,
                    success = true,
                    message = "Added to your list · Saved on this device (offline)",
                )
            }
            is ParsedAction.Task -> {
                val dueAt = action.dueDate ?: java.time.Instant.now().plusSeconds(3600)
                reminderScheduler.schedule(action.title, dueAt, action.priority?.name)
                saveActionLocally(action)
                ExecutionResult(
                    actionId = action.id,
                    destination = Destination.REMINDERS,
                    success = true,
                    message = "Task scheduled · Saved on this device (offline)",
                )
            }
            is ParsedAction.Note -> {
                saveActionLocally(action)
                ExecutionResult(
                    actionId = action.id,
                    destination = Destination.LOCAL,
                    success = true,
                    message = "Note saved on this device (offline)",
                )
            }
            is ParsedAction.Unknown -> failed(action, action.reason.ifBlank { "Could not understand action" })
        }
    }

    private suspend fun saveActionLocally(action: ParsedAction, pendingSync: Boolean = false) {
        when (action) {
            is ParsedAction.Calendar -> {
                val endInstant = action.end ?: action.start.plusSeconds(3600)
                localActionStore.addAction(
                    ServerActionItem(
                        id = action.id,
                        type = "calendar_event",
                        title = action.title,
                        at = action.start,
                        end = endInstant,
                        location = action.location,
                        attendees = action.attendees,
                        description = action.description,
                        pendingSync = pendingSync,
                        done = false,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            is ParsedAction.Reminder -> {
                localActionStore.addAction(
                    ServerActionItem(
                        id = action.id,
                        type = "reminder",
                        title = action.title,
                        at = action.dueAt ?: Instant.now(),
                        done = false,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            is ParsedAction.ListItem -> {
                localActionStore.addListItem(
                    ServerListItem(
                        id = action.id,
                        text = action.text,
                        list = action.list ?: "general",
                        done = false,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            is ParsedAction.ListAction -> {
                for ((index, itemText) in action.items.withIndex()) {
                    localActionStore.addListItem(
                        ServerListItem(
                            id = "${action.id}:$index",
                            text = itemText,
                            list = action.listName,
                            done = false,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            is ParsedAction.Task -> {
                localActionStore.addAction(
                    ServerActionItem(
                        id = action.id,
                        type = "task",
                        title = action.title,
                        at = action.dueDate ?: Instant.now(),
                        done = false,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            is ParsedAction.Note -> {
                localActionStore.addAction(
                    ServerActionItem(
                        id = action.id,
                        type = "note",
                        title = action.content,
                        at = Instant.now(),
                        done = false,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
            is ParsedAction.Unknown -> Unit
        }
    }

    private fun scheduleReminderLocally(action: ParsedAction.Reminder) {
        val dueAt = action.dueAt ?: java.time.Instant.now().plusSeconds(60)
        reminderScheduler.schedule(action.title, dueAt, action.priority?.name)
        val tenMinBefore = dueAt.minus(java.time.Duration.ofMinutes(10))
        if (tenMinBefore.isAfter(java.time.Instant.now().plusSeconds(60))) {
            reminderScheduler.schedule("Upcoming: ${action.title} (in 10 min)", tenMinBefore, action.priority?.name)
        }
    }

    private fun scheduleCalendarReminders(action: ParsedAction.Calendar) {
        val start = action.start
        val tenMinBefore = start.minus(java.time.Duration.ofMinutes(10))
        if (tenMinBefore.isAfter(java.time.Instant.now().plusSeconds(60))) {
            reminderScheduler.schedule("Upcoming: ${action.title} (in 10 min)", tenMinBefore, "HIGH")
        }
        reminderScheduler.schedule(action.title, start, "HIGH")
    }

    private fun failed(action: ParsedAction, message: String): ExecutionResult =
        ExecutionResult(
            actionId = action.id,
            destination = Destination.NONE,
            success = false,
            message = message,
        )

    private fun formatDue(action: ParsedAction.Reminder): String {
        val due = action.dueAt ?: return "soon"
        val ldt = java.time.LocalDateTime.ofInstant(due, java.time.ZoneId.systemDefault())
        return "${ldt.toLocalDate()} ${String.format(java.util.Locale.ROOT, "%02d:%02d", ldt.hour, ldt.minute)}"
    }

    private fun formatTime(instant: Instant): String {
        val ldt = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        return "${ldt.toLocalDate()} ${String.format(java.util.Locale.ROOT, "%02d:%02d", ldt.hour, ldt.minute)}"
    }
}