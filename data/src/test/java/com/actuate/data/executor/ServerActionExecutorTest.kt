package com.actuate.data.executor

import com.actuate.data.auth.GoogleCalendarAuthManager
import com.actuate.data.calendar.GoogleCalendarAuthException
import com.actuate.data.calendar.GoogleCalendarInsertResult
import com.actuate.data.calendar.GoogleCalendarSyncService
import com.actuate.data.network.ServerException
import com.actuate.data.reminders.LocalReminderScheduler
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerListItem
import com.actuate.domain.repository.LocalActionStore
import com.actuate.domain.repository.ServerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerActionExecutorTest {

    private val serverRepository: ServerRepository = mockk()
    private val reminderScheduler: LocalReminderScheduler = mockk(relaxed = true)
    private val localActionStore: LocalActionStore = mockk(relaxed = true)

    private val executor = ServerActionExecutor(
        serverRepository = serverRepository,
        reminderScheduler = reminderScheduler,
        localActionStore = localActionStore,
    )

    private val testStart = Instant.parse("2026-09-07T15:00:00Z")

    @Test
    fun `calendar event success saves to localActionStore`() = runTest {
        val action = ParsedAction.Calendar(
            id = "cal-1",
            title = "Team Meeting",
            start = testStart,
            end = null,
        )
        coEvery { serverRepository.execute(any()) } returns Result.success(
            listOf(
                ExecutionResult(
                    actionId = "cal-1",
                    destination = Destination.CALENDAR,
                    success = true,
                    message = "Event added to Google Calendar",
                )
            )
        )

        val result = executor.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.CALENDAR, result.destination)
        coVerify {
            localActionStore.addAction(
                match {
                    it.id == "cal-1" &&
                        it.title == "Team Meeting" &&
                        it.type == "calendar_event" &&
                        it.at == testStart
                }
            )
        }
    }

    @Test
    fun `reminder success schedules locally and saves to localActionStore`() = runTest {
        val action = ParsedAction.Reminder(
            id = "rem-1",
            title = "Buy groceries",
            dueAt = testStart,
        )
        coEvery { serverRepository.execute(any()) } returns Result.success(
            listOf(
                ExecutionResult(
                    actionId = "rem-1",
                    destination = Destination.REMINDERS,
                    success = true,
                    message = "Reminder scheduled",
                )
            )
        )

        val result = executor.execute(action)

        assertTrue(result.success)
        coVerify { reminderScheduler.schedule("Buy groceries", testStart, null) }
        coVerify {
            localActionStore.addAction(
                match {
                    it.id == "rem-1" &&
                        it.title == "Buy groceries" &&
                        it.type == "reminder" &&
                        it.at == testStart
                }
            )
        }
    }

    @Test
    fun `list item success saves to localActionStore`() = runTest {
        val action = ParsedAction.ListItem(
            id = "list-1",
            text = "Milk and eggs",
            list = "Groceries",
        )
        coEvery { serverRepository.execute(any()) } returns Result.success(
            listOf(
                ExecutionResult(
                    actionId = "list-1",
                    destination = Destination.NOTION,
                    success = true,
                    message = "Item added to Notion list",
                )
            )
        )

        val result = executor.execute(action)

        assertTrue(result.success)
        coVerify {
            localActionStore.addListItem(
                match {
                    it.id == "list-1" &&
                        it.text == "Milk and eggs" &&
                        it.list == "Groceries" &&
                        !it.done
                }
            )
        }
    }

    @Test
    fun `offline reminder schedules locally and persists locally`() = runTest {
        val action = ParsedAction.Reminder(
            id = "rem-off",
            title = "Stretch",
            dueAt = testStart,
        )
        coEvery { serverRepository.execute(any()) } returns Result.failure(IOException("No connection"))

        val result = executor.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.REMINDERS, result.destination)
        assertTrue(result.message.contains("offline"))
        coVerify { reminderScheduler.schedule("Stretch", testStart, null) }
        coVerify {
            localActionStore.addAction(
                match { it.id == "rem-off" && it.title == "Stretch" && it.type == "reminder" }
            )
        }
    }

    @Test
    fun `offline list item persists locally and succeeds`() = runTest {
        val action = ParsedAction.ListItem(
            id = "list-off",
            text = "Bread",
            list = "Shopping",
        )
        coEvery { serverRepository.execute(any()) } returns Result.failure(IOException("Server unreachable"))

        val result = executor.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.NOTION, result.destination)
        assertTrue(result.message.contains("offline"))
        coVerify {
            localActionStore.addListItem(
                match { it.id == "list-off" && it.text == "Bread" && it.list == "Shopping" }
            )
        }
    }

    @Test
    fun `offline calendar event persists locally and succeeds`() = runTest {
        val action = ParsedAction.Calendar(
            id = "cal-off",
            title = "Doctor appointment",
            start = testStart,
            end = null,
        )
        coEvery { serverRepository.execute(any()) } returns Result.failure(IOException("Connect timed out"))

        val result = executor.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.CALENDAR, result.destination)
        assertTrue(result.message.contains("offline"))
        coVerify {
            localActionStore.addAction(
                match {
                    it.id == "cal-off" &&
                        it.title == "Doctor appointment" &&
                        it.type == "calendar_event" &&
                        it.pendingSync
                }
            )
        }
    }

    @Test
    fun `offline calendar event is stored with pendingSync true, end, location, and description`() = runTest {
        val testEnd = testStart.plusSeconds(5400)
        val action = ParsedAction.Calendar(
            id = "cal-off-meta",
            title = "Quarterly Planning",
            start = testStart,
            end = testEnd,
            location = "Building A Room 204",
            description = "Q3 roadmap review",
        )
        coEvery { serverRepository.execute(any()) } returns Result.failure(IOException("No network route"))

        val result = executor.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.CALENDAR, result.destination)
        coVerify {
            localActionStore.addAction(
                match {
                    it.id == "cal-off-meta" &&
                        it.title == "Quarterly Planning" &&
                        it.type == "calendar_event" &&
                        it.at == testStart &&
                        it.end == testEnd &&
                        it.location == "Building A Room 204" &&
                        it.description == "Q3 roadmap review" &&
                        it.pendingSync
                }
            )
        }
    }

    @Test
    fun `offline calendar event with null end defaults to start plus 1 hour`() = runTest {
        val action = ParsedAction.Calendar(
            id = "cal-off-default-end",
            title = "Quick Sync",
            start = testStart,
            end = null,
            location = "Coffee Shop",
            description = "Catch up",
        )
        coEvery { serverRepository.execute(any()) } returns Result.failure(IOException("No network route"))

        val result = executor.execute(action)

        assertTrue(result.success)
        coVerify {
            localActionStore.addAction(
                match {
                    it.id == "cal-off-default-end" &&
                        it.at == testStart &&
                        it.end == testStart.plusSeconds(3600) &&
                        it.location == "Coffee Shop" &&
                        it.description == "Catch up" &&
                        it.pendingSync
                }
            )
        }
    }

    @Test
    fun `server exception does not save to localActionStore`() = runTest {
        val action = ParsedAction.Calendar(
            id = "cal-err",
            title = "Failing Event",
            start = testStart,
            end = null,
        )
        coEvery { serverRepository.execute(any()) } returns Result.failure(ServerException(400, "Invalid payload"))

        val result = executor.execute(action)

        assertFalse(result.success)
        coVerify(exactly = 0) { localActionStore.addAction(any()) }
    }

    @Test
    fun `direct google calendar sync success saves locally and returns success`() = runTest {
        val googleCalendarSyncService: GoogleCalendarSyncService = mockk()
        val googleCalendarAuthManager: GoogleCalendarAuthManager = mockk()
        every { googleCalendarAuthManager.isConnected() } returns true

        val executorWithGCal = ServerActionExecutor(
            serverRepository = serverRepository,
            reminderScheduler = reminderScheduler,
            localActionStore = localActionStore,
            googleCalendarSyncService = googleCalendarSyncService,
            googleCalendarAuthManager = googleCalendarAuthManager,
        )

        val action = ParsedAction.Calendar(
            id = "cal-g1",
            title = "1-on-1 with Alex",
            start = testStart,
            end = null,
        )

        coEvery { googleCalendarSyncService.insertEvent(action) } returns Result.success(
            GoogleCalendarInsertResult(eventId = "g-evt-1", htmlLink = "https://calendar.google.com/event?eid=1"),
        )

        val result = executorWithGCal.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.CALENDAR, result.destination)
        assertEquals("\"1-on-1 with Alex\" added to Google Calendar", result.message)
        coVerify(exactly = 0) { serverRepository.execute(any()) }
        coVerify {
            localActionStore.addAction(
                match { it.id == "cal-g1" && it.title == "1-on-1 with Alex" && it.type == "calendar_event" }
            )
        }
    }

    @Test
    fun `direct google calendar sync network error routes to offline handling`() = runTest {
        val googleCalendarSyncService: GoogleCalendarSyncService = mockk()
        val googleCalendarAuthManager: GoogleCalendarAuthManager = mockk()
        every { googleCalendarAuthManager.isConnected() } returns true

        val executorWithGCal = ServerActionExecutor(
            serverRepository = serverRepository,
            reminderScheduler = reminderScheduler,
            localActionStore = localActionStore,
            googleCalendarSyncService = googleCalendarSyncService,
            googleCalendarAuthManager = googleCalendarAuthManager,
        )

        val action = ParsedAction.Calendar(
            id = "cal-g2",
            title = "Airplane Mode Meeting",
            start = testStart,
            end = null,
        )

        coEvery { googleCalendarSyncService.insertEvent(action) } returns Result.failure(IOException("No network connection"))

        val result = executorWithGCal.execute(action)

        assertTrue(result.success)
        assertEquals(Destination.CALENDAR, result.destination)
        assertTrue(result.message.contains("(offline)"))
        coVerify(exactly = 0) { serverRepository.execute(any()) }
        coVerify {
            localActionStore.addAction(
                match { it.id == "cal-g2" && it.title == "Airplane Mode Meeting" && it.pendingSync }
            )
        }
    }

    @Test
    fun `direct google calendar sync auth failure returns failure but saves locally`() = runTest {
        val googleCalendarSyncService: GoogleCalendarSyncService = mockk()
        val googleCalendarAuthManager: GoogleCalendarAuthManager = mockk()
        every { googleCalendarAuthManager.isConnected() } returns true

        val executorWithGCal = ServerActionExecutor(
            serverRepository = serverRepository,
            reminderScheduler = reminderScheduler,
            localActionStore = localActionStore,
            googleCalendarSyncService = googleCalendarSyncService,
            googleCalendarAuthManager = googleCalendarAuthManager,
        )

        val action = ParsedAction.Calendar(
            id = "cal-g3",
            title = "Revoked Auth Meeting",
            start = testStart,
            end = null,
        )

        coEvery { googleCalendarSyncService.insertEvent(action) } returns Result.failure(
            GoogleCalendarAuthException("Google Calendar authentication failed: reconnection required"),
        )

        val result = executorWithGCal.execute(action)

        assertFalse(result.success)
        assertTrue(result.message.contains("reconnection required"))
        coVerify {
            localActionStore.addAction(
                match { it.id == "cal-g3" && it.title == "Revoked Auth Meeting" }
            )
        }
    }

    @Test
    fun `calendar action falls back to server repository when google calendar not connected`() = runTest {
        val googleCalendarSyncService: GoogleCalendarSyncService = mockk()
        val googleCalendarAuthManager: GoogleCalendarAuthManager = mockk()
        every { googleCalendarAuthManager.isConnected() } returns false

        val executorWithGCal = ServerActionExecutor(
            serverRepository = serverRepository,
            reminderScheduler = reminderScheduler,
            localActionStore = localActionStore,
            googleCalendarSyncService = googleCalendarSyncService,
            googleCalendarAuthManager = googleCalendarAuthManager,
        )

        val action = ParsedAction.Calendar(
            id = "cal-g4",
            title = "Fallback Meeting",
            start = testStart,
            end = null,
        )

        coEvery { serverRepository.execute(any()) } returns Result.success(
            listOf(
                ExecutionResult(
                    actionId = "cal-g4",
                    destination = Destination.CALENDAR,
                    success = true,
                    message = "Added via server",
                ),
            ),
        )

        val result = executorWithGCal.execute(action)

        assertTrue(result.success)
        assertEquals("Added via server", result.message)
        coVerify(exactly = 0) { googleCalendarSyncService.insertEvent(any()) }
        coVerify(exactly = 1) { serverRepository.execute(any()) }
    }
}
