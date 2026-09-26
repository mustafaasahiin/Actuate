package com.actuate.domain.usecase

import com.actuate.domain.entitlement.EntitlementProvider
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
class QuotaExceededException(
    message: String = "Free tier: 20 actions per week. Upgrade for unlimited.",
) : RuntimeException(message)

class ExecuteVoiceCommandUseCase(
    private val parser: ActionParser,
    private val executor: ActionExecutor,
    private val quotaRepository: QuotaRepository,
    private val historyRepository: HistoryRepository,
    private val entitlementProvider: EntitlementProvider? = null,
) {

    suspend fun execute(transcript: String, now: Instant = Instant.now()): VoiceRunResult =
        invoke(transcript, now)

    suspend operator fun invoke(transcript: String, now: Instant = Instant.now()): VoiceRunResult {
        val parsed: ParsedActions = parser.parse(transcript, now)
        return executePrepared(parsed)
    }

    suspend fun prepare(transcript: String): ParsedActions = parser.parse(transcript, Instant.now())

    suspend fun executePrepared(parsed: ParsedActions): VoiceRunResult {
        val transcript = parsed.rawTranscript
        val captureId = UUID.randomUUID().toString()
        val executable = parsed.actions.filter { it !is ParsedAction.Unknown }

        val isPro = entitlementProvider?.isPro() ?: false
        if (executable.isNotEmpty() && !isPro) {
            val consumed = quotaRepository.tryConsume(executable.size)
            if (!consumed) {
                throw QuotaExceededException()
            }
        }

        val results = parsed.actions.map { action ->
            val result = executeSafely(action)
            record(action, result, transcript, captureId)
            result
        }
        val remaining = if (isPro) null else quotaRepository.remaining()
        return VoiceRunResult(transcript, results, remaining, parsed.actions, captureId)
    }

    /** Retry already charged, failed actions by their original IDs; never replay successes. */
    suspend fun retryFailed(previous: VoiceRunResult): VoiceRunResult {
        val failedIds = previous.executed.filterNot { it.success }.map { it.actionId }.toSet()
        val replacements = previous.actions.filter { it.id in failedIds }.map { action ->
            executeSafely(action).also { record(action, it, previous.transcript, previous.captureId) }
        }.associateBy { it.actionId }
        return previous.copy(executed = previous.executed.map { replacements[it.actionId] ?: it })
    }

    private suspend fun executeSafely(action: ParsedAction): com.actuate.domain.model.ExecutionResult = try {
        executor.execute(action)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        com.actuate.domain.model.ExecutionResult(action.id, com.actuate.domain.model.Destination.NONE, false,
            "Couldn’t save this action · Retry")
    }

    private suspend fun record(action: ParsedAction, result: com.actuate.domain.model.ExecutionResult, transcript: String, captureId: String) {
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
                captureId = captureId,
            ),
        )
    }

    private fun actionSummary(action: ParsedAction): String = when (action) {
        is ParsedAction.Calendar ->
            "${action.title} · ${formatTime(action.start)}" +
                (action.location?.let { " · $it" } ?: "")
        is ParsedAction.ListItem -> "${action.text} → ${action.list ?: "general"}"
        is ParsedAction.ListAction -> "${action.items.joinToString(", ")} → ${action.listName}"
        is ParsedAction.Task -> "${action.title}${action.priority?.let { " [$it]" } ?: ""}"
        is ParsedAction.Note -> action.content.take(40)
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
