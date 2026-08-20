package com.actuate.domain.model

import java.time.Instant
import java.util.UUID

/** Severity for list items and reminders. */
enum class Priority { LOW, MEDIUM, HIGH }

/** Where a transcript was understood from. */
enum class ParserSource { NONE, RULES, LLM }

/**
 * A single structured action extracted from a voice transcript.
 *
 * [Calendar] and [ListItem] are executed against their destination apps
 * (Google Calendar, Notion) via the Actuate server, with on-device
 * fallbacks. [Reminder] is scheduled locally via AlarmManager — the Android
 * equivalent of Apple EventKit reminders. [Unknown] carries anything the
 * parser could not map.
 */
sealed interface ParsedAction {

    val id: String

    data class Calendar(
        override val id: String = UUID.randomUUID().toString(),
        val title: String,
        val start: Instant,
        val end: Instant?,
        val allDay: Boolean = false,
        val location: String? = null,
        val attendees: List<String> = emptyList(),
        val description: String? = null,
    ) : ParsedAction

    data class ListItem(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val list: String? = null,
        val priority: Priority? = null,
    ) : ParsedAction

    data class Reminder(
        override val id: String = UUID.randomUUID().toString(),
        val title: String,
        val dueAt: Instant? = null,
        val priority: Priority? = null,
    ) : ParsedAction

    data class Unknown(
        override val id: String = UUID.randomUUID().toString(),
        val reason: String,
    ) : ParsedAction
}

/** The result of turning a transcript into structured actions. */
data class ParsedActions(
    val rawTranscript: String,
    val actions: List<ParsedAction>,
    val source: ParserSource,
    val confidence: Float,
) {
    val executableCount: Int
        get() = actions.count {
            it is ParsedAction.Calendar || it is ParsedAction.ListItem || it is ParsedAction.Reminder
        }
}

/** Display label for an action type. */
val ParsedAction.label: String
    get() = when (this) {
        is ParsedAction.Calendar -> "Calendar event"
        is ParsedAction.ListItem -> "List item"
        is ParsedAction.Reminder -> "Reminder"
        is ParsedAction.Unknown -> "Couldn't parse"
    }