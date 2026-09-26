package com.actuate.data.calendar

import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.repository.LocalActionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarOfflineSyncManagerTest {

    private val localActionStore: LocalActionStore = mockk(relaxed = true)
    private val googleCalendarSyncService: GoogleCalendarSyncService = mockk()
    private val googleCalendarAuthManager: GoogleCalendarAuthManager = mockk()

    private val syncManager = CalendarOfflineSyncManager(
        localActionStore = localActionStore,
        googleCalendarSyncService = googleCalendarSyncService,
        googleCalendarAuthManager = googleCalendarAuthManager,
    )

    private val testStart = Instant.parse("2026-09-08T10:00:00Z")
    private val testEnd = Instant.parse("2026-09-08T11:00:00Z")

    @Test
    fun `syncPendingEvents pushes pending events to GoogleCalendarSyncService and calls markActionSynced`() = runTest {
        val pendingEvent = ServerActionItem(
            id = "pending-cal-1",
            type = "calendar_event",
            title = "Design Sync",
            at = testStart,
            end = testEnd,
            location = "Boardroom",
            description = "Review mocks",
            pendingSync = true,
        )

        every { googleCalendarAuthManager.isConnected() } returns true
        coEvery { localActionStore.getPendingSyncActions() } returns listOf(pendingEvent)
        coEvery { googleCalendarSyncService.insertEvent(any()) } returns Result.success(
            GoogleCalendarInsertResult(eventId = "g-evt-123"),
        )

        val syncedCount = syncManager.syncPendingEvents()

        assertEquals(1, syncedCount)
        coVerify(exactly = 1) {
            googleCalendarSyncService.insertEvent(
                match { action ->
                    action.id == "pending-cal-1" &&
                        action.title == "Design Sync" &&
                        action.start == testStart &&
                        action.end == testEnd &&
                        action.location == "Boardroom" &&
                        action.description == "Review mocks"
                }
            )
        }
        coVerify(exactly = 1) { localActionStore.markActionSynced("pending-cal-1") }
    }

    @Test
    fun `syncPendingEvents returns 0 when Google Calendar is not connected`() = runTest {
        val pendingEvent = ServerActionItem(
            id = "pending-cal-2",
            type = "calendar_event",
            title = "Offline Meeting",
            at = testStart,
            pendingSync = true,
        )

        every { googleCalendarAuthManager.isConnected() } returns false
        coEvery { localActionStore.getPendingSyncActions() } returns listOf(pendingEvent)

        val syncedCount = syncManager.syncPendingEvents()

        assertEquals(0, syncedCount)
        coVerify(exactly = 0) { googleCalendarSyncService.insertEvent(any()) }
        coVerify(exactly = 0) { localActionStore.markActionSynced(any()) }
    }

    @Test
    fun `syncPendingEvents catches API failure and leaves item pending`() = runTest {
        val pendingEvent1 = ServerActionItem(
            id = "pending-fail-1",
            type = "calendar_event",
            title = "Event that Fails",
            at = testStart,
            pendingSync = true,
        )
        val pendingEvent2 = ServerActionItem(
            id = "pending-success-2",
            type = "calendar_event",
            title = "Event that Succeeds",
            at = testStart.plusSeconds(7200),
            pendingSync = true,
        )

        every { googleCalendarAuthManager.isConnected() } returns true
        coEvery { localActionStore.getPendingSyncActions() } returns listOf(pendingEvent1, pendingEvent2)
        coEvery {
            googleCalendarSyncService.insertEvent(match { it.id == "pending-fail-1" })
        } returns Result.failure(GoogleCalendarApiException(500, "Internal Server Error"))
        coEvery {
            googleCalendarSyncService.insertEvent(match { it.id == "pending-success-2" })
        } returns Result.success(GoogleCalendarInsertResult(eventId = "g-evt-456"))

        val syncedCount = syncManager.syncPendingEvents()

        assertEquals(1, syncedCount)
        coVerify(exactly = 0) { localActionStore.markActionSynced("pending-fail-1") }
        coVerify(exactly = 1) { localActionStore.markActionSynced("pending-success-2") }
    }

    @Test
    fun `syncPendingEvents handles unexpected exception during insert without crashing`() = runTest {
        val pendingEvent = ServerActionItem(
            id = "pending-throw",
            type = "calendar_event",
            title = "Crashy Event",
            at = testStart,
            pendingSync = true,
        )

        every { googleCalendarAuthManager.isConnected() } returns true
        coEvery { localActionStore.getPendingSyncActions() } returns listOf(pendingEvent)
        coEvery { googleCalendarSyncService.insertEvent(any()) } throws IOException("Socket closed")

        val syncedCount = syncManager.syncPendingEvents()

        assertEquals(0, syncedCount)
        coVerify(exactly = 0) { localActionStore.markActionSynced("pending-throw") }
    }

    @Test
    fun `syncPendingEvents ignores non-calendar actions`() = runTest {
        val pendingNote = ServerActionItem(
            id = "pending-note",
            type = "note",
            title = "My Note",
            at = testStart,
            pendingSync = true,
        )

        every { googleCalendarAuthManager.isConnected() } returns true
        coEvery { localActionStore.getPendingSyncActions() } returns listOf(pendingNote)

        val syncedCount = syncManager.syncPendingEvents()

        assertEquals(0, syncedCount)
        coVerify(exactly = 0) { googleCalendarSyncService.insertEvent(any()) }
        coVerify(exactly = 0) { localActionStore.markActionSynced(any()) }
    }
}
