package com.actuate.app.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.actuate.core.components.BubbleState
import com.actuate.core.components.EmptyState
import com.actuate.core.components.FeedbackRow
import com.actuate.core.components.VoiceBubble
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.VoiceRunResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActuateActionSheet(
    onDismiss: () -> Unit,
    bubbleState: BubbleState,
    statusText: String?,
    quotaText: String?,
    onVoiceClick: () -> Unit,
    onSubmitText: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    autoDismissOnSuccess: Boolean = false,
    modifier: Modifier = Modifier,
    draft: String = "",
    onDraftChange: (String) -> Unit = {},
    result: VoiceRunResult? = null,
    onRetry: () -> Unit = {},
    micGranted: Boolean = true,
    prepared: ParsedActions? = null,
    onConfirm: () -> Unit = {},
    onEditAction: (ParsedAction) -> Unit = {},
    onNewThought: () -> Unit = {},
    audioLevel: Float? = null,
    audioWaveform: List<Float> = emptyList(),
    prepareLatencyMs: Long? = null,
) {
    val busy = bubbleState == BubbleState.BUSY
    val listening = bubbleState == BubbleState.LISTENING
    val staging = prepared != null && result == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxHeight(0.94f).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        result != null -> "Execution log"
                        staging -> "Staging deck"
                        else -> "Capture a thought"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = when {
                        listening -> "Listening. Tap stop when you're finished."
                        busy -> statusText ?: "Processing your thought…"
                        result != null -> if (result.executed.any { !it.success }) {
                            "Some actions need attention."
                        } else {
                            "Here's what happened."
                        }

                        staging -> "Re-route, reschedule or rename anything before committing."
                        else -> "One thought can become several actions."
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                if (result == null) {
                    if (staging) {
                        StagingHeader(
                            prepared = prepared!!,
                            latencyMs = prepareLatencyMs,
                        )

                        prepared.actions.forEachIndexed { index, action ->
                            StagedActionCard(
                                action = action,
                                index = index,
                                onEdit = onEditAction,
                            )
                        }

                        if (prepared.actions.any { it is ParsedAction.Calendar }) {
                            TextButton(onClick = onOpenSettings) {
                                Text("Calendar connection settings")
                            }
                        }
                        if (prepared.actions.any {
                                it is ParsedAction.Reminder && it.dueAt == null
                            }
                        ) {
                            FeedbackRow("Set a time for your reminder before saving.")
                        }
                        if (prepared.actions.any { it is ParsedAction.Reminder }) {
                            Text(
                                "Notifications let your reminder reach you on time. Android may " +
                                    "ask when you save. You can keep using Actuate if you decline.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            label = { Text("Your thought") },
                            placeholder = { Text("Say it naturally, or type it here…") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            enabled = !busy && !listening,
                        )
                        if (!busy && !listening && statusText != null) FeedbackRow(statusText)
                        if (prepared == null && !listening) {
                            Text(
                                "For example: Add milk and bread to my shopping list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(result.transcript, style = MaterialTheme.typography.bodyLarge)
                    if (result.executed.isEmpty()) {
                        EmptyState(
                            "No actions found",
                            "Close this view and edit your thought to include what you'd like to do.",
                        )
                    }
                    result.executed.forEach { outcome ->
                        val action = result.actions.find { it.id == outcome.actionId }
                        if (action != null) ActionSummary(action, outcome)
                        else FeedbackRow(outcome.message)
                    }
                    if (result.executed.any { !it.success }) {
                        TextButton(onClick = onRetry, enabled = !busy) {
                            Text("Retry failed actions")
                        }
                        TextButton(onClick = onOpenSettings) { Text("Manage connections") }
                    }
                    TextButton(onClick = onNewThought, enabled = !busy) {
                        Text("Capture another thought")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (result == null) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (prepared == null) {
                        if (!micGranted) {
                            Text(
                                "Microphone access is used only when you record. You can also type.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        VoiceBubble(
                            state = bubbleState,
                            onClick = onVoiceClick,
                            size = 64.dp,
                            audioLevel = audioLevel,
                            waveform = audioWaveform,
                        )
                        Text(
                            if (listening) "Stop recording" else "Record a thought",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        TextButton(
                            onClick = { onSubmitText(draft) },
                            enabled = draft.isNotBlank() && !busy && !listening,
                        ) {
                            Text("Review typed thought")
                        }
                    } else {
                        ActuateAllButton(
                            count = prepared.executableCount,
                            enabled = !busy &&
                                prepared.executableCount > 0 &&
                                prepared.actions.none {
                                    it is ParsedAction.Reminder && it.dueAt == null
                                },
                            isLoading = busy,
                            onClick = onConfirm,
                        )
                        TextButton(onClick = { onSubmitText(draft) }, enabled = !busy) {
                            Text("Re-understand transcript")
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}
