package com.actuate.domain.usecase

import com.actuate.domain.entitlement.EntitlementProvider
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteVoiceCommandUseCaseTest {

    @Test
    fun `retry preserves capture and never executes successful work or consumes quota again`() = runTest {
        val saved = ParsedAction.ListItem(text = "Milk", list = "Shopping")
        val failed = ParsedAction.Reminder(title = "Call Maya", dueAt = Instant.now())
        val previous = com.actuate.domain.model.VoiceRunResult("milk and call Maya", listOf(
            ExecutionResult(saved.id, Destination.LOCAL, true, "Added"),
            ExecutionResult(failed.id, Destination.REMINDERS, false, "Failed")), 0, listOf(saved, failed), "capture-1")
        coEvery { executor.execute(failed) } returns ExecutionResult(failed.id, Destination.REMINDERS, true, "Scheduled")
        coEvery { historyRepository.record(any()) } returns Unit
        val result = useCase.retryFailed(previous)
        assertTrue(result.executed.all { it.success })
        assertEquals("capture-1", result.captureId)
        coVerify(exactly = 0) { executor.execute(saved) }
        coVerify(exactly = 0) { quotaRepository.tryConsume(any()) }
        coVerify { historyRepository.record(match { it.captureId == "capture-1" && it.id == failed.id }) }
    }

    @Test
    fun `preparing a capture performs no writes and preserves editable details`() = runTest {
        val action = calendarAction()
        coEvery { parser.parse(any(), any()) } returns parsed(action)
        val result = useCase.prepare("Meeting tomorrow")
        assertEquals(action, result.actions.single())
        coVerify(exactly = 0) { executor.execute(any()) }
        coVerify(exactly = 0) { quotaRepository.tryConsume(any()) }
        coVerify(exactly = 0) { historyRepository.record(any()) }
    }

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

        var thrown = false
        try {
            useCase("Schedule a standup", now)
        } catch (e: QuotaExceededException) {
            thrown = true
            assertEquals("Free tier: 20 actions per week. Upgrade for unlimited.", e.message)
        }
        assertTrue("Expected QuotaExceededException to be thrown", thrown)
        coVerify(exactly = 0) { executor.execute(any()) }
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

    @Test
    fun `free user executing 3 actions succeeds and decrements remaining quota`() = runTest {
        val entitlementProvider: EntitlementProvider = mockk()
        coEvery { entitlementProvider.isPro() } returns false
        val quotaRepo: QuotaRepository = mockk()
        val useCaseWithEntitlement = ExecuteVoiceCommandUseCase(
            parser = parser,
            executor = executor,
            quotaRepository = quotaRepo,
            historyRepository = historyRepository,
            entitlementProvider = entitlementProvider,
        )
        coEvery { historyRepository.record(any()) } returns Unit

        val action1 = calendarAction("Action 1")
        coEvery { parser.parse("action 1", any()) } returns parsed(action1)
        coEvery { quotaRepo.tryConsume(1) } returns true
        coEvery { quotaRepo.remaining() } returns 2
        coEvery { executor.execute(action1) } returns ExecutionResult(action1.id, Destination.CALENDAR, true, "OK 1")

        val result1 = useCaseWithEntitlement("action 1", now)
        assertTrue(result1.executed.single().success)
        assertEquals(2, result1.remainingQuota)

        val action2 = calendarAction("Action 2")
        coEvery { parser.parse("action 2", any()) } returns parsed(action2)
        coEvery { quotaRepo.remaining() } returns 1
        coEvery { executor.execute(action2) } returns ExecutionResult(action2.id, Destination.CALENDAR, true, "OK 2")

        val result2 = useCaseWithEntitlement("action 2", now)
        assertTrue(result2.executed.single().success)
        assertEquals(1, result2.remainingQuota)

        val action3 = calendarAction("Action 3")
        coEvery { parser.parse("action 3", any()) } returns parsed(action3)
        coEvery { quotaRepo.remaining() } returns 0
        coEvery { executor.execute(action3) } returns ExecutionResult(action3.id, Destination.CALENDAR, true, "OK 3")

        val result3 = useCaseWithEntitlement("action 3", now)
        assertTrue(result3.executed.single().success)
        assertEquals(0, result3.remainingQuota)

        coVerify(exactly = 3) { quotaRepo.tryConsume(1) }
        coVerify(exactly = 3) { executor.execute(any()) }
    }

    @Test
    fun `free user attempting a 4th action within rolling 7-day window is blocked`() = runTest {
        val entitlementProvider: EntitlementProvider = mockk()
        coEvery { entitlementProvider.isPro() } returns false
        val quotaRepo: QuotaRepository = mockk()
        val useCaseWithEntitlement = ExecuteVoiceCommandUseCase(
            parser = parser,
            executor = executor,
            quotaRepository = quotaRepo,
            historyRepository = historyRepository,
            entitlementProvider = entitlementProvider,
        )

        val action4 = calendarAction("Action 4")
        coEvery { parser.parse("action 4", any()) } returns parsed(action4)
        coEvery { quotaRepo.tryConsume(1) } returns false
        coEvery { quotaRepo.remaining() } returns 0
        coEvery { historyRepository.record(any()) } returns Unit

        var thrown = false
        try {
            useCaseWithEntitlement("action 4", now)
        } catch (e: QuotaExceededException) {
            thrown = true
            assertEquals("Free tier: 20 actions per week. Upgrade for unlimited.", e.message)
        }
        assertTrue("Expected QuotaExceededException to be thrown", thrown)
        coVerify(exactly = 0) { executor.execute(action4) }
    }

    @Test
    fun `pro user executes 5+ actions without being blocked by quota`() = runTest {
        val entitlementProvider: EntitlementProvider = mockk()
        coEvery { entitlementProvider.isPro() } returns true
        val quotaRepo: QuotaRepository = mockk()
        val useCasePro = ExecuteVoiceCommandUseCase(
            parser = parser,
            executor = executor,
            quotaRepository = quotaRepo,
            historyRepository = historyRepository,
            entitlementProvider = entitlementProvider,
        )
        coEvery { historyRepository.record(any()) } returns Unit

        for (i in 1..6) {
            val action = calendarAction("Pro Action $i")
            coEvery { parser.parse("command $i", any()) } returns parsed(action)
            coEvery { executor.execute(action) } returns
                ExecutionResult(action.id, Destination.CALENDAR, true, "Success $i")

            val result = useCasePro("command $i", now)

            assertTrue("Action $i should succeed", result.executed.single().success)
            assertEquals("Success $i", result.executed.single().message)
        }

        coVerify(exactly = 0) { quotaRepo.tryConsume(any()) }
        coVerify(exactly = 6) { executor.execute(any()) }
        coVerify(exactly = 6) { historyRepository.record(match { it.status == ActionStatus.DONE }) }
    }

    @Test
    fun `realistic end-to-end quota gating from 20 actions to blocked 21st action`() = runTest {
        val fakeQuotaRepo = object : QuotaRepository {
            private val timestamps = mutableListOf<Instant>()

            override fun observeRemaining(): Flow<Int> =
                flowOf(QuotaPolicy.remaining(timestamps, now))

            override suspend fun remaining(): Int = QuotaPolicy.remaining(timestamps, now)

            override suspend fun tryConsume(count: Int): Boolean {
                if (QuotaPolicy.canConsume(timestamps, now, count)) {
                    repeat(count) { timestamps.add(now) }
                    return true
                }
                return false
            }
        }

        val useCaseGated = ExecuteVoiceCommandUseCase(
            parser = parser,
            executor = executor,
            quotaRepository = fakeQuotaRepo,
            historyRepository = historyRepository,
        )
        coEvery { historyRepository.record(any()) } returns Unit

        val a1 = calendarAction("A1")
        coEvery { parser.parse("a1", any()) } returns parsed(a1)
        coEvery { executor.execute(a1) } returns ExecutionResult(a1.id, Destination.CALENDAR, true, "OK 1")
        val r1 = useCaseGated("a1", now)
        assertTrue(r1.executed.single().success)
        assertEquals(19, r1.remainingQuota)

        for (i in 2..20) {
            val a = calendarAction("A$i")
            coEvery { parser.parse("a$i", any()) } returns parsed(a)
            coEvery { executor.execute(a) } returns ExecutionResult(a.id, Destination.CALENDAR, true, "OK $i")
            val r = useCaseGated("a$i", now)
            assertTrue(r.executed.single().success)
            assertEquals(20 - i, r.remainingQuota)
        }

        val a21 = calendarAction("A21")
        coEvery { parser.parse("a21", any()) } returns parsed(a21)
        var thrown = false
        try {
            useCaseGated("a21", now)
        } catch (e: QuotaExceededException) {
            thrown = true
            assertEquals("Free tier: 20 actions per week. Upgrade for unlimited.", e.message)
        }
        assertTrue("Expected QuotaExceededException on 21st action", thrown)
        coVerify(exactly = 0) { executor.execute(a21) }
    }
}
