package com.actuate.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full permutation coverage for [withDestination].
 *
 * Every source action is pushed through every destination, so a new [ParsedAction] subtype or a new
 * [Destination] entry fails this suite instead of silently degrading at runtime.
 */
class ActionDestinationTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-09-18T10:00:00Z")
    private val tomorrowAt9: Instant = Instant.parse("2026-09-19T09:00:00Z")

    private fun action(): ParsedAction.Calendar = ParsedAction.Calendar(
        title = "Dentist appointment",
        start = Instant.parse("2026-09-18T15:00:00Z"),
        end = Instant.parse("2026-09-18T16:00:00Z"),
        location = "12 Bridge St",
        attendees = listOf(Attendee("Priya", "priya@example.com")),
        description = "Bring insurance card",
    )

    private fun reminder(): ParsedAction.Reminder = ParsedAction.Reminder(
        title = "Buy eggs",
        dueAt = Instant.parse("2026-09-18T20:00:00Z"),
        priority = Priority.HIGH,
    )

    private fun listItem(): ParsedAction.ListItem = ParsedAction.ListItem(
        text = "buy chicken",
        list = "shopping",
    )

    private fun listAction(): ParsedAction.ListAction =
        ParsedAction.ListAction(listName = "Groceries", isNewList = true, items = listOf("milk", "eggs"))

    private fun task(): ParsedAction.Task = ParsedAction.Task(
        title = "Send report",
        priority = Priority.MEDIUM,
        project = "work",
        dueDate = Instant.parse("2026-09-20T09:00:00Z"),
    )

    private fun note(): ParsedAction.Note = ParsedAction.Note(
        content = "Wifi password is hunter2\nand the router is in the closet",
        destination = "notes",
    )

    private fun unknown(): ParsedAction.Unknown = ParsedAction.Unknown(reason = "no verb detected")

    private fun everySource(): List<ParsedAction> =
        listOf(action(), reminder(), listItem(), listAction(), task(), note(), unknown())

    private fun everyTarget(): List<Destination> = Destination.entries.toList()

    // ---------------------------------------------------------------- coverage

    @Test
    fun `every source and target permutation is total and never throws`() {
        for (source in everySource()) {
            for (target in everyTarget()) {
                val result = runCatching { source.withDestination(target, now, zone) }
                assertTrue(
                    "withDestination($target) threw for ${source::class.simpleName}: " +
                        result.exceptionOrNull(),
                    result.isSuccess,
                )
                // The id must survive so the staging deck can keep its per-card identity.
                assertEquals(source.id, result.getOrThrow().id)
            }
        }
    }

    @Test
    fun `destinationOf maps each action type to its routed destination`() {
        assertEquals(Destination.CALENDAR, action().routedDestination)
        assertEquals(Destination.REMINDERS, reminder().routedDestination)
        assertEquals(Destination.NOTION, listItem().routedDestination)
        assertEquals(Destination.NOTION, listAction().routedDestination)
        assertEquals(Destination.LOCAL, task().routedDestination)
        assertEquals(Destination.LOCAL, note().routedDestination)
        assertEquals(Destination.NONE, unknown().routedDestination)
    }

    @Test
    fun `switching to the current destination is identity`() {
        val source = action()
        assertSame(source, source.withDestination(Destination.CALENDAR, now, zone))

        val item = listItem()
        assertSame(item, item.withDestination(Destination.NOTION, now, zone))

        val scheduled = reminder()
        assertSame(scheduled, scheduled.withDestination(Destination.REMINDERS, now, zone))
    }

    // ------------------------------------------------------- calendar -> reminder

    @Test
    fun `calendar to reminder transfers the title and extracts the start time as dueAt`() {
        val result = action().withDestination(Destination.REMINDERS, now, zone)

        assertTrue(result is ParsedAction.Reminder)
        result as ParsedAction.Reminder
        assertEquals("Dentist appointment", result.title)
        assertEquals(Instant.parse("2026-09-18T15:00:00Z"), result.dueAt)
    }

    @Test
    fun `calendar to reminder drops the scheduled end instead of inventing one`() {
        val result = action().withDestination(Destination.REMINDERS, now, zone) as ParsedAction.Reminder
        // A reminder has no notion of duration; only the start is meaningful.
        assertEquals(Instant.parse("2026-09-18T15:00:00Z"), result.dueAt)
    }

    // ------------------------------------------------------- reminder -> calendar

    @Test
    fun `reminder to calendar uses dueAt as start and adds one hour`() {
        val result = reminder().withDestination(Destination.CALENDAR, now, zone)

        assertTrue(result is ParsedAction.Calendar)
        result as ParsedAction.Calendar
        assertEquals("Buy eggs", result.title)
        assertEquals(Instant.parse("2026-09-18T20:00:00Z"), result.start)
        assertEquals(Instant.parse("2026-09-18T21:00:00Z"), result.end)
    }

    @Test
    fun `reminder to calendar clears location and attendees`() {
        val result = reminder().withDestination(Destination.CALENDAR, now, zone) as ParsedAction.Calendar
        assertNull(result.location)
        assertEquals(emptyList<Attendee>(), result.attendees)
    }

    @Test
    fun `reminder without a due time is promoted to tomorrow at nine`() {
        val floating = reminder().copy(dueAt = null)
        val result = floating.withDestination(Destination.CALENDAR, now, zone) as ParsedAction.Calendar

        assertEquals(tomorrowAt9, result.start)
        assertEquals(tomorrowAt9.plus(Duration.ofHours(1)), result.end)
    }

    // ------------------------------------------------------- -> list item

    @Test
    fun `calendar to list item keeps the title and infers a shopping category`() {
        val result = action().withDestination(Destination.NOTION, now, zone)

        assertTrue(result is ParsedAction.ListItem)
        result as ParsedAction.ListItem
        assertEquals("Dentist appointment", result.text)
        assertEquals("health", result.list)
    }

    @Test
    fun `reminder to list item keeps the title and the priority`() {
        val result = reminder().withDestination(Destination.NOTION, now, zone) as ParsedAction.ListItem

        assertEquals("Buy eggs", result.text)
        assertEquals("shopping", result.list)
        assertEquals(Priority.HIGH, result.priority)
    }

    @Test
    fun `task to list item keeps the explicit project instead of guessing a category`() {
        val result = task().withDestination(Destination.NOTION, now, zone) as ParsedAction.ListItem

        assertEquals("Send report", result.text)
        assertEquals("work", result.list)
        assertEquals(Priority.MEDIUM, result.priority)
    }

    @Test
    fun `note to list item keeps the full multi-line content`() {
        val result = note().withDestination(Destination.NOTION, now, zone) as ParsedAction.ListItem

        assertEquals("Wifi password is hunter2\nand the router is in the closet", result.text)
        assertEquals("notes", result.list)
    }

    @Test
    fun `local destination also lands on the list surface`() {
        val result = action().withDestination(Destination.LOCAL, now, zone) as ParsedAction.ListItem
        assertEquals("Dentist appointment", result.text)
    }

    @Test
    fun `list item keeps its own category when re-routed to another list surface`() {
        val item = listItem()
        val result = item.withDestination(Destination.LOCAL, now, zone)

        assertSame(item, result)
        assertEquals("shopping", (result as ParsedAction.ListItem).list)
    }

    // ------------------------------------------------------- list item -> scheduled

    @Test
    fun `list item to calendar gets a deterministic tomorrow-morning slot`() {
        val result = listItem().withDestination(Destination.CALENDAR, now, zone) as ParsedAction.Calendar

        assertEquals("buy chicken", result.title)
        assertEquals(tomorrowAt9, result.start)
        assertEquals(tomorrowAt9.plus(Duration.ofHours(1)), result.end)
        assertEquals(LocalDate.of(2026, 9, 19), LocalDate.ofInstant(result.start, zone))
    }

    @Test
    fun `list item to reminder keeps the text with no fabricated due time`() {
        val result = listItem().withDestination(Destination.REMINDERS, now, zone) as ParsedAction.Reminder

        assertEquals("buy chicken", result.title)
        assertNull(result.dueAt)
    }

    @Test
    fun `list item promotion honours the supplied zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val result = listItem().withDestination(Destination.CALENDAR, now, tokyo) as ParsedAction.Calendar

        // 2026-09-19T09:00+09:00 == 2026-09-19T00:00Z
        assertEquals(Instant.parse("2026-09-19T00:00:00Z"), result.start)
        assertNotEquals(tomorrowAt9, result.start)
    }

    // ------------------------------------------------------- unsupported sources

    @Test
    fun `list action cannot collapse into a single event and degrades to unknown`() {
        val source = listAction()

        val asCalendar = source.withDestination(Destination.CALENDAR, now, zone)
        val asReminder = source.withDestination(Destination.REMINDERS, now, zone)

        assertTrue(asCalendar is ParsedAction.Unknown)
        assertTrue(asReminder is ParsedAction.Unknown)
        assertTrue((asCalendar as ParsedAction.Unknown).reason.contains("Groceries"))
        assertEquals(source.id, asReminder.id)
    }

    @Test
    fun `list action stays intact on list surfaces`() {
        val source = listAction()
        assertSame(source, source.withDestination(Destination.NOTION, now, zone))
        assertSame(source, source.withDestination(Destination.LOCAL, now, zone))
    }

    @Test
    fun `unknown actions stay unknown and never throw`() {
        val source = unknown()
        for (target in everyTarget()) {
            val result = source.withDestination(target, now, zone)
            assertTrue(result is ParsedAction.Unknown)
            assertEquals(source.reason, (result as ParsedAction.Unknown).reason)
        }
    }

    @Test
    fun `NONE is a safe no-op rather than a data-losing downgrade`() {
        for (source in everySource()) {
            assertSame(source, source.withDestination(Destination.NONE, now, zone))
        }
    }

    // ------------------------------------------------------- round trips

    @Test
    fun `calendar to reminder and back preserves the identifying fields`() {
        val original = action()
        val roundTripped = original
            .withDestination(Destination.REMINDERS, now, zone)
            .withDestination(Destination.CALENDAR, now, zone) as ParsedAction.Calendar

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.title, roundTripped.title)
        assertEquals(original.start, roundTripped.start)
        assertEquals(original.start.plus(Duration.ofHours(1)), roundTripped.end)
    }

    @Test
    fun `list item to calendar and back preserves the text`() {
        val original = listItem()
        val roundTripped = original
            .withDestination(Destination.CALENDAR, now, zone)
            .withDestination(Destination.NOTION, now, zone) as ParsedAction.ListItem

        assertEquals(original.text, roundTripped.text)
        assertEquals("shopping", roundTripped.list)
    }

    @Test
    fun `blank titles fall back rather than producing empty cards`() {
        val blank = ParsedAction.ListItem(text = "   ", list = "general")
        val result = blank.withDestination(Destination.CALENDAR, now, zone) as ParsedAction.Calendar
        assertEquals("Untitled", result.title)
    }

    @Test
    fun `listCategoryFor classifies common phrasings and defaults safely`() {
        assertEquals("shopping", listCategoryFor("buy milk"))
        assertEquals("work", listCategoryFor("Meeting with Priya"))
        assertEquals("health", listCategoryFor("gym session"))
        assertEquals(DEFAULT_LIST_CATEGORY, listCategoryFor("water the plants"))
    }
}
