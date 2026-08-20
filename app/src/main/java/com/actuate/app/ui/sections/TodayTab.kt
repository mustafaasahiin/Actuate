package com.actuate.app.ui.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.actuate.core.components.ActuateCard
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.Spacing
import com.actuate.domain.model.ServerActionItem
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** "Today" tab: calendar events and reminders scheduled from today onward. */
@Composable
fun TodayTab(state: SectionsState, onConnect: () -> Unit) {
    when {
        !state.serverConfigured -> ConnectHint(onConnect)
        state.loading && state.today.isEmpty() -> LoadingPlaceholder()
        state.today.isEmpty() -> EmptySection(
            title = "Nothing scheduled",
            body = "Speak a calendar event or a reminder and it will show up here.",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        ) {
            item {
                SectionHeader("TODAY")
                Spacer(Modifier.height(Spacing.sm))
            }
            items(state.today, key = { it.id }) { action ->
                TodayRow(action)
                Spacer(Modifier.height(Spacing.sm))
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}

@Composable
private fun TodayRow(action: ServerActionItem) {
    ActuateCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = actionIcon(action.type),
                contentDescription = null,
                tint = AppleBlue,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.padding(horizontal = Spacing.xs))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Carbon,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val at = action.at
                if (at != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatTime(at),
                        style = MaterialTheme.typography.bodySmall,
                        color = Graphite,
                    )
                }
            }
        }
    }
}

private fun actionIcon(type: String): ImageVector = when (type) {
    "reminder" -> Icons.Rounded.Alarm
    else -> Icons.Rounded.CalendarMonth
}

private fun formatTime(instant: Instant): String {
    val ldt = java.time.LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    return if (ldt.toLocalDate() == java.time.LocalDate.now(ZoneId.systemDefault())) {
        String.format(Locale.ROOT, "Today · %02d:%02d", ldt.hour, ldt.minute)
    } else {
        String.format(
            Locale.ROOT,
            "%s · %02d:%02d",
            ldt.toLocalDate().toString(),
            ldt.hour,
            ldt.minute,
        )
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
internal fun ConnectHint(onConnect: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        ActuateCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sync your lists",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Carbon,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Connect to the Actuate server in Settings to keep your lists and reminders across devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Graphite,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = "Open Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppleBlue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable(onClick = onConnect)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .background(Ice, MaterialTheme.shapes.small),
                )
            }
        }
    }
}

@Composable
internal fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodyLarge,
            color = Graphite,
        )
    }
}

@Composable
internal fun EmptySection(title: String, body: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Rounded.EventBusy,
                contentDescription = null,
                tint = Mist,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Carbon,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Graphite,
                textAlign = TextAlign.Center,
            )
        }
    }
}