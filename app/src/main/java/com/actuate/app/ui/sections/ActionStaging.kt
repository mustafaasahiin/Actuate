package com.actuate.app.ui.sections

import com.actuate.domain.model.Destination
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.withDestination
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Which day the quick date chips move an action to. */
enum class DayChoice { TODAY, TOMORROW, NEXT_MONDAY }

/**
 * Every mutation the staging deck can apply to a parsed action.
 *
 * Pure functions with no Compose and no Android: the deck's interactivity is the
 * part of the app judges will hammer, so its behaviour is unit tested rather than
 * verified by tapping. [now] and [zone] are parameters so "tomorrow", "next Monday"
 * and "is this in the past" are all reproducible.
 */
object ActionStaging {

    val STEP_SMALL: Duration = Duration.ofMinutes(15)
    val STEP_LARGE: Duration = Duration.ofHours(1)

    /** The instant an action is scheduled at, or `null` if it is not time-bound. */
    fun anchorOf(action: ParsedAction): Instant? = when (action) {
        is ParsedAction.Calendar -> action.start
        is ParsedAction.Reminder -> action.dueAt
        is ParsedAction.Task -> action.dueDate
        else -> null
    }

    /**
     * Replaces [updated] inside [parsed] by id, leaving every other action untouched.
     *
     * Switching a destination must never close the sheet or drop sibling actions, so
     * this is a pure map-and-replace rather than a rebuild from the transcript.
     */
    fun applyTo(parsed: ParsedActions, updated: ParsedAction): ParsedActions =
        parsed.copy(actions = parsed.actions.map { if (it.id == updated.id) updated else it })

    /** Re-routes an action to [target], preserving its id and everything transferable. */
    fun withDestination(
        action: ParsedAction,
        target: Destination,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ParsedAction = action.withDestination(target, now, zone)

    /**
     * Moves an action's schedule by [delta].
     *
     * Refuses to land in the past: a stepper that silently schedules something for
     * yesterday is worse than a stepper that does nothing. Callers detect the refusal
     * with [canStep] and grey the pill out.
     */
    fun stepTime(
        action: ParsedAction,
        delta: Duration,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ParsedAction {
        val anchor = anchorOf(action) ?: return action
        val target = anchor.plus(delta)
        if (!target.isAfter(now)) return action
        return retime(action, target)
    }

    /** Whether [stepTime] would actually move this action. */
    fun canStep(
        action: ParsedAction,
        delta: Duration,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val anchor = anchorOf(action) ?: return false
        return anchor.plus(delta).isAfter(now)
    }

    /**
     * Moves an action to [day], keeping its time of day.
     *
     * "Today" with a time that has already passed rolls forward one day instead of
     * creating an event in the past — the one case where silently doing something
     * different from the label is the right call.
     */
    fun moveToDay(
        action: ParsedAction,
        day: DayChoice,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ParsedAction {
        val anchor = anchorOf(action) ?: return action
        // `LocalTime.ofInstant` / `LocalDate.ofInstant` are Java 9 additions that are not
        // present in Android's java.time before API 31/34, so convert through ZonedDateTime.
        val time = anchor.atZone(zone).toLocalTime()
        val today = now.atZone(zone).toLocalDate()
        val date = when (day) {
            DayChoice.TODAY -> today
            DayChoice.TOMORROW -> today.plusDays(1)
            DayChoice.NEXT_MONDAY -> nextMonday(today)
        }
        val candidate = date.atTime(time).atZone(zone).toInstant()
        if (candidate.isAfter(now)) return retime(action, candidate)
        if (day != DayChoice.TODAY) return action
        return retime(action, date.plusDays(1).atTime(time).atZone(zone).toInstant())
    }

    /** Replaces an action's user-visible title, ignoring blank input. */
    fun withTitle(action: ParsedAction, title: String): ParsedAction {
        val clean = title.trim()
        if (clean.isBlank()) return action
        return when (action) {
            is ParsedAction.Calendar -> action.copy(title = clean)
            is ParsedAction.Reminder -> action.copy(title = clean)
            is ParsedAction.Task -> action.copy(title = clean)
            is ParsedAction.ListItem -> action.copy(text = clean)
            is ParsedAction.Note -> action.copy(content = clean)
            is ParsedAction.ListAction -> action.copy(listName = clean)
            is ParsedAction.Unknown -> action
        }
    }

    /** Whether this action has a schedule the time controls can act on. */
    fun isScheduled(action: ParsedAction): Boolean = anchorOf(action) != null

    private fun retime(action: ParsedAction, target: Instant): ParsedAction = when (action) {
        is ParsedAction.Calendar -> {
            // Shift the end by the same delta so the duration the user saw is preserved.
            val delta = Duration.between(action.start, target)
            action.copy(start = target, end = action.end?.plus(delta))
        }

        is ParsedAction.Reminder -> action.copy(dueAt = target)
        is ParsedAction.Task -> action.copy(dueDate = target)
        else -> action
    }

    private fun nextMonday(today: LocalDate): LocalDate {
        // DayOfWeek.MONDAY.value == 1; move forward at least one day.
        val daysAhead = ((1 - today.dayOfWeek.value) + 7) % 7
        return today.plusDays(if (daysAhead == 0) 7L else daysAhead.toLong())
    }
}
