package com.actuate.app.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.actuate.core.components.DestinationBadge
import com.actuate.core.components.MissionControlCard
import com.actuate.core.components.TelemetryBadge
import com.actuate.core.components.TelemetryStatus
import com.actuate.core.theme.Ash
import com.actuate.core.theme.DangerRose
import com.actuate.core.theme.SuccessEmerald
import com.actuate.core.theme.TelemetrySmall
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.label
import com.actuate.domain.model.routedDestination
import java.time.Duration
import java.time.ZoneId

/**
 * Read-only report card for one executed action.
 *
 * Editing lives in the staging deck ([StagedActionCard]) — by the time an outcome is
 * being reported the user is reviewing, not correcting, so this view has no inputs
 * and no modal dialog. The old `AlertDialog`-based `ActionEditor` is gone: it blocked
 * the keyboard over the sheet and made a one-field change feel like a form submission.
 */
@Composable
fun ActionSummary(
    action: ParsedAction,
    outcome: ExecutionResult? = null,
) {
    val zone = ZoneId.systemDefault()
    val visual = com.actuate.core.components.destinationVisual(action.routedDestination)

    MissionControlCard(
        radius = 10.dp,
        accent = visual.color,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = action.label.uppercase(),
                    style = TelemetrySmall,
                    color = Ash,
                )
                Spacer(Modifier.weight(1f))
                DestinationBadge(
                    destination = action.routedDestination,
                    detail = outcome?.let { if (it.success) "Saved" else "Failed" },
                )
            }

            Text(
                text = action.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            action.anchor(zone)?.let { instant ->
                Text(
                    text = "${instant.telemetryDate(zone)} · ${instant.telemetryTime(zone)}",
                    style = TelemetrySmall,
                    color = Ash,
                )
            }

            val detail = when (action) {
                is ParsedAction.Calendar -> action.end?.let {
                    "${Duration.between(action.start, it).toMinutes()} min"
                } ?: "60 min · default duration"

                is ParsedAction.Reminder -> action.dueAt?.let {
                    "Scheduled on this device"
                } ?: "Time not set · edit to schedule"

                is ParsedAction.Task -> action.project?.let { "Project: $it" } ?: "Task"
                is ParsedAction.ListAction -> "${action.items.size} item(s): " +
                    action.items.joinToString(", ")
                is ParsedAction.ListItem -> "List: ${action.list ?: "general"}"
                is ParsedAction.Note -> "Note"
                is ParsedAction.Unknown -> action.reason
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TelemetryBadge(
                    text = detail,
                    status = TelemetryStatus.LOCAL,
                )
                if (action is ParsedAction.Calendar && action.attendees.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    TelemetryBadge(
                        text = "${action.attendees.size} ATTENDEES",
                        status = TelemetryStatus.SYNCED,
                    )
                }
            }

            if (outcome != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (outcome.success) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        contentDescription = if (outcome.success) "Saved" else "Failed",
                        tint = if (outcome.success) SuccessEmerald else DangerRose,
                    )
                    Text(
                        text = outcome.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (outcome.success) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                if (!outcome.success) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Retry re-runs only the failed actions; the ones already saved " +
                            "are left alone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
