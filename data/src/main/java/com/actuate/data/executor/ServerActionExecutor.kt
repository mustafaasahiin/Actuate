package com.actuate.data.executor

import com.actuate.data.network.ServerException
import com.actuate.data.reminders.LocalReminderScheduler
import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.repository.ServerRepository
import java.io.IOException

/**
 * Server-authoritative executor. Actions are pushed to their destination
 * apps by the Actuate server (Google Calendar API, Notion API) so
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
) : ActionExecutor {

    override suspend fun execute(action: ParsedAction): ExecutionResult {
        val serverResult = serverRepository.execute(listOf(action))
        if (serverResult.isSuccess) {
            val serverOutcome = serverResult.getOrThrow().firstOrNull()
            val result = serverOutcome ?: failed(action, "Server returned no result")
            if (result.success && action is ParsedAction.Reminder) {
                scheduleReminderLocally(action)
            }
            return result
        }

        val error = serverResult.exceptionOrNull()
        if (error is ServerException) {
            return failed(action, error.message ?: "Server rejected this action (HTTP ${error.code})")
        }
        if (error is IOException) {
            return when (action) {
                is ParsedAction.Reminder -> {
                    scheduleReminderLocally(action)
                    ExecutionResult(
                        actionId = action.id,
                        destination = Destination.REMINDERS,
                        success = true,
                        message = "Reminder set for ${formatDue(action)} (offline)",
                    )
                }
                else -> failed(action, "Server unreachable — check your connection and try again")
            }
        }
        return failed(action, error?.message ?: "Execution failed")
    }

    private fun scheduleReminderLocally(action: ParsedAction.Reminder) {
        val dueAt = action.dueAt ?: java.time.Instant.now().plusSeconds(60)
        reminderScheduler.schedule(action.title, dueAt, action.priority?.name)
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
}