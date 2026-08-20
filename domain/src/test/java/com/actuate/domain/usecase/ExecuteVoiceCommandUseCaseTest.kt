package com.actuate.domain.usecase

import com.actuate.domain.executor.ActionExecutor
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.parser.ActionParser
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.QuotaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteVoiceCommandUseCaseTest {

    private val parser: ActionParser = mockk()
    private val executor: ActionExecutor = mockk()
    private val quotaRepository: QuotaRepository = mockk()
    private val historyRepository: HistoryRepository = mockk()

    private val useCase = ExecuteVoiceCommandUseCase(parser, executor, quotaRepository, historyRepository)

    private val now: Instant = Instant.parse("2026-08-19T10:00:00Z")

    private fun calendarAction(title: String = "Standup") =
        ParsedAction.Calendar(title = title, start = now, end = now.plusSeconds(3600))

    private fun parsed(vararg actions: ParsedAction) =
        ParsedActions("transcript", actions.toList(), ParserSource.LLM, 1f)

    @Test
    fun `executes actions and records history`() = runTest {
        val action = calendarAction()
        coEvery { parser.parse(any(), any()) } returns parsed(action)
        coEvery { quotaRepository.tryConsume(1) } returns true
        coEvery { quotaRepository.remaining() } returns 2
        coEvery { executor.execute(action) } returns
            ExecutionResult(action.id, Destination.CALENDAR, true, "added")
        coEvery { historyRepository.record(any()) } returns Unit

        val result = useCase("Schedule a standup", now)

        assertTrue(result.executed.single().success)
        assertEquals(2, result.remainingQuota)
        coVerify(exactly = 1) { historyRepository.record(match { it.status == ActionStatus.DONE }) }
    }

    @Test
    fun `blocks execution when quota is exhausted`() = runTest {
        val action = calendarAction()
        coEvery { parser.parse(any(), any()) } returns parsed(action)
        coEvery { quotaRepository.tryConsume(1) } returns false
        coEvery { quotaRepository.remaining() } returns 0
        coEvery { historyRepository.record(any()) } returns Unit

        val result = useCase("Schedule a standup", now)

        assertFalse(result.executed.single().success)
        assertEquals(Destination.NONE, result.executed.single().destination)
        assertTrue(result.executed.single().message.contains("3 actions per week"))
        coVerify(exactly = 0) { executor.execute(any()) }
        coVerify(exactly = 1) { historyRepository.record(match { it.status == ActionStatus.FAILED }) }
    }

    @Test
    fun `counts reminders toward the quota and records them as done`() = runTest {
        val reminder = ParsedAction.Reminder(title = "Call mom", dueAt = now)
        coEvery { parser.parse(any(), any()) } returns parsed(reminder)
        coEvery { quotaRepository.tryConsume(1) } returns true
        coEvery { quotaRepository.remaining() } returns 2
        coEvery { executor.execute(reminder) } returns
            ExecutionResult(reminder.id, Destination.REMINDERS, true, "Reminder set")
        coEvery { historyRepository.record(any()) } returns Unit

        val result = useCase("Remind me to call mom", now)

        assertTrue(result.executed.single().success)
        assertEquals(2, result.remainingQuota)
        coVerify(exactly = 1) { quotaRepository.tryConsume(1) }
        coVerify(exactly = 1) { historyRepository.record(match { it.status == ActionStatus.DONE }) }
    }
}