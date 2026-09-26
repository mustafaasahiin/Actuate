package com.actuate.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.actuate.core.components.DestinationBadge
import com.actuate.core.theme.Ash
import com.actuate.core.theme.BorderSubtleDark
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.SurfaceElevatedDark
import com.actuate.core.theme.TelemetrySmall
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.model.Destination
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

/**
 * Monospace audit-log strip for one executed action.
 *
 * Everything on this line is verifiable from the record itself — the wall-clock time
 * it ran, where it went, and whether it succeeded. No derived or estimated values.
 */
@Composable
fun ExecutionLogHeader(record: ActionRecord, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = record.timestamp.atZone(zone).format(LOG_TIME),
            style = TelemetrySmall,
            color = Ash,
        )
        DestinationBadge(destination = record.destination)
    }
}

/**
 * Expandable raw payload inspector.
 *
 * Renders the persisted record as formatted key/value lines rather than a single blob,
 * because what makes this a debugging tool is being able to read one field at a time.
 * It is deliberately collapsed by default — this is for the developer who taps it, not
 * something the everyday UI has to carry.
 */
@Composable
fun JsonInspector(
    record: ActionRecord,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember(record.id) { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.DataObject,
                contentDescription = null,
                tint = HyperCyan,
                modifier = Modifier.width(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (expanded) "HIDE DETAILS" else "VIEW DETAILS",
                style = TelemetrySmall,
                color = HyperCyan,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevatedDark.copy(alpha = 0.45f))
                    .border(BorderStroke(0.5.dp, BorderSubtleDark), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                payloadLines(record).forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = key,
                            style = TelemetrySmall,
                            color = Ash,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = value,
                            style = TelemetrySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** The record flattened into displayable `key → value` pairs, in a stable order. */
internal fun payloadLines(record: ActionRecord): List<Pair<String, String>> = listOf(
    "id" to record.id,
    "captureId" to (record.captureId ?: "—"),
    "actionType" to record.actionType,
    "destination" to (if (record.destination == Destination.NOTION) "LISTS" else record.destination.name),
    "status" to record.status.name,
    "summary" to record.summary,
    "message" to record.message.ifBlank { "—" },
    "timestamp" to record.timestamp.toString(),
    "transcript" to record.transcript.ifBlank { "—" },
)
