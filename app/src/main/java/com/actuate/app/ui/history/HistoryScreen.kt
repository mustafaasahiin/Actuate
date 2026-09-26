package com.actuate.app.ui.history

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.actuate.app.R
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.CellPosition
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.SpotlightSearchBar
import com.actuate.core.components.cellShape
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
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.Pebble
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.core.theme.VividEmerald
import com.actuate.domain.model.ActionRecord
import com.actuate.domain.model.ActionStatus
import com.actuate.domain.repository.HistoryRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel

enum class CaptureStatus {
    ALL_SUCCESS,
    PARTIAL_SUCCESS,
    ALL_FAILED,
}

data class CaptureSessionGroup(
    val captureId: String,
    val timestamp: Instant,
    val transcript: String,
    val actions: List<ActionRecord>,
    val overallStatus: CaptureStatus = when {
        actions.all { it.status == ActionStatus.DONE } -> CaptureStatus.ALL_SUCCESS
        actions.any { it.status == ActionStatus.DONE } -> CaptureStatus.PARTIAL_SUCCESS
        else -> CaptureStatus.ALL_FAILED
    },
)

fun groupActionRecords(records: List<ActionRecord>): List<CaptureSessionGroup> {
    if (records.isEmpty()) return emptyList()
    val grouped = linkedMapOf<String, MutableList<ActionRecord>>()
    for (record in records) {
        val key = record.captureId?.takeIf { it.isNotBlank() } ?: record.id
        grouped.getOrPut(key) { mutableListOf() }.add(record)
    }
    return grouped.map { (key, groupRecords) ->
        val sortedByTime = groupRecords.sortedByDescending { it.timestamp }
        val newest = sortedByTime.first()
        CaptureSessionGroup(
            captureId = key,
            timestamp = newest.timestamp,
            transcript = newest.transcript,
            actions = sortedByTime,
        )
    }.sortedByDescending { it.timestamp }
}

class HistoryViewModel(
    historyRepository: HistoryRepository,
) : ViewModel() {
    val history: StateFlow<List<ActionRecord>> =
        historyRepository.observeHistory()
            .map { it.asReversed() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groupedHistory: StateFlow<List<CaptureSessionGroup>> =
        historyRepository.observeHistory()
            .map { records ->
                groupActionRecords(records.asReversed())
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@Composable
fun HistoryContent(
    modifier: Modifier = Modifier,
) {
    val viewModel: HistoryViewModel = koinViewModel()
    val groupedCaptures by viewModel.groupedHistory.collectAsStateWithLifecycle()
    HistoryGroupedContent(
        groupedCaptures = groupedCaptures,
        modifier = modifier,
    )
}

@Composable
fun HistoryGroupedContent(
    groupedCaptures: List<CaptureSessionGroup>,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val haptics = rememberCupertinoHaptics()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("all") }

    val filteredGroups = groupedCaptures.filter { group ->
        val matchesQuery = searchQuery.isBlank() ||
            group.transcript.contains(searchQuery, ignoreCase = true) ||
            group.actions.any {
                it.summary.contains(searchQuery, ignoreCase = true) ||
                    it.actionType.contains(searchQuery, ignoreCase = true)
            }

        val matchesFilter = when (selectedFilter) {
            "all" -> true
            "calendar" -> group.actions.any { it.actionType.contains("calendar", ignoreCase = true) }
            "lists" -> group.actions.any { it.actionType.contains("list", ignoreCase = true) }
            "reminders" -> group.actions.any {
                it.actionType.contains("reminder", ignoreCase = true) || it.actionType.contains("alarm", ignoreCase = true)
            }
            else -> true
        }

        matchesQuery && matchesFilter
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoldEyebrow(
                text = stringResource(R.string.history_title),
                dotColor = ElectricCyan,
            )
            Surface(
                shape = Capsule,
                color = if (isDark) ElevatedDark else Ice,
                border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = if (filteredGroups.size == 1) "1 capture" else "${filteredGroups.size} captures",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        SpotlightSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.search_history_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val filters = listOf(
                "all" to stringResource(R.string.filter_all),
                "calendar" to stringResource(R.string.filter_calendar),
                "lists" to stringResource(R.string.filter_lists),
                "reminders" to stringResource(R.string.filter_reminders),
            )

            filters.forEach { (key, label) ->
                val isSelected = selectedFilter == key
                Surface(
                    onClick = {
                        haptics.selectionChanged()
                        selectedFilter = key
                    },
                    shape = Capsule,
                    color = if (isSelected) AppleBlue else if (isDark) ElevatedDark else Ice,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) AppleBlue else if (isDark) MistDark else Mist.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.height(32.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        if (filteredGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(if (isDark) ElevatedDark else Ice, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = if (isDark) AshDark else Ash,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = if (searchQuery.isNotBlank()) stringResource(R.string.no_matching_actions) else stringResource(R.string.no_actions_logged),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try a different search term or clear the filter." else "Voice commands and typed actions will be chronologically tracked here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                itemsIndexed(filteredGroups, key = { _, group -> group.captureId }) { index, group ->
                    CaptureHistoryCard(
                        group = group,
                        initialExpanded = index == 0,
                    )
                }
                item {
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
        }
    }
}

@Composable
private fun CaptureHistoryCard(
    group: CaptureSessionGroup,
    initialExpanded: Boolean = false,
) {
    val isDark = isSystemInDarkTheme()
    val haptics = rememberCupertinoHaptics()
    val context = LocalContext.current
    var isExpanded by rememberSaveable(group.captureId) { mutableStateOf(initialExpanded) }

    val (statusLabel, statusColor, statusBg) = when (group.overallStatus) {
        CaptureStatus.ALL_SUCCESS -> Triple(
            "All Succeeded",
            Verge,
            if (isDark) Verge.copy(alpha = 0.22f) else Verge.copy(alpha = 0.14f),
        )
        CaptureStatus.PARTIAL_SUCCESS -> Triple(
            "Partial Failure",
            SolarAmber,
            if (isDark) SolarAmber.copy(alpha = 0.22f) else SolarAmber.copy(alpha = 0.14f),
        )
        CaptureStatus.ALL_FAILED -> Triple(
            "Failed",
            DangerRose,
            if (isDark) DestructiveSoftDark else DestructiveSoftLight,
        )
    }

    CupertinoGroupedCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Card Header: Timestamp, Status Pill, Expand Chevron
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.selectionChanged()
                        isExpanded = !isExpanded
                    }
                    .padding(horizontal = Spacing.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isDark) ElevatedDark else Ice,
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    Spacer(Modifier.width(Spacing.sm))

                    Column {
                        Text(
                            text = formatRelativeTime(group.timestamp, context),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val totalActions = group.actions.size
                        val successCount = group.actions.count { it.status == ActionStatus.DONE }
                        val summarySub = if (totalActions == 1) {
                            "1 action executed"
                        } else {
                            "$totalActions actions · $successCount succeeded"
                        }
                        Text(
                            text = summarySub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = Capsule,
                        color = statusBg,
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Spoken/Typed Transcript Section (Shown Once)
            if (group.transcript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) ElevatedDark.copy(alpha = 0.6f) else Ice.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = 4.dp),
                ) {
                    Text(
                        text = "“${group.transcript}”",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Expandable breakdown of individual actions
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = if (isDark) MistDark else Mist.copy(alpha = 0.35f),
                    )

                    group.actions.forEachIndexed { actionIndex, action ->
                        val isCalendar = action.actionType.contains("calendar", ignoreCase = true)
                        val isList = action.actionType.contains("list", ignoreCase = true)
                        val actionIcon = when {
                            isCalendar -> Icons.Rounded.CalendarMonth
                            isList -> Icons.Rounded.Checklist
                            else -> Icons.Rounded.Alarm
                        }
                        val actionColor = when {
                            isCalendar -> SignalBlue
                            isList -> Verge
                            else -> SolarAmber
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = 10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (isDark) ElevatedDark else Ice,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    tint = actionColor,
                                    modifier = Modifier.size(15.dp),
                                )
                            }

                            Spacer(Modifier.width(Spacing.sm))

                            Column(modifier = Modifier.weight(1f)) {
                                // Terminal-style audit line: wall-clock time, destination badge
                                // and the OK/FAIL verdict, all in monospace.
                                ExecutionLogHeader(action)

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    text = action.summary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                val destinationDesc = friendlyActionTypeName(action.actionType)
                                val subText = if (action.message.isNotBlank()) {
                                    "$destinationDesc · ${action.message}"
                                } else {
                                    destinationDesc
                                }

                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (action.status == ActionStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.width(Spacing.xs))

                            val (itemLabel, itemColor, itemBg) = when (action.status) {
                                ActionStatus.DONE -> Triple("Success", Verge, if (isDark) Verge.copy(alpha = 0.22f) else Verge.copy(alpha = 0.12f))
                                ActionStatus.FAILED -> Triple("Failed", DangerRose, if (isDark) DestructiveSoftDark else DestructiveSoftLight)
                                ActionStatus.SKIPPED -> Triple("Skipped", Graphite, if (isDark) ElevatedDark else Pebble)
                                ActionStatus.PENDING -> Triple("Pending", SignalBlue, if (isDark) SignalBlue.copy(alpha = 0.22f) else SignalBlue.copy(alpha = 0.12f))
                            }

                            Surface(
                                shape = Capsule,
                                color = itemBg,
                            ) {
                                Text(
                                    text = itemLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = itemColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }

                        if (actionIndex < group.actions.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 48.dp),
                                thickness = 0.5.dp,
                                color = if (isDark) MistDark else Mist.copy(alpha = 0.25f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun friendlyActionTypeName(type: String): String = when {
    type.contains("calendar", ignoreCase = true) -> "Google Calendar"
    type.contains("list", ignoreCase = true) -> "Lists"
    type.contains("reminder", ignoreCase = true) || type.contains("alarm", ignoreCase = true) -> "Device Alarm"
    else -> type.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

private fun formatRelativeTime(timestamp: Instant, context: Context): String {
    val now = Instant.now()
    val diff = Duration.between(timestamp, now)

    return when {
        diff.isNegative || diff.seconds < 60 -> context.getString(R.string.just_now)
        diff.toMinutes() < 60 -> context.getString(R.string.minutes_ago, diff.toMinutes().toInt())
        diff.toHours() < 24 -> context.getString(R.string.hours_ago, diff.toHours().toInt())
        diff.toDays() < 7 -> context.getString(R.string.days_ago, diff.toDays().toInt())
        else -> {
            val ldt = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
            ldt.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        }
    }
}
