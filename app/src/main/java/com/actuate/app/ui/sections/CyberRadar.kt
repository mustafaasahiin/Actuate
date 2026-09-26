package com.actuate.app.ui.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.actuate.core.components.DestinationBadge
import com.actuate.core.components.MissionControlCard
import com.actuate.core.components.TelemetryBadge
import com.actuate.core.components.TelemetryStatus
import com.actuate.core.components.destinationVisual
import com.actuate.core.theme.Ash
import com.actuate.core.theme.BorderSubtleDark
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.SurfaceElevatedDark
import com.actuate.core.theme.TelemetryLarge
import com.actuate.core.theme.TelemetrySmall
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ServerActionItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Destination an action item is routed to, inferred from its server `type`. */
internal fun ServerActionItem.routedDestination(): Destination = when (type.lowercase()) {
    "calendar_event", "calendar", "event" -> Destination.CALENDAR
    "reminder", "alarm" -> Destination.REMINDERS
    "task" -> Destination.LOCAL
    "list_item", "list" -> Destination.NOTION
    else -> Destination.NONE
}

/**
 * `Next action in 42m` — the single sentence a user needs when they open the app.
 *
 * Returns `null` when there is nothing in the future to count down to, so the caller
 * renders the empty radar instead of a card claiming "next in 0m".
 */
internal fun nextUpCountdown(
    from: Instant,
    items: List<ServerActionItem>,
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val next = items
        .filter { it.done.not() }
        .mapNotNull { item -> item.at?.takeIf { it.isAfter(from) } }
        .minOrNull()
        ?: return null

    val fromDate = from.atZone(zone).toLocalDate()
    val nextDate = next.atZone(zone).toLocalDate()

    return if (fromDate == nextDate) {
        val span = Duration.between(from, next)
        val minutes = span.toMinutes()
        when {
            minutes < 1 -> "Starting now"
            minutes < 60 -> "Next action in ${minutes}m"
            else -> {
                val hours = span.toHours()
                val rest = minutes - hours * 60
                if (rest == 0L) {
                    "Next action in ${hours}h"
                } else {
                    "Next action in ${hours}h ${rest}m"
                }
            }
        }
    } else if (nextDate == fromDate.plusDays(1)) {
        val timeStr = next.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
        "Tomorrow at $timeStr"
    } else {
        val dateStr = next.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", java.util.Locale.getDefault()))
        dateStr
    }
}

/**
 * Hero card for the imminent item.
 *
 * The countdown ticks every 30 seconds — often enough to feel live, rarely enough to
 * be free — and the border glows with the destination's own colour, so the user knows
 * where the next thing is going before they have read a word.
 */
@Composable
fun NextUpHeroCard(
    items: List<ServerActionItem>,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(items.size) {
        while (true) {
            now = Instant.now()
            delay(30_000)
        }
    }

    val upcoming = items
        .filter { !it.done && it.at != null && it.at!!.isAfter(now.minus(Duration.ofMinutes(1))) }
        .minByOrNull { it.at!! }
    val countdown = nextUpCountdown(now, items, zone)

    if (upcoming == null || countdown == null) {
        MissionControlCard(radius = 12.dp, glow = true) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("RADAR CLEAR", style = TelemetrySmall, color = HyperCyan)
                Text(
                    text = "Nothing else scheduled today",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Speak a thought and it will appear here with a live countdown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val visual = destinationVisual(upcoming.routedDestination())

    MissionControlCard(
        modifier = modifier,
        radius = 12.dp,
        accent = visual.color,
        glow = true,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT UP", style = TelemetrySmall, color = HyperCyan)
                Spacer(Modifier.weight(1f))
                DestinationBadge(destination = upcoming.routedDestination())
            }

            Text(
                text = countdown.uppercase(),
                style = TelemetryLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = upcoming.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TelemetryBadge(
                    text = upcoming.at!!.telemetryTime(zone),
                    status = TelemetryStatus.LOCAL,
                )
                upcoming.location?.takeIf { it.isNotBlank() }?.let {
                    TelemetryBadge(text = it, status = TelemetryStatus.OFFLINE)
                }
                if (upcoming.pendingSync) {
                    TelemetryBadge(text = "Pending sync", status = TelemetryStatus.OFFLINE)
                }
            }
        }
    }
}

/**
 * Chronological rail for everything scheduled today.
 *
 * A connecting hairline with a node per item, monotone stamps and a destination badge.
 * The rail is what makes a flat list read as a day: time runs down the left edge and
 * the eye can find "where am I" without reading every row.
 */
@Composable
fun MissionTimeline(
    items: List<ServerActionItem>,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val ordered = remember(items) { items.sortedBy { it.at ?: Instant.MAX } }
    val today = LocalDate.now(zone)

    MissionControlCard(modifier = modifier, radius = 12.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            ordered.forEachIndexed { index, item ->
                MissionTimelineRow(
                    item = item,
                    isLast = index == ordered.lastIndex,
                    isToday = item.at?.atZone(zone)?.toLocalDate() == today,
                    zone = zone,
                )
            }
        }
    }
}

@Composable
private fun MissionTimelineRow(
    item: ServerActionItem,
    isLast: Boolean,
    isToday: Boolean,
    zone: ZoneId,
) {
    val visual = destinationVisual(item.routedDestination())

    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        // Rail gutter: mono time over a node and a connecting hairline.
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = item.at?.telemetryTime(zone) ?: "--:--",
                style = TelemetrySmall,
                color = if (isToday) visual.color else Ash,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (item.done) Ash.copy(alpha = 0.4f) else visual.color),
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(if (isLast) 0.dp else 44.dp)
                .background(BorderSubtleDark),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 2.dp, bottom = if (isLast) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DestinationBadge(
                    destination = item.routedDestination(),
                    detail = when {
                        item.done -> "Done"
                        item.pendingSync -> "Pending"
                        else -> null
                    },
                )
                if (item.attendees.isNotEmpty()) {
                    TelemetryBadge(
                        text = "${item.attendees.size} PEOPLE",
                        status = TelemetryStatus.SYNCED,
                    )
                }
            }
        }
    }
}

/**
 * Voice prompts that change with the hour.
 *
 * A generic "try asking Actuate something" chip teaches nothing. A chip that names the
 * task the user is statistically about to do teaches the whole product in one tap.
 */
@Composable
fun ContextualPromptPills(
    hour: Int,
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompts = when (hour) {
        in 5..11 -> listOf(
            "Plan my morning meetings",
            "Remind me to leave at 8:30",
        )

        in 12..17 -> listOf(
            "Add lunch expenses to my list",
            "Block 2 hours for deep work",
        )

        in 18..21 -> listOf(
            "Review tomorrow's schedule",
            "Add milk and eggs to my shopping list",
        )

        else -> listOf(
            "Summarise today into a note",
            "Schedule gym tomorrow at 7",
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("SUGGESTED NOW", style = TelemetrySmall, color = Ash)
        prompts.forEach { prompt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevatedDark.copy(alpha = 0.45f))
                    .border(BorderStroke(0.5.dp, BorderSubtleDark), RoundedCornerShape(8.dp))
                    .clickable { onPrompt(prompt) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(HyperCyan),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
