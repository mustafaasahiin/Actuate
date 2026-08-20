package com.actuate.app.ui.history

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.core.components.ActuateCard
import com.actuate.core.components.StatusChip
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Pebble
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel

/** History tab content — shared between the full screen and the Home tab. */
@Composable
fun HistoryContent() {
    val viewModel: HistoryViewModel = koinViewModel()
    val history by viewModel.history.collectAsStateWithLifecycle()

    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No actions yet.\nSpeak to the voice bubble to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = Graphite,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.md),
        ) {
            items(history, key = { it.id }) { record ->
                HistoryRow(record)
                Spacer(Modifier.height(Spacing.sm))
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}

class HistoryViewModel(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val history: StateFlow<List<ActionRecord>> =
        historyRepository.observeHistory()
            .map { it.asReversed() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun HistoryRow(record: ActionRecord) {
    ActuateCard {
        Column {
            Text(
                text = record.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = Carbon,
            )
            if (record.message.isNotBlank() && record.status != ActionStatus.DONE) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = record.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Row {
                Text(
                    text = "${record.actionType} · ${formatTimestamp(record)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    text = when (record.status) {
                        ActionStatus.DONE -> "done"
                        ActionStatus.FAILED -> "failed"
                        ActionStatus.SKIPPED -> "skipped"
                        ActionStatus.PENDING -> "pending"
                    },
                    background = when (record.status) {
                        ActionStatus.DONE -> Verge.copy(alpha = 0.18f)
                        else -> Pebble
                    },
                    contentColor = when (record.status) {
                        ActionStatus.DONE -> Verge
                        else -> Graphite
                    },
                )
            }
        }
    }
}

private fun formatTimestamp(record: ActionRecord): String {
    val ldt = java.time.LocalDateTime.ofInstant(record.timestamp, java.time.ZoneId.systemDefault())
    return "${ldt.toLocalDate()} · ${String.format(java.util.Locale.ROOT, "%02d:%02d", ldt.hour, ldt.minute)}"
}