package com.actuate.domain.usecase

import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.model.VoiceRunResult
import com.actuate.domain.model.label
import com.actuate.domain.parser.ActionParser
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import java.time.Instant
import java.util.UUID

/**
 * Full voice round-trip: transcript -> parse -> quota check -> execute ->
 * history. Returns per-action results plus the remaining free quota.
 */
class ExecuteVoiceCommandUseCase(
    private val parser: ActionParser,
    private val executor: ActionExecutor,
    private val quotaRepository: QuotaRepository,
    private val historyRepository: HistoryRepository,
) {

    suspend operator fun invoke(transcript: String, now: Instant = Instant.now()): VoiceRunResult {
        val parsed: ParsedActions = parser.parse(transcript, now)
        val executable = parsed.actions.filter {
            it is ParsedAction.Calendar || it is ParsedAction.ListItem || it is ParsedAction.Reminder
        }

        if (executable.isNotEmpty()) {
            val consumed = quotaRepository.tryConsume(executable.size)
            if (!consumed) {
                val blocked = executable.map { action ->
                    val result = com.actuate.domain.model.ExecutionResult(
                        actionId = action.id,
                        destination = com.actuate.domain.model.Destination.NONE,
                        success = false,
                        message = "Free tier: 3 actions per week. Upgrade for unlimited.",
                    )
                    record(action, result, transcript)
                    result
                }
                return VoiceRunResult(transcript, blocked, quotaRepository.remaining())
            }
        }

        val results = parsed.actions.map { action ->
            val result = executor.execute(action)
            record(action, result, transcript)
            result
        }
        return VoiceRunResult(transcript, results, quotaRepository.remaining())
    }

    private suspend fun record(action: ParsedAction, result: com.actuate.domain.model.ExecutionResult, transcript: String) {
        historyRepository.record(
            ActionRecord(
                id = result.actionId,
                timestamp = Instant.now(),
                transcript = transcript,
                summary = actionSummary(action),
                actionType = action.label,
                status = when {
                    result.success -> ActionStatus.DONE
                    else -> ActionStatus.FAILED
                },
                message = result.message,
                destination = result.destination,
            ),
        )
    }

    private fun actionSummary(action: ParsedAction): String = when (action) {
        is ParsedAction.Calendar ->
            "${action.title} · ${formatTime(action.start)}" +
                (action.location?.let { " · $it" } ?: "")
        is ParsedAction.ListItem -> "${action.text} → ${action.list ?: "general"}"
        is ParsedAction.Reminder -> action.title
        is ParsedAction.Unknown -> "Unrecognized: ${action.reason}"
    }

    private fun formatTime(instant: Instant): String {
        val zone = java.time.ZoneId.systemDefault()
        val ldt = java.time.LocalDateTime.ofInstant(instant, zone)
        val day = when (ldt.toLocalDate()) {
            java.time.LocalDate.now(zone) -> "Today"
            java.time.LocalDate.now(zone).plusDays(1) -> "Tomorrow"
            else -> ldt.toLocalDate().toString()
        }
        return "$day ${String.format("%d:%02d", ldt.hour, ldt.minute)}"
    }
}