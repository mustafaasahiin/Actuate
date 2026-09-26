package com.actuate.domain.parser

import com.actuate.domain.model.Attendee
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.ParserSource
import com.actuate.domain.model.Priority
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Deterministic, offline fallback parser.
 *
 * Recognizes calendar events ("schedule X with Y tomorrow at 5 PM"),
 * list items ("add X to my shopping list") and reminders
 * ("remind me to X on Friday") using heuristics. The LLM parser is used
 * first when configured; this guarantees the app works with zero keys.
 */
class RuleBasedActionParser : ActionParser {

    override suspend fun parse(transcript: String, now: Instant): ParsedActions {
        val clauses = splitClauses(transcript)
        val actions = clauses.flatMap { parseClause(it, now) }
        val recognized = actions.count { it !is ParsedAction.Unknown }
        val confidence = if (actions.isEmpty()) 0f else recognized.toFloat() / actions.size
        return ParsedActions(
            rawTranscript = transcript,
            actions = actions,
            source = ParserSource.RULES,
            confidence = confidence,
        )
    }

    // region Clause splitting

    private fun splitClauses(transcript: String): List<String> {
        val normalized = transcript
            .replace("'", "")
            .replace("\u2019", "")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        val rawParts = SPLIT_RE.split(normalized).map { it.trim() }.filter { it.isNotEmpty() }
        val merged = mutableListOf<String>()
        var prefixBuffer = ""
        for (part in rawParts) {
            val c = part.lowercase(Locale.ROOT)
            val hasVerb = isCalendarClause(c) || isListClause(c) || isReminderClause(c)
            if (!hasVerb && merged.isEmpty() && prefixBuffer.isEmpty()) {
                prefixBuffer = part
            } else if (prefixBuffer.isNotEmpty()) {
                merged.add("$prefixBuffer $part")
                prefixBuffer = ""
            } else {
                merged.add(part)
            }
        }
        if (prefixBuffer.isNotEmpty()) merged.add(prefixBuffer)
        return merged
    }

    // region Classification

    private fun parseClause(clause: String, now: Instant): List<ParsedAction> {
        val c = clause.lowercase(Locale.ROOT)

        if (isReminderClause(c)) return listOf(parseReminder(clause, now))
        if (isCalendarClause(c)) return listOf(parseCalendar(clause, now))
        if (isListClause(c)) return parseListItem(clause)
        return listOf(ParsedAction.Unknown(reason = "No action recognized in \"$clause\""))
    }

    private fun isCalendarClause(c: String): Boolean =
        CALENDAR_VERBS.any { c.contains(it) } ||
            c.contains("on my calendar") ||
            c.contains("in my calendar") ||
            CALENDAR_NOUNS.any { c.contains(it) }

    private fun isListClause(c: String): Boolean =
        LIST_VERBS.any { c.contains(it) }

    private fun isReminderClause(c: String): Boolean =
        c.contains("remind me") ||
            c.contains("set a reminder") ||
            c.contains("set reminder") ||
            c.contains("set me a reminder") ||
            c.contains("don't let me forget")

    // region Calendar parsing

    private fun parseCalendar(clause: String, now: Instant): ParsedAction.Calendar {
        val dateTime = extractDateTime(clause, now)
        val attendees = extractAttendees(clause)
        val location = extractLocation(clause, dateTime)

        var title = extractTitle(clause)
            .replaceAttendees(attendees)
            .replaceLocation(location)
            .replace(Regex("(?i)\\s+(?:on|at|for|with)\\s*$"), "")
            .trimTitle()

        if (title.isBlank() || title.equals("meeting", ignoreCase = true) || title.equals("new meeting", ignoreCase = true) || title.equals("event", ignoreCase = true) || title.equals("new event", ignoreCase = true)) {
            title = if (attendees.isNotEmpty()) "Meeting with ${attendees.joinToString(", ") { it.name }}" else (title.ifBlank { "Meeting" })
        }

        val allDay = clause.contains("all day")
        val start = dateTime?.start ?: now
        val end = when {
            allDay -> null
            else -> dateTime?.end ?: start.plusSeconds(DEFAULT_DURATION_SECONDS)
        }
        return ParsedAction.Calendar(
            title = title,
            start = start,
            end = end,
            allDay = allDay,
            location = location?.takeIf { it.isNotBlank() },
            attendees = attendees,
        )
    }

    private fun extractTitle(clause: String): String {
        var title = clause.replace(Regex("(?i)\\b\\d+(?:\\s*-\\s*|\\s+)?(?:min|minute|minutes|hour|hours)\\b"), " ")
        title = TIME_EXPR_RE.replace(title, " ")
        title = title.replace(Regex("(?i)\\b(?:${CALENDAR_VERBS.joinToString("|")})\\b"), " ")
        title = title.replace(Regex("(?i)\\b(me|us|a|an|the)\\b"), " ")
        title = title.replaceFirst(Regex("(?i)\\b(and|then|plus)\\b.*$"), "")
        return title
    }

    private fun String.replaceAttendees(attendees: List<Attendee>): String {
        if (attendees.isEmpty()) return this
        val pattern = Regex("(?i)\\bwith\\s+.*?(?=\\s+(?:at|on|tomorrow|today|next\\s+\\w+day)|$)")
        return pattern.replace(this, " ")
    }

    private fun String.replaceLocation(location: String?): String {
        if (location.isNullOrBlank()) return this
        return Regex("(?i)\\bat\\s+the\\s+${Regex.escape(location)}\\b").replace(this, " ")
            .replace(Regex("(?i)\\bat\\s+${Regex.escape(location)}\\b"), " ")
    }

    private fun String.trimTitle(): String {
        return trim()
            .replace(Regex("\\s{2,}"), " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
            .trim()
    }

    private fun extractAttendees(clause: String): List<Attendee> {
        val match = Regex(
            "(?i)\\bwith\\s+(.+?)(?=\\s+at\\s+(?:the\\s+)?[a-z]|\\s+at\\s+[\\d]|" +
                "\\s+on\\s+\\w+day|\\s+tomorrow|\\s+today|\\s+next\\s+\\w+day|$)",
        ).find(clause) ?: return emptyList()
        val names = match.groupValues[1]
            .split(Regex("\\s+and\\s+|\\s*,\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("me", true) && !it.equals("i", true) }
        return names.take(MAX_ATTENDEES).map { Attendee(name = it, email = null) }
    }

    private fun extractLocation(clause: String, dateTime: ExtractedDateTime?): String? {
        val afterTime = TIME_EXPR_RE.replace(clause, " ")
        val locationMatch = Regex(
            "(?i)\\bat\\s+(the\\s+)?([a-z][a-z\\s]{0,24}?)(?=\\s+(?:on|at|for|tomorrow|today|next\\s+\\w+day)|$)",
        ).find(afterTime)
        val candidate = locationMatch?.groupValues?.get(2)?.trim()
        if (candidate == null || candidate.length < 2) return null
        val cleaned = candidate.replace(Regex("\\s+(please|okay|ok|thanks|thank you)$"), "")
        if (cleaned.isBlank() || cleaned in LOCATION_STOP_WORDS) return null
        return cleaned
    }

    // region List parsing

    private fun parseListItem(clause: String): List<ParsedAction.ListItem> {
        val listMatch = LIST_CATEGORY_RE.find(clause)
            ?: LIST_FALLBACK_RE.find(clause)
        val rawList = listMatch?.groupValues?.get(1)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() && it !in LIST_STOP_WORDS }
            ?: "general"
        val list = when (rawList) {
            "grocery" -> "groceries"
            else -> rawList
        }

        val rawText = clause
            .replaceFirst(Regex("(?i)^.*?(?:add|put|append|jot down)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:to|on|in)\\s+(?:my|the)\\s+.*$"), "")
            .replace(Regex("(?i)\\s*(?:please|okay|ok|thanks|thank you)\\s*$"), "")
            .trim()

        val items = if (rawText.contains(" and ") || rawText.contains(",")) {
            rawText.split(Regex("\\s+and\\s+|,\\s*")).map { it.trim() }.filter { it.isNotBlank() }
        } else {
            listOf(rawText.ifBlank { clause.trim() })
        }

        val priority = extractPriority(clause)
        return items.map { itemText ->
            ParsedAction.ListItem(
                text = itemText,
                list = list,
                priority = priority,
            )
        }
    }

    private fun extractPriority(clause: String): Priority? = when {
        HIGH_PRIORITY_RE.containsMatchIn(clause) -> Priority.HIGH
        LOW_PRIORITY_RE.containsMatchIn(clause) -> Priority.LOW
        MEDIUM_PRIORITY_RE.containsMatchIn(clause) -> Priority.MEDIUM
        else -> null
    }

    // region Reminder parsing

    private fun parseReminder(clause: String, now: Instant): ParsedAction.Reminder {
        val dateTime = extractDateTime(clause, now)
        val title = clause
            .replaceFirst(Regex("(?i)^.*?(?:remind me|set (?:me )?a reminder|set reminder|don't let me forget)\\s*"), "")
            .replace(Regex("(?i)^\\s*(?:at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|tomorrow|today|tonight|on\\s+\\w+day)\\s*"), "")
            .replace(Regex("(?i)^\\s*(?:to|that|about|for)\\s+"), "")
            .let { TIME_EXPR_RE.replace(it, " ") }
            .replace(Regex("(?i)\\s*(?:on|at|for)\\b\\s*$"), "")
            .replace(Regex("(?i)\\s*(please|okay|ok|thanks|thank you)\\s*$"), "")
            .trim()

        return ParsedAction.Reminder(
            title = title.ifBlank { "Reminder" },
            dueAt = dateTime?.start,
            priority = extractPriority(clause),
        )
    }

    // region Date & time extraction

    private data class ExtractedDateTime(
        val start: Instant,
        val end: Instant?,
        val rawText: String,
    )

    private fun extractDateTime(clause: String, now: Instant): ExtractedDateTime? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val currentTime = LocalDateTime.now(zone)
        var text = clause

        val allDay = clause.contains("all day")
        var date: LocalDate = today
        var time: LocalTime? = null
        var rawSpan = ""

        // Relative day first
        DAY_SHIFT_RE.findAll(clause).forEach { m ->
            val shift = when (m.value.lowercase(Locale.ROOT)) {
                "today" -> 0
                "tonight" -> 0
                "tomorrow" -> 1
                "the day after tomorrow" -> 2
                else -> return@forEach
            }
            date = today.plusDays(shift.toLong())
            rawSpan += " ${m.value}"
        }
        WEEKDAY_RE.findAll(clause).forEach { m ->
            val target = DayOfWeek.valueOf(m.groupValues[2].uppercase(Locale.ROOT))
            date = nextWeekday(target, today)
            rawSpan += " ${m.value}"
        }

        // Time of day
        TIME_RE.findAll(clause).forEach { m ->
            time = parseClockTime(m.value) ?: return@forEach
            rawSpan += " ${m.value}"
        }
        if (time == null && clause.contains("noon")) {
            time = LocalTime.NOON
            rawSpan += " noon"
        }
        if (time == null && clause.contains("midnight")) {
            time = LocalTime.MIDNIGHT
            rawSpan += " midnight"
        }
        if (clause.contains("tonight")) {
            time = time ?: LocalTime.of(20, 0)
        }

        val durationMatch = Regex("(?i)\\b(\\d+)\\s*(?:-|\\s+)?(?:min|minute|minutes)\\b").find(clause)
        val durationHoursMatch = Regex("(?i)\\b(\\d+)\\s*(?:-|\\s+)?(?:hour|hours)\\b").find(clause)
        val customDuration = durationMatch?.groupValues?.get(1)?.toLongOrNull()?.times(60)
            ?: durationHoursMatch?.groupValues?.get(1)?.toLongOrNull()?.times(3600)
        val durationSeconds = customDuration ?: DEFAULT_DURATION_SECONDS

        // Fallback clock
        val start: Instant
        val end: Instant?
        if (time != null) {
            var startLdt = LocalDateTime.of(date, time)
            if (startLdt.isBefore(currentTime)) {
                startLdt = startLdt.plusDays(1)
            }
            start = startLdt.atZone(zone).toInstant()
            end = if (allDay) null else start.plusSeconds(durationSeconds)
        } else if (date != today || allDay) {
            start = if (allDay) {
                date.atStartOfDay(zone).toInstant()
            } else {
                LocalDateTime.of(date, DEFAULT_EVENT_TIME).atZone(zone).toInstant()
            }
            end = if (allDay) null else start.plusSeconds(durationSeconds)
        } else {
            val defaultStart = currentTime.plusHours(1)
            start = defaultStart.atZone(zone).toInstant()
            end = start.plusSeconds(durationSeconds)
        }

        return ExtractedDateTime(start = start, end = end, rawText = rawSpan.trim())
    }

    private fun parseClockTime(text: String): LocalTime? {
        val normalized = text.lowercase(Locale.ROOT).replace(".", "")
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(normalized) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (minute !in 0..59) return null
        val meridiem = match.groupValues[3]
        return when {
            meridiem == "am" && hour in 1..12 -> LocalTime.of(hour % 12, minute)
            meridiem == "pm" && hour in 1..12 -> LocalTime.of(hour % 12 + 12, minute)
            meridiem.isNullOrEmpty() && hour in 0..23 -> {
                if (hour < 8) LocalTime.of(hour + 12, minute) else LocalTime.of(hour, minute)
            }
            else -> null
        }
    }

    private fun nextWeekday(target: DayOfWeek, today: LocalDate): LocalDate {
        var candidate = today.plusDays(1)
        while (candidate.dayOfWeek != target) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    // region Constants

    companion object {
        private val SPLIT_RE = Regex(
            "(?:,\\s*(?:and\\s+|then\\s+|plus\\s+)?|;\\s*|\\s+(?:then|plus|also|additionally)\\s+|\\s+and\\s+(?=(?:schedule|book|set|add|put|remind|jot|make|plan|arrange|create)\\b))",
            RegexOption.IGNORE_CASE,
        )

        private val CALENDAR_VERBS = listOf(
            "schedule", "book", "set up", "plan", "arrange", "make an appointment", "add to my calendar", "create a", "create", "make", "set",
        )
        private val CALENDAR_VERB_RE =
            Regex("(?i)^\\s*(?:${CALENDAR_VERBS.joinToString("|")})\\s*")

        private val CALENDAR_NOUNS = listOf(
            " meeting", " appointment", " gym session", " session with", " call with", " dinner", " lunch",
            " coffee with", " event", " on my calendar", " in my calendar",
        )
        private val LIST_VERBS = listOf(
            "add ", "put ", "append ", "jot down ", "add to my list", "add to the list",
        )
        private val LOCATION_STOP_WORDS = setOf(
            "home", "the office", "work", "gym", "the gym", "the park", "park", "the store",
        )
        private val LIST_STOP_WORDS = setOf("my", "the", "a", "to", "in", "on")

        private val LIST_CATEGORY_RE =
            Regex("(?i)\\b(?:my|the)\\s+([a-z\\-]+)?\\s*(?:\\bto[- ]do\\b|list)")
        private val LIST_FALLBACK_RE =
            Regex("(?i)\\b([a-z\\-]+)?\\s*(?:\\bto[- ]do\\b|list)")

        private val HIGH_PRIORITY_RE = Regex("(?i)\\b(urgent|asap|right away|high priority|important)\\b")
        private val LOW_PRIORITY_RE = Regex("(?i)\\b(low priority|whenever|no rush)\\b")
        private val MEDIUM_PRIORITY_RE = Regex("(?i)\\b(medium priority|normal priority)\\b")

        private val DAY_SHIFT_RE = Regex("(?i)\\b(the day after tomorrow|tomorrow|tonight|today)\\b")
        private val WEEKDAY_RE = Regex("(?i)\\b(next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
        private val TIME_RE = Regex("(?i)\\b(?:at\\s+)?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|a\\.m\\.|p\\.m\\.)?)\\b(?!(?:-|\\s*)(?:min|minute|hour|sec))")

        // Used to strip clock/date expressions out of titles after extraction.
        private val TIME_EXPR_RE = Regex(
            "(?i)\\b(?:at\\s+)?\\d{1,2}:?\\d{0,2}\\s*(?:am|pm|a\\.m\\.|p\\.m\\.)?\\b(?!(?:-|\\s*)(?:min|minute|hour|sec))|" +
                "\\b(the day after tomorrow|tomorrow|tonight|today|next\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|" +
                "(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b",
        )

        private const val DEFAULT_DURATION_SECONDS = 3600L
        private const val MAX_ATTENDEES = 4
        private val DEFAULT_EVENT_TIME = LocalTime.of(9, 0)
    }
}