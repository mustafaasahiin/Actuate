package com.actuate.domain.parser

import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParserSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedActionParserTest {

    private val parser = RuleBasedActionParser()
    private val now: Instant = Instant.parse("2026-08-19T10:00:00Z")
    private val zone: ZoneId = ZoneId.systemDefault()

    @Test
    fun `parses the example sentence from the spec`() = runBlocking {
        val result = parser.parse(
            "Schedule a gym session with Alex tomorrow at 5 PM, " +
                "add buy chicken to my shopping list",
            now,
        )

        assertEquals(2, result.actions.size)
        assertEquals(ParserSource.RULES, result.source)

        val calendar = result.actions[0] as ParsedAction.Calendar
        assertEquals("Gym Session", calendar.title)
        assertEquals(listOf("Alex"), calendar.attendees)
        assertEquals(17, LocalTime.from(calendar.start.atZone(zone)).hour)
        assertEquals(
            LocalDate.now(zone).plusDays(1),
            LocalDate.from(calendar.start.atZone(zone)),
        )
        assertEquals(3600, calendar.end!!.epochSecond - calendar.start.epochSecond)

        val listItem = result.actions[1] as ParsedAction.ListItem
        assertEquals("buy chicken", listItem.text)
        assertEquals("shopping", listItem.list)
    }

    @Test
    fun `splits compound commands on and then and plus`() = runBlocking {
        val result = parser.parse(
            "book a standup tomorrow at 9 AM and add milk to the shopping list " +
                "then schedule lunch with mom on Friday at 1 PM",
            now,
        )

        assertEquals(3, result.actions.size)
        val calendar = result.actions[0] as ParsedAction.Calendar
        assertEquals("Standup", calendar.title)
        assertEquals(listOf("milk"), listOf((result.actions[1] as ParsedAction.ListItem).text))
        assertEquals("shopping", (result.actions[1] as ParsedAction.ListItem).list)
        val lunch = result.actions[2] as ParsedAction.Calendar
        assertEquals(listOf("mom"), lunch.attendees)
        assertEquals(LocalTime.of(13, 0), LocalTime.from(lunch.start.atZone(zone)))
    }

    @Test
    fun `extracts location and attendee from the same clause`() = runBlocking {
        val result = parser.parse(
            "schedule a dinner with Jane at the olive garden on Friday at 7 PM",
            now,
        )

        val calendar = result.actions[0] as ParsedAction.Calendar
        assertEquals(listOf("Jane"), calendar.attendees)
        assertEquals("olive garden", calendar.location)
        assertEquals("Dinner", calendar.title)
    }

    @Test
    fun `parses all day events with no end time`() = runBlocking {
        val result = parser.parse("book a conference all day on Monday", now)

        val calendar = result.actions[0] as ParsedAction.Calendar
        assertTrue(calendar.allDay)
        assertNull(calendar.end)
        val monday = LocalDate.from(calendar.start.atZone(zone))
        assertTrue(monday.isAfter(LocalDate.now(zone)))
        assertEquals(java.time.DayOfWeek.MONDAY, monday.dayOfWeek)
    }

    @Test
    fun `parses reminders`() = runBlocking {
        val result = parser.parse("remind me to call mom on Friday at 6 PM", now)

        val reminder = result.actions[0] as ParsedAction.Reminder
        assertEquals("call mom", reminder.title)
        val dueDate = LocalDate.from(reminder.dueAt!!.atZone(zone))
        assertEquals(java.time.DayOfWeek.FRIDAY, dueDate.dayOfWeek)
        assertEquals(LocalTime.of(18, 0), LocalTime.from(reminder.dueAt.atZone(zone)))
    }

    @Test
    fun `marks unrecognized speech as Unknown`() = runBlocking {
        val result = parser.parse("good morning", now)

        assertEquals(1, result.actions.size)
        assertTrue(result.actions[0] is ParsedAction.Unknown)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun `returns empty actions for blank transcript`() = runBlocking {
        val result = parser.parse("   ", now)
        assertTrue(result.actions.isEmpty())
        assertEquals(ParserSource.RULES, result.source)
    }

    @Test
    fun `detects high priority`() = runBlocking {
        val result = parser.parse("add finish report to my to-do list asap", now)

        val item = result.actions[0] as ParsedAction.ListItem
        assertEquals("finish report", item.text)
        assertEquals("to-do", item.list)
        assertEquals(com.actuate.domain.model.Priority.HIGH, item.priority)
    }

    @Test
    fun `defaults unknown list to general`() = runBlocking {
        val result = parser.parse("add buy batteries", now)

        val item = result.actions[0] as ParsedAction.ListItem
        assertEquals("buy batteries", item.text)
        assertEquals("general", item.list)
    }
}