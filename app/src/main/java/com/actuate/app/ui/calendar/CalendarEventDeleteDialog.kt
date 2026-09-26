package com.actuate.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.actuate.app.R
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Verge
import com.actuate.domain.model.ServerActionItem

/**
 * Cancellation confirmation for calendar events.
 *
 * Two shapes:
 *  - attendee(s) present: question row + generated, editable message preview
 *    with [Send] / [Just Cancel] options.
 *  - no attendee: simple "remove this event?" confirm.
 *
 * The preview is editable; whatever text it holds when Send is tapped is
 * exactly what [onSendAndDelete] receives.
 */
@Composable
fun CalendarEventDeleteDialog(
    action: ServerActionItem,
    onDismiss: () -> Unit,
    onSendAndDelete: (message: String) -> Unit,
    onJustDelete: () -> Unit,
) {
    val attendees = action.attendees
    val hasAttendee = attendees.isNotEmpty()

    if (!hasAttendee) {
        NoAttendeeDialog(
            action = action,
            onDismiss = onDismiss,
            onJustDelete = onJustDelete,
        )
        return
    }

    val initialMessage = remember(action.id) { CalendarViewModel.buildCancellationMessage(action) }
    var message by remember(action.id) { mutableStateOf(initialMessage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.cancel_event_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EventHeaderRow(action)

                AttendeeQuestionRow(attendees)

                Text(
                    text = stringResource(R.string.cancel_preview_label),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.cancel_message_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { onJustDelete() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_dialog_delete),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { onSendAndDelete(message) },
                    modifier = Modifier.weight(1f),
                    enabled = message.isNotBlank(),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_send_button),
                        color = AppleBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

@Composable
private fun NoAttendeeDialog(
    action: ServerActionItem,
    onDismiss: () -> Unit,
    onJustDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.cancel_no_attendee_message),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EventHeaderRow(action)
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_dialog_dismiss),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onJustDelete,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_dialog_delete),
                        color = AppleBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

@Composable
private fun EventHeaderRow(action: ServerActionItem) {
    val isDark = isSystemInDarkTheme()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (isDark) MistDark else Ice, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.EventBusy,
                contentDescription = null,
                tint = SolarAmber,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
            )
            action.at?.let {
                Text(
                    text = CalendarViewModel.formatCancellableTime(action),
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite,
                )
            }
        }
    }
}

@Composable
private fun AttendeeQuestionRow(attendees: List<com.actuate.domain.model.Attendee>) {
    val isDark = isSystemInDarkTheme()
    val primary = attendees.first()
    val displayName = primary.name.ifBlank {
        primary.email ?: stringResource(R.string.cancel_attendee_fallback)
    }
    val question = if (attendees.size == 1) {
        stringResource(R.string.cancel_send_question, displayName)
    } else {
        stringResource(R.string.cancel_send_question_multi, displayName, attendees.size - 1)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) MistDark.copy(alpha = 0.35f) else Ice, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (attendees.size > 1) Icons.Rounded.Group else Icons.Rounded.Person,
            contentDescription = null,
            tint = Verge,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = question,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        )
    }
}
