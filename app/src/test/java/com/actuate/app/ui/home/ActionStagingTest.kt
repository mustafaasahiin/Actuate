package com.actuate.app.ui.home

import com.actuate.app.ui.sections.ActionStaging
import com.actuate.app.ui.sections.DayChoice
import com.actuate.domain.model.Attendee
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.model.Priority
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The staging deck is the interactive centrepiece of the overhaul, so its behaviour
 * is asserted here instead of by tapping: destination switching, time stepping, day
 * moves and in-place editing all mutate a `ParsedActions` list that must survive intact.
 */
class ActionStagingTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-09-18T10:00:00Z")

    private val meeting = ParsedAction.Calendar(
        id = "a1",
        title = "Design review",
        start = Instant.parse("2026-09-18T14:00:00Z"),
        end = Instant.parse("2026-09-18T14:30:00Z"),
        location = "Room 2",
        attendees = listOf(Attendee("Maya")),
    )

    private val eggs = ParsedAction.ListItem(id = "a2", text = "buy eggs", list = "shopping")

    private val ping = ParsedAction.Reminder(
        id = "a3",
        title = "Send the invoice",
        dueAt = Instant.parse("2026-09-18T18:00:00Z"),
        priority = Priority.HIGH,
    )

    private fun parsed() = ParsedActions(
        rawTranscript = "design review at 2, buy eggs, remind me at 6",
        actions = listOf(meeting, eggs, ping),
        source = ParserSource.RULES,
        confidence = 1f,
    )

    // ------------------------------------------------------------ list integrity

    @Test
    fun `editing one action replaces it in place and leaves its siblings untouched`() {
        val routed = ActionStaging.withDestination(meeting, Destination.REMINDERS, now, zone)
        val updated = ActionStaging.applyTo(parsed(), routed)

        assertEquals(3, updated.actions.size)
        assertEquals("a1", updated.actions[0].id)
        assertTrue(updated.actions[0] is ParsedAction.Reminder)
        // The two other intents must be bit-identical, not re-derived.
        assertEquals(eggs, updated.actions[1])
        assertEquals(ping, updated.actions[2])
        assertEquals(parsed().rawTranscript, updated.rawTranscript)
        assertEquals(ParserSource.RULES, updated.source)
    }

    @Test
    fun `switching destination preserves the action id so the deck keeps its card identity`() {
        for (target in Destination.entries) {
            val routed = ActionStaging.withDestination(meeting, target, now, zone)
            assertEquals("a1", routed.id)

            val updated = ActionStaging.applyTo(parsed(), routed)
            assertEquals(3, updated.actions.size)
            assertEquals("a1", updated.actions[0].id)
            assertEquals(routed, updated.actions[0])
        }
    }

    @Test
    fun `switching a calendar action to reminder keeps the title and the start time`() {
        val routed = ActionStaging.withDestination(meeting, Destination.REMINDERS, now, zone)

        assertTrue(routed is ParsedAction.Reminder)
        routed as ParsedAction.Reminder
        assertEquals("Design review", routed.title)
        assertEquals(Instant.parse("2026-09-18T14:00:00Z"), routed.dueAt)
    }

    @Test
    fun `switching a list item to calendar gives it a real slot instead of dropping it`() {
        val routed = ActionStaging.withDestination(eggs, Destination.CALENDAR, now, zone)

        assertTrue(routed is ParsedAction.Calendar)
        routed as ParsedAction.Calendar
        assertEquals("buy eggs", routed.title)
        assertEquals(LocalDate.of(2026, 9, 19), LocalDate.ofInstant(routed.start, zone))
    }

    @Test
    fun `an unknown action survives a destination switch without throwing`() {
        val unknown = ParsedAction.Unknown(id = "a9", reason = "no verb detected")
        val parsed = parsed().copy(actions = listOf(unknown))
        val routed = ActionStaging.withDestination(unknown, Destination.CALENDAR, now, zone)

        assertTrue(routed is ParsedAction.Unknown)
        assertEquals(unknown.reason, (routed as ParsedAction.Unknown).reason)
        assertEquals(1, ActionStaging.applyTo(parsed, routed).actions.size)
    }

    // --------------------------------------------------------------- time steps

    @Test
    fun `plus 15 minutes advances the start time by exactly 15 minutes`() {
        val stepped = ActionStaging.stepTime(meeting, Duration.ofMinutes(15), now, zone)

        assertTrue(stepped is ParsedAction.Calendar)
        stepped as ParsedAction.Calendar
        assertEquals(Instant.parse("2026-09-18T14:15:00Z"), stepped.start)
        assertEquals(Duration.ofMinutes(15), Duration.between(meeting.start, stepped.start))
        assertEquals(14 * 60 + 15, stepped.start.atZone(zone).let { it.hour * 60 + it.minute })
    }

    @Test
    fun `stepping a calendar action moves its end by the same delta, preserving duration`() {
        val before = Duration.between(meeting.start, meeting.end!!)
        val stepped = ActionStaging.stepTime(meeting, Duration.ofHours(1), now, zone) as ParsedAction.Calendar

        assertEquals(Instant.parse("2026-09-18T15:00:00Z"), stepped.start)
        assertEquals(Instant.parse("2026-09-18T15:30:00Z"), stepped.end)
        assertEquals(before, Duration.between(stepped.start, stepped.end!!))
    }

    @Test
    fun `minus 15 minutes steps a reminder back within the same day`() {
        val stepped = ActionStaging.stepTime(ping, Duration.ofMinutes(-15), now, zone) as ParsedAction.Reminder
        assertEquals(Instant.parse("2026-09-18T17:45:00Z"), stepped.dueAt)
    }

    @Test
    fun `stepping backwards past midnight rolls the date back deterministically`() {
        val lateNight = ParsedAction.Reminder(
            id = "mid",
            title = "Standup prep",
            dueAt = Instant.parse("2026-09-19T00:10:00Z"),
        )
        val stepped = ActionStaging.stepTime(
            lateNight,
            Duration.ofMinutes(-15),
            now,
            zone,
        ) as ParsedAction.Reminder

        // 00:10 on the 19th minus 15 minutes is 23:55 on the 18th — one day earlier.
        assertEquals(Instant.parse("2026-09-18T23:55:00Z"), stepped.dueAt)
        assertEquals(LocalDate.of(2026, 9, 18), LocalDate.ofInstant(stepped.dueAt!!, zone))
    }

    @Test
    fun `a step that would land in the past is refused and reported as not steppable`() {
        val soon = ParsedAction.Reminder(
            id = "soon",
            title = "Tea",
            dueAt = now.plus(Duration.ofMinutes(10)),
        )

        assertTrue(ActionStaging.canStep(soon, Duration.ofMinutes(15), now, zone))
        assertFalse(ActionStaging.canStep(soon, Duration.ofMinutes(-15), now, zone))
        // Refused means unchanged, not silently rescheduled.
        assertEquals(soon, ActionStaging.stepTime(soon, Duration.ofMinutes(-15), now, zone))
    }

    @Test
    fun `unscheduled actions ignore time steps instead of growing a fake timestamp`() {
        val note = ParsedAction.Note(id = "n1", content = "wifi password")
        assertFalse(ActionStaging.canStep(note, Duration.ofMinutes(15), now, zone))
        assertEquals(note, ActionStaging.stepTime(note, Duration.ofMinutes(15), now, zone))
        assertFalse(ActionStaging.isScheduled(note))
    }

    // ------------------------------------------------------------------ day moves

    @Test
    fun `tomorrow keeps the time of day and moves the date forward one day`() {
        val moved = ActionStaging.moveToDay(meeting, DayChoice.TOMORROW, now, zone) as ParsedAction.Calendar

        assertEquals(LocalDate.of(2026, 9, 19), LocalDate.ofInstant(moved.start, zone))
        assertEquals(14, moved.start.atZone(zone).hour)
    }

    @Test
    fun `next monday always lands on monday and never in the past`() {
        // 2026-09-18 is a Friday; next Monday is the 21st.
        val moved = ActionStaging.moveToDay(meeting, DayChoice.NEXT_MONDAY, now, zone) as ParsedAction.Calendar

        assertEquals(java.time.DayOfWeek.MONDAY, LocalDate.ofInstant(moved.start, zone).dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 21), LocalDate.ofInstant(moved.start, zone))
        assertTrue(moved.start.isAfter(now))
    }

    @Test
    fun `today with an already-past time rolls forward rather than creating a past event`() {
        val past = ParsedAction.Reminder(
            id = "past",
            title = "Morning standup",
            dueAt = Instant.parse("2026-09-18T09:00:00Z"),
        )
        val moved = ActionStaging.moveToDay(past, DayChoice.TODAY, now, zone) as ParsedAction.Reminder

        assertNotEquals(past.dueAt, moved.dueAt)
        assertTrue(moved.dueAt!!.isAfter(now))
        assertEquals(LocalDate.of(2026, 9, 19), LocalDate.ofInstant(moved.dueAt!!, zone))
    }

    @Test
    fun `today with a future time stays on today`() {
        val moved = ActionStaging.moveToDay(ping, DayChoice.TODAY, now, zone) as ParsedAction.Reminder
        assertEquals(LocalDate.of(2026, 9, 18), LocalDate.ofInstant(moved.dueAt!!, zone))
        assertEquals(18, moved.dueAt!!.atZone(zone).hour)
    }

    // ------------------------------------------------------------------- renaming

    @Test
    fun `renaming replaces the title of every action type that has one`() {
        assertEquals(
            "Design sync",
            (ActionStaging.withTitle(meeting, "Design sync") as ParsedAction.Calendar).title,
        )
        assertEquals(
            "buy bread",
            (ActionStaging.withTitle(eggs, "buy bread") as ParsedAction.ListItem).text,
        )
        assertEquals(
            "Send the invoice today",
            (ActionStaging.withTitle(ping, "Send the invoice today") as ParsedAction.Reminder).title,
        )
    }

    @Test
    fun `a blank rename is ignored so a card can never end up with no title`() {
        assertEquals(meeting, ActionStaging.withTitle(meeting, "   "))
        assertEquals(eggs, ActionStaging.withTitle(eggs, ""))
    }

    @Test
    fun `renaming trims surrounding whitespace`() {
        val renamed = ActionStaging.withTitle(meeting, "  Design sync  ") as ParsedAction.Calendar
        assertEquals("Design sync", renamed.title)
    }
}
