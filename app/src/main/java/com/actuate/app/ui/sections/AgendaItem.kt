package com.actuate.app.ui.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.actuate.domain.model.ServerActionItem
import java.time.ZoneId
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

fun agendaDetails(item: ServerActionItem): String = listOfNotNull(
    item.at?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.ENGLISH)),
    if (item.at != null && item.end != null) "${Duration.between(item.at, item.end).toMinutes()} min" else null,
    item.location,
).joinToString(" · ")

@Composable
fun AgendaItem(item: ServerActionItem) {
    var expanded by remember { mutableStateOf(false) }
    Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(if (item.type == "calendar_event") Icons.Rounded.Event else Icons.Rounded.Alarm, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(agendaDetails(item), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.pendingSync) Text("Saved on this device · Waiting to sync", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(item.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (item.type == "calendar_event") "Calendar event" else "Reminder")
                Text(agendaDetails(item))
                item.description?.let { Text(it) }
                if (item.attendees.isNotEmpty()) Text(item.attendees.joinToString { it.name })
                if (item.pendingSync) Text("Saved on this device · Waiting to sync")
            }
        }, confirmButton = { TextButton(onClick = { expanded = false }) { Text("Done") } })
}
