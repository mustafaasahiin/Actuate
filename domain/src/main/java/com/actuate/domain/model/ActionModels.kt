package com.actuate.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Severity for list items and reminders. */
enum class Priority { LOW, MEDIUM, HIGH }

/** Where a transcript was understood from. */
enum class ParserSource { NONE, RULES, LLM }

/**
 * Represents an attendee for a calendar event with a name and optional email address.
 *
 * Roadmap constraint: Meeting cancellation message drafting, approval, and send flows must NOT be built yet.
 * When implemented in the future, permissions (Contacts access, SMS/WhatsApp sending) MUST be requested
 * strictly just-in-time at the exact moment the user taps 'pick attendees' or chooses a send channel,
 * with a clear one-line rationale, and NEVER requested upfront or during onboarding.
 */
data class Attendee(
    val name: String,
    val email: String? = null,
)

/**
 * A single structured action extracted from a voice transcript.
 *
 * [ListItem] and [Calendar] are executed against file storage
 * via the Actuate server, with on-device
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
        val attendees: List<Attendee> = emptyList(),
        val description: String? = null,
    ) : ParsedAction

    data class ListItem(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val list: String? = null,
        val priority: Priority? = null,
    ) : ParsedAction

    data class ListAction(
        override val id: String = UUID.randomUUID().toString(),
        val listName: String,
        val isNewList: Boolean,
        val items: List<String>,
    ) : ParsedAction

    data class Task(
        override val id: String = UUID.randomUUID().toString(),
        val title: String,
        val priority: Priority? = null,
        val project: String? = null,
        val dueDate: Instant? = null,
    ) : ParsedAction

    data class Note(
        override val id: String = UUID.randomUUID().toString(),
        val content: String,
        val destination: String? = null,
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
        get() = actions.count { it !is ParsedAction.Unknown }
}

/** Display label for an action type. */
val ParsedAction.label: String
    get() = when (this) {
        is ParsedAction.Calendar -> "Calendar event"
        is ParsedAction.ListItem -> "List item"
        is ParsedAction.ListAction -> "List: $listName"
        is ParsedAction.Task -> "Task"
        is ParsedAction.Note -> "Note"
        is ParsedAction.Reminder -> "Reminder"
        is ParsedAction.Unknown -> "Couldn't parse"
    }

// ---------------------------------------------------------------------------
// Destination switching (pure domain logic)
// ---------------------------------------------------------------------------

/** Hour of day a list item is promoted to when it gains a schedule. */
private const val PROMOTION_HOUR = 9

/** Assumed length of a promoted event when the source had no end time. */
private val PROMOTED_DURATION: Duration = Duration.ofHours(1)

/** Category assigned when no better list guess can be made from the title. */
const val DEFAULT_LIST_CATEGORY = "general"

/**
 * The destination an action is currently routed to.
 *
 * This mirrors [com.actuate.domain.model.Destination] routing used by the executor and backs the
 * destination chips shown on the staging deck, so the UI never has to guess.
 *
 * Deliberately NOT named `destination`: [ParsedAction.Note] already exposes a `destination: String?`
 * member, and a member always shadows an extension, which would silently return the wrong type.
 */
val ParsedAction.routedDestination: Destination
    get() = when (this) {
        is ParsedAction.Calendar -> Destination.CALENDAR
        is ParsedAction.Reminder -> Destination.REMINDERS
        is ParsedAction.ListItem -> Destination.NOTION
        is ParsedAction.ListAction -> Destination.NOTION
        is ParsedAction.Task -> Destination.LOCAL
        is ParsedAction.Note -> Destination.LOCAL
        is ParsedAction.Unknown -> Destination.NONE
    }

/**
 * Re-routes an action to [target] without ever throwing.
 *
 * Switching destination is the single most-used interaction on the staging deck: a user speaks
 * "dentist at 3pm", sees the calendar card, and taps `[Reminder]` because they only want a nudge.
 * The rules are deliberately total — every source/target pair produces *something*, and nothing
 * is allowed to throw on a malformed action.
 *
 * Mapping table:
 * | from \ to | CALENDAR | REMINDERS | NOTION / LOCAL |
 * |---|---|---|---|
 * | Calendar | identity | title + start as `dueAt` | title as list item |
 * | Reminder | title + `dueAt` (or tomorrow 09:00) as start, +1h | identity | title as list item |
 * | ListItem | text + tomorrow 09:00, +1h | text, no time | identity (category preserved) |
 * | Task | title + `dueDate` (or tomorrow 09:00) | title + `dueDate` | title, project as list |
 * | Note | first line + tomorrow 09:00 | first line | full content |
 * | ListAction | unknown | unknown | identity |
 * | Unknown | unknown | unknown | unknown |
 *
 * [Destination.NONE] is treated as a no-op: "route nowhere" is not a destination you can move to,
 * and silently degrading the card to `Unknown` would lose the user's data on a stray tap.
 *
 * @param now reference instant used for the deterministic "tomorrow at 09:00" default.
 * @param zone zone used to resolve that default; injectable so tests are timezone-independent.
 */
fun ParsedAction.withDestination(
    target: Destination,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): ParsedAction = when (target) {
    Destination.CALENDAR -> asCalendar(now, zone)
    Destination.REMINDERS -> asReminder()
    Destination.NOTION, Destination.LOCAL -> asListItem()
    Destination.NONE -> this
}

/** Tomorrow at [PROMOTION_HOUR]:00 in [zone], derived from [now] so results are reproducible. */
private fun promotedInstant(now: Instant, zone: ZoneId): Instant =
    // `LocalDate.ofInstant` is a Java 9 addition and is NOT in Android's java.time below
    // API 34, so the zone conversion is done explicitly. This module has no lint coverage,
    // which means the wrong call would only surface as a NoSuchMethodError in the field.
    now.atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atTime(PROMOTION_HOUR, 0)
        .atZone(zone)
        .toInstant()

/** Best-effort list bucket for a free-text title. */
fun listCategoryFor(title: String): String {
    val words = title.lowercase().split(' ', ',', '.', ':', ';', '!', '?', '\n')
    return when {
        words.any { it in SHOPPING_WORDS } -> "shopping"
        words.any { it in WORK_WORDS } -> "work"
        words.any { it in HEALTH_WORDS } -> "health"
        else -> DEFAULT_LIST_CATEGORY
    }
}

private val SHOPPING_WORDS = setOf(
    "buy", "bought", "shop", "shopping", "grocery", "groceries", "store", "market",
    "milk", "eggs", "bread", "order", "pickup", "pick", "cart",
)

private val WORK_WORDS = setOf(
    "meeting", "meet", "call", "email", "report", "deck", "review", "standup", "sync",
    "client", "invoice", "proposal", "interview", "deadline", "sprint",
)

private val HEALTH_WORDS = setOf(
    "gym", "run", "workout", "yoga", "dentist", "doctor", "meditate", "sleep", "walk",
)

/** A single-line, non-blank title derived from free text. */
private fun titleFrom(text: String, fallback: String): String =
    text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback

private fun ParsedAction.asCalendar(now: Instant, zone: ZoneId): ParsedAction = when (this) {
    is ParsedAction.Calendar -> this
    is ParsedAction.Reminder -> {
        val start = dueAt ?: promotedInstant(now, zone)
        ParsedAction.Calendar(
            id = id,
            title = title.ifBlank { "Untitled" },
            start = start,
            end = start.plus(PROMOTED_DURATION),
            // A reminder carries no location, attendees or description; nothing to clear,
            // and inventing values here would corrupt the payload.
            location = null,
            attendees = emptyList(),
            description = null,
        )
    }
    is ParsedAction.ListItem -> {
        val start = promotedInstant(now, zone)
        ParsedAction.Calendar(
            id = id,
            title = titleFrom(text, "Untitled"),
            start = start,
            end = start.plus(PROMOTED_DURATION),
        )
    }
    is ParsedAction.Task -> {
        val start = dueDate ?: promotedInstant(now, zone)
        ParsedAction.Calendar(
            id = id,
            title = title.ifBlank { "Untitled" },
            start = start,
            end = start.plus(PROMOTED_DURATION),
        )
    }
    is ParsedAction.Note -> {
        val start = promotedInstant(now, zone)
        ParsedAction.Calendar(
            id = id,
            title = titleFrom(content, "Note"),
            start = start,
            end = start.plus(PROMOTED_DURATION),
            description = content.trim().takeIf { it.isNotBlank() },
        )
    }
    is ParsedAction.ListAction -> unsupported(this, "Calendars hold one event; \"$listName\" holds ${items.size}")
    // Already unparsed: keep the original reason verbatim rather than wrapping it a second time.
    is ParsedAction.Unknown -> this
}

private fun ParsedAction.asReminder(): ParsedAction = when (this) {
    is ParsedAction.Reminder -> this
    is ParsedAction.Calendar -> ParsedAction.Reminder(
        id = id,
        title = title.ifBlank { "Untitled" },
        dueAt = start,
    )
    is ParsedAction.ListItem -> ParsedAction.Reminder(
        id = id,
        title = titleFrom(text, "Untitled"),
        dueAt = null,
        priority = priority,
    )
    is ParsedAction.Task -> ParsedAction.Reminder(
        id = id,
        title = title.ifBlank { "Untitled" },
        dueAt = dueDate,
        priority = priority,
    )
    is ParsedAction.Note -> ParsedAction.Reminder(
        id = id,
        title = titleFrom(content, "Note"),
        dueAt = null,
    )
    is ParsedAction.ListAction -> unsupported(this, "Reminders hold one item; \"$listName\" holds ${items.size}")
    is ParsedAction.Unknown -> this
}

private fun ParsedAction.asListItem(): ParsedAction = when (this) {
    is ParsedAction.ListItem -> this
    is ParsedAction.ListAction -> this
    is ParsedAction.Calendar -> ParsedAction.ListItem(
        id = id,
        text = title.ifBlank { "Untitled" },
        list = listCategoryFor(title),
    )
    is ParsedAction.Reminder -> ParsedAction.ListItem(
        id = id,
        text = title.ifBlank { "Untitled" },
        list = listCategoryFor(title),
        priority = priority,
    )
    is ParsedAction.Task -> ParsedAction.ListItem(
        id = id,
        text = title.ifBlank { "Untitled" },
        list = project ?: listCategoryFor(title),
        priority = priority,
    )
    is ParsedAction.Note -> ParsedAction.ListItem(
        id = id,
        text = content.trim().ifBlank { "Note" },
        list = destination,
    )
    is ParsedAction.Unknown -> this
}

/** Deterministic, non-throwing fallback for conversions that cannot preserve the action. */
private fun unsupported(action: ParsedAction, why: String): ParsedAction.Unknown =
    ParsedAction.Unknown(id = action.id, reason = "Cannot re-route ${action.label}: $why")