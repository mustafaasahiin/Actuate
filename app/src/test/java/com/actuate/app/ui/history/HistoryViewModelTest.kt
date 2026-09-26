package com.actuate.app.ui.history

import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.model.Destination
import com.actuate.domain.repository.HistoryRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val now = Instant.now()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `groupedHistory groups records by captureId and calculates status accurately`() = runTest {
        val captureId = "cap-123"
        val transcript = "Meeting with Maya at 2 and buy oat milk"
        val record1 = ActionRecord(
            id = "act-1",
            timestamp = now.minusSeconds(60),
            transcript = transcript,
            summary = "Meeting with Maya at 2pm",
            actionType = "calendar_event",
            status = ActionStatus.DONE,
            destination = Destination.CALENDAR,
            captureId = captureId,
        )
        val record2 = ActionRecord(
            id = "act-2",
            timestamp = now.minusSeconds(55),
            transcript = transcript,
            summary = "Buy oat milk",
            actionType = "list_item",
            status = ActionStatus.FAILED,
            destination = Destination.LOCAL,
            captureId = captureId,
        )

        every { historyRepository.observeHistory() } returns flowOf(listOf(record1, record2))

        val viewModel = HistoryViewModel(historyRepository)
        val groups = viewModel.groupedHistory.value

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(captureId, group.captureId)
        assertEquals(transcript, group.transcript)
        assertEquals(2, group.actions.size)
        assertEquals(CaptureStatus.PARTIAL_SUCCESS, group.overallStatus)
    }

    @Test
    fun `records without captureId are isolated by their own id`() = runTest {
        val record1 = ActionRecord(
            id = "act-1",
            timestamp = now.minusSeconds(100),
            transcript = "First standalone thought",
            summary = "First thought",
            actionType = "reminder",
            status = ActionStatus.DONE,
            destination = Destination.REMINDERS,
            captureId = null,
        )
        val record2 = ActionRecord(
            id = "act-2",
            timestamp = now.minusSeconds(50),
            transcript = "Second standalone thought",
            summary = "Second thought",
            actionType = "reminder",
            status = ActionStatus.DONE,
            destination = Destination.REMINDERS,
            captureId = null,
        )

        every { historyRepository.observeHistory() } returns flowOf(listOf(record1, record2))

        val viewModel = HistoryViewModel(historyRepository)
        val groups = viewModel.groupedHistory.value

        assertEquals(2, groups.size)
        assertEquals(CaptureStatus.ALL_SUCCESS, groups[0].overallStatus)
        assertEquals(CaptureStatus.ALL_SUCCESS, groups[1].overallStatus)
    }
}
