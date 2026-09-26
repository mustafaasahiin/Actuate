package com.actuate.app.ui.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.BubbleState
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.FeedbackRow
import com.actuate.core.components.VoiceBubble
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.CobaltVivid
import com.actuate.core.theme.DangerRose
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.DestructiveSoftDark
import com.actuate.core.theme.DestructiveSoftLight
import com.actuate.core.theme.ElectricCyan
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.domain.model.Destination
import com.actuate.domain.model.ExecutionResult
import com.actuate.domain.model.ParsedAction
import com.actuate.domain.model.ParsedActions
import com.actuate.domain.model.VoiceRunResult
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@Composable
fun ActuateTab(
    bubbleState: BubbleState,
    statusText: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onSubmitText: (String) -> Unit,
    prepared: ParsedActions?,
    onConfirm: () -> Unit,
    onEditAction: (ParsedAction) -> Unit,
    result: VoiceRunResult?,
    onRetry: () -> Unit,
    onNewThought: () -> Unit,
    audioLevel: Float?,
    audioWaveform: List<Float> = emptyList(),
    prepareLatencyMs: Long? = null,
    micGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val haptics = rememberCupertinoHaptics()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero Voice Capture Section
        Spacer(Modifier.height(Spacing.xs))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VoiceBubble(
                    state = bubbleState,
                    onClick = onVoiceClick,
                    size = 76.dp,
                    audioLevel = audioLevel,
                    waveform = audioWaveform,
                )

                Spacer(Modifier.height(Spacing.sm))

                val subtitleText = when (bubbleState) {
                    BubbleState.LISTENING -> "Listening… Tap bubble when finished"
                    BubbleState.BUSY -> statusText ?: "Understanding your thought…"
                    BubbleState.SUCCESS -> "Executed"
                    BubbleState.IDLE -> when {
                        result != null -> if (result.executed.any { !it.success }) "Some actions need attention" else "All actions executed"
                        prepared != null -> "Check details before saving"
                        else -> "Tap the bubble to speak your thoughts"
                    }
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (bubbleState == BubbleState.LISTENING) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (bubbleState == BubbleState.LISTENING && audioLevel != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    AudioLevels(db = audioLevel)
                }

                if (bubbleState == BubbleState.BUSY) {
                    Spacer(Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(180.dp)
                            .height(4.dp)
                            .clip(Capsule),
                        color = AppleBlue,
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Spoken / Typed Thought Input Card
        CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Your Thought",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    if (draft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onDraftChange("")
                                onNewThought()
                            },
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            text = "e.g. Tomorrow at 3 PM, schedule a 30-minute design review, add milk and eggs to my shopping list, and remind me at 6 PM to send the invoice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) AshDark else Ash,
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (draft.isNotBlank()) {
                                haptics.impactLight()
                                focusManager.clearFocus()
                                onSubmitText(draft)
                            }
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = AppleBlue,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (draft.isNotBlank() && bubbleState != BubbleState.LISTENING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Surface(
                            onClick = {
                                haptics.impactLight()
                                focusManager.clearFocus()
                                onSubmitText(draft)
                            },
                            shape = Capsule,
                            color = AppleBlue,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Parse & Actuate",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // Example Chips when empty
        if (prepared == null && result == null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BoldEyebrow(
                    text = "TRY A COMPOUND THOUGHT",
                    dotColor = SolarAmber,
                )
                Spacer(Modifier.height(Spacing.xs))

                val samplePrompts = listOf(
                    "Tomorrow at 3 PM, schedule a 30-minute design review, add milk and eggs to my shopping list, and remind me at 6 PM to send the invoice",
                    "Put sourdough bread and coffee on shopping list",
                    "Schedule standup tomorrow at 10 AM with Maya",
                )

                samplePrompts.forEach { sample ->
                    Surface(
                        onClick = {
                            haptics.selectionChanged()
                            onDraftChange(sample)
                            onSubmitText(sample)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "“$sample”",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }        // Mission-control staging deck: re-route, reschedule and rename before committing.
        if (prepared != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                StagingHeader(
                    prepared = prepared,
                    latencyMs = prepareLatencyMs,
                    modifier = Modifier.padding(horizontal = Spacing.xxs),
                )

                Spacer(Modifier.height(Spacing.sm))

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    prepared.actions.forEachIndexed { index, action ->
                        StagedActionCard(
                            action = action,
                            index = index,
                            onEdit = onEditAction,
                        )
                    }
                }

                if (prepared.actions.any { it is ParsedAction.Reminder && it.dueAt == null }) {
                    Spacer(Modifier.height(Spacing.xs))
                    FeedbackRow("Set a time for your reminder before saving.")
                }

                Spacer(Modifier.height(Spacing.md))

                ActuateAllButton(
                    count = prepared.executableCount,
                    enabled = prepared.executableCount > 0 &&
                        prepared.actions.none {
                            it is ParsedAction.Reminder && it.dueAt == null
                        },
                    isLoading = bubbleState == BubbleState.BUSY,
                    onClick = {
                        haptics.impactSuccess()
                        onConfirm()
                    },
                )
            }
        }

        // Execution Results Review
        if (result != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val hasFailures = result.executed.any { !it.success }
                BoldEyebrow(
                    text = if (hasFailures) "EXECUTION OUTCOMES (PARTIAL FAILURE)" else "EXECUTION OUTCOMES",
                    dotColor = if (hasFailures) SolarAmber else Verge,
                )
                Spacer(Modifier.height(Spacing.xs))

                CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
                    result.executed.forEachIndexed { index, execution ->
                        ExecutionOutcomeRow(outcome = execution)
                        if (index < result.executed.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp),
                                thickness = 0.5.dp,
                                color = if (isDark) MistDark else Mist.copy(alpha = 0.3f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (hasFailures) {
                        Surface(
                            onClick = {
                                haptics.impactLight()
                                onRetry()
                            },
                            shape = Capsule,
                            color = SolarAmber,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(Icons.Rounded.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry Failed", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            haptics.selectionChanged()
                            onNewThought()
                        },
                        shape = Capsule,
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New Thought", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = AppleBlue)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun ExecutionOutcomeRow(outcome: ExecutionResult) {
    val isDark = isSystemInDarkTheme()

    val (icon, color, targetBadge) = when (outcome.destination) {
        Destination.CALENDAR -> Triple(Icons.Rounded.CalendarMonth, SignalBlue, "Calendar")
        Destination.NOTION -> Triple(Icons.Rounded.Checklist, Verge, "Lists")
        Destination.REMINDERS -> Triple(Icons.Rounded.Alarm, SolarAmber, "Alarm")
        Destination.LOCAL -> Triple(Icons.Rounded.Checklist, Verge, "Local")
        Destination.NONE -> Triple(Icons.Rounded.Close, DangerRose, "Failed")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (isDark) ElevatedDark else Ice, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (outcome.success) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (outcome.success) Verge else DangerRose,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = outcome.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Target: $targetBadge",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(Spacing.xs))

        Surface(
            shape = Capsule,
            color = if (outcome.success) {
                if (isDark) Verge.copy(alpha = 0.22f) else Verge.copy(alpha = 0.12f)
            } else {
                if (isDark) DestructiveSoftDark else DestructiveSoftLight
            },
        ) {
            Text(
                text = if (outcome.success) "Saved" else "Failed",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (outcome.success) Verge else DangerRose,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
