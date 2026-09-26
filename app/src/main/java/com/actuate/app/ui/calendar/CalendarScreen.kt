package com.actuate.app.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.app.R
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.CellPosition
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.FeedbackRow
import com.actuate.core.components.TelemetryBadge
import com.actuate.core.components.TelemetryStatus
import com.actuate.core.components.cellShape
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.CobaltVivid
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElectricCyan
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Ice
import com.actuate.core.theme.LinkBlue
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.domain.model.ServerActionItem
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onRefresh: () -> Unit = viewModel::refresh,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarContent(
        state = state,
        onSelectDate = viewModel::selectDate,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onGoToToday = viewModel::goToToday,
        onRefresh = onRefresh,
        modifier = modifier,
        onDeleteEvent = viewModel::requestDelete,
        onDeleteEventWithMessage = viewModel::requestDeleteWithSend,
    )
}

@Composable
fun CalendarContent(
    state: CalendarState,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDeleteEvent: (ServerActionItem) -> Unit = {},
    onDeleteEventWithMessage: (ServerActionItem, String) -> Unit = { _, _ -> },
) {
    val haptics = rememberCupertinoHaptics()
    var isMonthExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedDetailAction by remember { mutableStateOf<ServerActionItem?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
    ) {
        if (state.loading) {
            item(key = "loading-indicator") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(Capsule),
                    color = AppleBlue,
                )
                Spacer(Modifier.height(Spacing.xs))
            }
        }

        if (state.error != null) {
            item(key = "error-feedback") {
                FeedbackRow(
                    message = state.error ?: "Viewing cached calendar · Server sync pending",
                    action = "Retry",
                    onAction = onRefresh,
                )
                Spacer(Modifier.height(Spacing.xs))
            }
        }

        item(key = "sync-status-pills") {
            CalendarSyncPills(
                live = state.error == null && !state.loading,
                lastError = state.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        item(key = "header") {
            MonthNavigationHeader(
                currentMonth = state.currentMonth,
                isMonthExpanded = isMonthExpanded,
                onToggleMonthExpanded = {
                    haptics.selectionChanged()
                    isMonthExpanded = !isMonthExpanded
                },
                onPreviousMonth = {
                    haptics.selectionChanged()
                    onPreviousMonth()
                },
                onNextMonth = {
                    haptics.selectionChanged()
                    onNextMonth()
                },
                onGoToToday = {
                    haptics.selectionChanged()
                    onGoToToday()
                },
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        item(key = "date-strip-or-grid") {
            if (isMonthExpanded) {
                MonthCalendarGrid(
                    currentMonth = state.currentMonth,
                    selectedDate = state.selectedDate,
                    actionsByDate = state.actionsByDate,
                    onSelectDate = {
                        haptics.impactLight()
                        onSelectDate(it)
                    },
                )
            } else {
                CompactDateStrip(
                    currentMonth = state.currentMonth,
                    selectedDate = state.selectedDate,
                    actionsByDate = state.actionsByDate,
                    onSelectDate = {
                        haptics.impactLight()
                        onSelectDate(it)
                    },
                )
            }
            Spacer(Modifier.height(Spacing.md))
        }

        item(key = "agenda-header") {
            SelectedDayHeader(
                selectedDate = state.selectedDate,
                count = state.selectedDayActions.size,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        if (state.selectedDayActions.isEmpty()) {
            item(key = "empty-agenda") {
                DayEmptyState(selectedDate = state.selectedDate)
                Spacer(Modifier.height(Spacing.xxl))
            }
        } else {
            item(key = "agenda-card") {
                CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
                    state.selectedDayActions.forEachIndexed { index, action ->
                        val position = when {
                            state.selectedDayActions.size == 1 -> CellPosition.SINGLE
                            index == 0 -> CellPosition.FIRST
                            index == state.selectedDayActions.size - 1 -> CellPosition.LAST
                            else -> CellPosition.MIDDLE
                        }

                        TimelineAgendaRow(
                            action = action,
                            position = position,
                            onClick = {
                                haptics.selectionChanged()
                                selectedDetailAction = action
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }

    selectedDetailAction?.let { action ->
        if (action.type == "calendar_event") {
            CalendarEventDeleteDialog(
                action = action,
                onDismiss = { selectedDetailAction = null },
                onSendAndDelete = { message ->
                    selectedDetailAction = null
                    onDeleteEventWithMessage(action, message)
                },
                onJustDelete = {
                    selectedDetailAction = null
                    onDeleteEvent(action)
                },
            )
        } else {
            EventDetailDialog(
                action = action,
                onDismiss = { selectedDetailAction = null },
            )
        }
    }
}

/**
 * Tells the user where the calendar on screen actually came from.
 *
 * "Live" and "Local cache" look identical when rendered, which is exactly the problem:
 * a user about to trust a slot needs to know whether the server confirmed it or whether
 * they are looking at whatever was last written to disk.
 */
@Composable
fun CalendarSyncPills(
    live: Boolean,
    lastError: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            TelemetryBadge(text = "Live Google Calendar", status = TelemetryStatus.LIVE)
            TelemetryBadge(text = "Device alarms", status = TelemetryStatus.LOCAL)
        } else {
            TelemetryBadge(text = "Local cache", status = TelemetryStatus.OFFLINE)
            lastError?.let {
                TelemetryBadge(text = "Sync failed", status = TelemetryStatus.ERROR)
            }
        }
    }
}

@Composable
private fun MonthNavigationHeader(
    currentMonth: YearMonth,
    isMonthExpanded: Boolean,
    onToggleMonthExpanded: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
) {
    val monthTitle = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    val isDark = isSystemInDarkTheme()
    val pillBg = if (isDark) ElevatedDark else Ice
    val pillBorder = if (isDark) MistDark else Mist.copy(alpha = 0.35f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Surface(
                onClick = onGoToToday,
                shape = Capsule,
                color = pillBg,
                border = BorderStroke(1.dp, pillBorder),
                modifier = Modifier.height(34.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.today),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppleBlue,
                    )
                }
            }

            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_month),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_month),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            IconButton(
                onClick = onToggleMonthExpanded,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isMonthExpanded) Icons.Rounded.CalendarToday else Icons.Rounded.CalendarViewMonth,
                    contentDescription = if (isMonthExpanded) "Show compact date strip" else "Show full month grid",
                    tint = if (isMonthExpanded) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Compact horizontal date strip: 1 row of selectable dates.
 * Keeps the focus on the readable daily agenda.
 */
@Composable
private fun CompactDateStrip(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    actionsByDate: Map<LocalDate, List<ServerActionItem>>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val listState = rememberLazyListState()
    val daysInMonth = currentMonth.lengthOfMonth()
    val days = remember(currentMonth) {
        (1..daysInMonth).map { currentMonth.atDay(it) }
    }
    val today = remember { LocalDate.now() }

    LaunchedEffect(selectedDate, currentMonth) {
        if (selectedDate.year == currentMonth.year && selectedDate.month == currentMonth.month) {
            val targetIndex = (selectedDate.dayOfMonth - 3).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(days, key = { it.toString() }) { dayDate ->
            val isSelected = dayDate == selectedDate
            val isToday = dayDate == today
            val eventsForDay = actionsByDate[dayDate].orEmpty()

            CompactDayChip(
                date = dayDate,
                isSelected = isSelected,
                isToday = isToday,
                events = eventsForDay,
                onClick = { onSelectDate(dayDate) },
            )
        }
    }
}

@Composable
private fun CompactDayChip(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<ServerActionItem>,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(12.dp)

    val chipBg = when {
        isSelected -> AppleBlue
        else -> if (isDark) ElevatedDark else Ice
    }

    val chipBorder = when {
        isSelected -> BorderStroke(1.dp, AppleBlue)
        isToday -> BorderStroke(1.5.dp, AppleBlue)
        else -> BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f))
    }

    val weekdayText = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())

    Surface(
        onClick = onClick,
        shape = shape,
        color = chipBg,
        border = chipBorder,
        modifier = Modifier
            .width(52.dp)
            .height(74.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Text(
                text = weekdayText.take(3),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = if (isSelected) Color.White else if (isToday) AppleBlue else MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            // Event dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(6.dp),
            ) {
                if (events.isNotEmpty()) {
                    val hasCalendar = events.any { it.type == "calendar_event" }
                    val hasAlarm = events.any { it.type == "reminder" || it.type == "task" }

                    if (hasCalendar) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(if (isSelected) Color.White else SignalBlue, CircleShape),
                        )
                    }
                    if (hasAlarm) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(if (isSelected) Color.White else SolarAmber, CircleShape),
                        )
                    }
                } else {
                    Spacer(Modifier.size(5.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    actionsByDate: Map<LocalDate, List<ServerActionItem>>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val cardBorder = if (isDark) MistDark else Mist.copy(alpha = 0.35f)
    val cardBg = if (isDark) DeepGraphite else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(12.dp))
            .background(cardBg, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            val daysOfWeek = listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            )
            for (dow in daysOfWeek) {
                Text(
                    text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(42.dp),
                )
            }
        }

        val firstDayOfMonth = currentMonth.atDay(1)
        val daysInMonth = currentMonth.lengthOfMonth()
        val firstDayOfWeekSundayBased = firstDayOfMonth.dayOfWeek.value % 7

        var currentDayIndex = 1
        var weekRow = 0
        val today = LocalDate.now()

        while (currentDayIndex <= daysInMonth) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                for (column in 0..6) {
                    if (weekRow == 0 && column < firstDayOfWeekSundayBased) {
                        Spacer(modifier = Modifier.size(42.dp))
                    } else if (currentDayIndex <= daysInMonth) {
                        val dayDate = currentMonth.atDay(currentDayIndex)
                        val isSelected = dayDate == selectedDate
                        val isToday = dayDate == today
                        val eventsForDay = actionsByDate[dayDate].orEmpty()

                        DayCell(
                            date = dayDate,
                            isSelected = isSelected,
                            isToday = isToday,
                            events = eventsForDay,
                            onClick = { onSelectDate(dayDate) },
                        )
                        currentDayIndex++
                    } else {
                        Spacer(modifier = Modifier.size(42.dp))
                    }
                }
            }
            weekRow++
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<ServerActionItem>,
    onClick: () -> Unit,
) {
    val backgroundModifier = when {
        isSelected -> Modifier.background(AppleBlue, CircleShape)
        isToday -> Modifier.border(BorderStroke(1.5.dp, AppleBlue), CircleShape)
        else -> Modifier
    }

    val textColor = when {
        isSelected -> Color.White
        isToday -> AppleBlue
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(backgroundModifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                ),
                color = textColor,
                textAlign = TextAlign.Center,
            )

            if (events.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val hasAlarm = events.any { it.type == "reminder" || it.type == "task" }
                    val hasCalendar = events.any { it.type == "calendar_event" }

                    if (hasCalendar) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(if (isSelected) Color.White else SignalBlue, CircleShape),
                        )
                    }
                    if (hasAlarm) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(if (isSelected) Color.White else SolarAmber, CircleShape),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SelectedDayHeader(selectedDate: LocalDate, count: Int = 0) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    val formatted = selectedDate.format(formatter)
    val isToday = selectedDate == LocalDate.now()
    val isTomorrow = selectedDate == LocalDate.now().plusDays(1)
    val todayPrefix = stringResource(R.string.today_prefix)
    val tomorrowPrefix = stringResource(R.string.tomorrow_prefix)
    val isDark = isSystemInDarkTheme()

    val label = when {
        isToday -> "$todayPrefix · $formatted"
        isTomorrow -> "$tomorrowPrefix · $formatted"
        else -> formatted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoldEyebrow(
            text = label,
            dotColor = if (isToday) ElectricCyan else CobaltVivid,
        )
        if (count > 0) {
            Surface(
                shape = Capsule,
                color = if (isDark) ElevatedDark else Ice,
                border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = if (count == 1) "1 item" else "$count items",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineAgendaRow(
    action: ServerActionItem,
    position: CellPosition,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = cellShape(position, 16.dp)

    val isAlarm = action.type == "reminder" || action.type == "task"
    val iconVector = if (isAlarm) Icons.Rounded.Alarm else Icons.Rounded.CalendarMonth
    val iconColor = if (isAlarm) SolarAmber else SignalBlue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isDark) ElevatedDark else Ice,
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                val at = action.at
                if (at != null) {
                    Spacer(Modifier.height(2.dp))
                    val timeStr = formatEventTimeSpan(at, action.end)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (action.pendingSync) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "· Waiting to sync",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = SolarAmber,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(Spacing.xs))

            Surface(
                shape = Capsule,
                color = if (isDark) ElevatedDark else Ice,
                border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = if (isAlarm) {
                        stringResource(R.string.badge_reminder)
                    } else {
                        stringResource(R.string.badge_calendar)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = iconColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        if (position == CellPosition.FIRST || position == CellPosition.MIDDLE) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun DayEmptyState(selectedDate: LocalDate) {
    val isDark = isSystemInDarkTheme()
    val badgeBg = if (isDark) ElevatedDark else Ice

    CupertinoGroupedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(badgeBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EventBusy,
                    contentDescription = null,
                    tint = if (isDark) AshDark else Ash,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.no_events_scheduled),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tap capture below to schedule a meeting or set a reminder for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventDetailDialog(
    action: ServerActionItem,
    onDismiss: () -> Unit,
) {
    val isAlarm = action.type == "reminder" || action.type == "task"
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                action.at?.let { at ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatEventTimeSpan(at, action.end),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                action.location?.takeIf { it.isNotBlank() }?.let { loc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = Verge,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = loc,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAlarm) Icons.Rounded.Alarm else Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = if (isAlarm) SolarAmber else SignalBlue,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isAlarm) "Device Reminder" else "Google Calendar",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (action.pendingSync) Icons.Rounded.CloudQueue else Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = if (action.pendingSync) SolarAmber else Verge,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (action.pendingSync) "Saved on this device · Waiting to sync" else "Synchronized",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AppleBlue)
            }
        },
    )
}

private fun formatEventTimeSpan(startInstant: Instant, endInstant: Instant?): String {
    val zone = ZoneId.systemDefault()
    val startTime = java.time.LocalDateTime.ofInstant(startInstant, zone)
    val startStr = String.format(Locale.getDefault(), "%02d:%02d", startTime.hour, startTime.minute)
    if (endInstant == null) return startStr

    val endTime = java.time.LocalDateTime.ofInstant(endInstant, zone)
    val endStr = String.format(Locale.getDefault(), "%02d:%02d", endTime.hour, endTime.minute)
    val durationMinutes = Duration.between(startInstant, endInstant).toMinutes()

    return if (durationMinutes > 0 && durationMinutes < 1440) {
        "$startStr - $endStr (${durationMinutes}m)"
    } else {
        "$startStr - $endStr"
    }
}
