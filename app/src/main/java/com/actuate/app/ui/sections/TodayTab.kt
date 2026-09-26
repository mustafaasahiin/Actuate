package com.actuate.app.ui.sections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actuate.app.R
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.ButtonSize
import com.actuate.core.components.CellPosition
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.FeedbackRow
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
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.domain.model.ServerActionItem
import com.actuate.domain.model.ServerListItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayTab(
    state: SectionsState,
    onToggle: (ServerListItem) -> Unit,
    onRefresh: () -> Unit,
    onOpenActuate: () -> Unit = {},
    onTrySample: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val haptics = rememberCupertinoHaptics()
    val today = remember { LocalDate.now() }
    val now = remember { LocalTime.now() }

    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Good night"
    }

    val formattedDate = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }

    val pendingListItems = state.lists.flatMap { it.items }.filterNot { it.done }
    val calendarEvents = state.today.filter { it.type == "calendar_event" }
    val reminders = state.today.filter { it.type == "reminder" || it.type == "task" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "header-greeting") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs, bottom = Spacing.xxs),
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loading) {
            item(key = "loading-indicator") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(Capsule),
                    color = AppleBlue,
                )
            }
        }

        if (state.error != null) {
            item(key = "error-feedback") {
                FeedbackRow(
                    message = state.error ?: "Viewing cached content · Server sync pending",
                    action = "Retry",
                    onAction = onRefresh,
                )
            }
        }

        item(key = "glance-bento-row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GlanceStatCard(
                    title = "Events",
                    count = calendarEvents.size,
                    icon = Icons.Rounded.CalendarMonth,
                    accentColor = SignalBlue,
                    modifier = Modifier.weight(1f),
                )
                GlanceStatCard(
                    title = "Tasks",
                    count = pendingListItems.size,
                    icon = Icons.Rounded.Checklist,
                    accentColor = Verge,
                    modifier = Modifier.weight(1f),
                )
                GlanceStatCard(
                    title = "Reminders",
                    count = reminders.size,
                    icon = Icons.Rounded.Alarm,
                    accentColor = SolarAmber,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "hero-capture-prompt") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(AppleBlue.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = AppleBlue,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Speak to Actuate",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.2).sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Natural voice to calendar, lists & alarms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        ActuatePrimaryButton(
                            text = "Speak",
                            onClick = {
                                haptics.selectionChanged()
                                onOpenActuate()
                            },
                            size = ButtonSize.SMALL,
                            leadingIcon = {
                                Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            },
                        )
                    }

                    Spacer(Modifier.height(Spacing.sm))

                    Surface(
                        onClick = {
                            haptics.selectionChanged()
                            onTrySample("Tomorrow at 3 PM, schedule a 30-minute design review, add milk and eggs to my shopping list, and remind me at 6 PM to send the invoice")
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Try: “Tomorrow 3 PM review, milk & eggs, remind at 6 PM”",
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        item(key = "next-up-hero") {
            NextUpHeroCard(items = state.today)
        }

        item(key = "contextual-prompts") {
            ContextualPromptPills(
                hour = now.hour,
                onPrompt = onTrySample,
            )
        }

        item(key = "schedule-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoldEyebrow(
                    text = "SCHEDULE TODAY",
                    dotColor = SignalBlue,
                )
                if (state.today.isNotEmpty()) {
                    Surface(
                        shape = Capsule,
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = if (state.today.size == 1) "1 item" else "${state.today.size} items",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SignalBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (state.today.isEmpty()) {
            item(key = "empty-schedule-card") {
                CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(if (isDark) ElevatedDark else Ice, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EventBusy,
                                contentDescription = null,
                                tint = if (isDark) AshDark else Ash,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = "Nothing scheduled for today",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Your calendar events and reminders will appear here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "schedule-card") {
                // Chronological rail: mono timestamps on a connecting hairline with a
                // destination badge per node, so the day reads as a timeline rather than
                // as a list of rows that happen to have times.
                MissionTimeline(items = state.today)
            }
        }

        if (state.tomorrow.isNotEmpty()) {
            item(key = "tomorrow-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoldEyebrow(
                        text = "TOMORROW",
                        dotColor = SolarAmber,
                    )
                    Surface(
                        shape = Capsule,
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = if (state.tomorrow.size == 1) "1 item" else "${state.tomorrow.size} items",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SolarAmber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            item(key = "tomorrow-schedule-card") {
                MissionTimeline(items = state.tomorrow)
            }
        }

        item(key = "lists-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoldEyebrow(
                    text = "FROM YOUR LISTS",
                    dotColor = Verge,
                )
                if (pendingListItems.isNotEmpty()) {
                    Surface(
                        shape = Capsule,
                        color = if (isDark) ElevatedDark else Ice,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = "${pendingListItems.size} pending",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Verge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (pendingListItems.isEmpty()) {
            item(key = "empty-lists-card") {
                CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(if (isDark) ElevatedDark else Ice, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Verge,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = "All list items completed",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Speak or add items to Shopping, Work, or Todo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "pending-lists-card") {
                CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 12.dp) {
                    val displayItems = pendingListItems.take(8)
                    displayItems.forEachIndexed { index, item ->
                        val position = when {
                            displayItems.size == 1 -> CellPosition.SINGLE
                            index == 0 -> CellPosition.FIRST
                            index == displayItems.size - 1 -> CellPosition.LAST
                            else -> CellPosition.MIDDLE
                        }
                        TodayTaskRow(
                            item = item,
                            position = position,
                            onToggle = {
                                haptics.impactSuccess()
                                onToggle(item)
                            },
                        )
                    }
                }
            }
        }

        item(key = "bottom-spacer") {
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun GlanceStatCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (isDark) DeepGraphite else Color.White,
        border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.25f)),
        modifier = modifier.height(86.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TodayActionRow(
    action: ServerActionItem,
    position: CellPosition,
) {
    val isDark = isSystemInDarkTheme()
    val shape = cellShape(position, 16.dp)

    val isAlarm = action.type == "reminder" || action.type == "task"
    val iconVector = if (isAlarm) Icons.Rounded.Alarm else Icons.Rounded.CalendarMonth
    val iconColor = if (isAlarm) SolarAmber else SignalBlue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (isDark) ElevatedDark else Ice, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = iconVector, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                action.at?.let { at ->
                    val zone = ZoneId.systemDefault()
                    val ldt = java.time.LocalDateTime.ofInstant(at, zone)
                    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", ldt.hour, ldt.minute)
                    val locationPart = action.location?.let { " · $it" } ?: ""
                    Text(
                        text = "$timeStr$locationPart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(Spacing.xs))

            Surface(
                shape = Capsule,
                color = if (isDark) ElevatedDark else Ice,
                border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = if (isAlarm) "Alarm" else "Google Calendar",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
private fun TodayTaskRow(
    item: ServerListItem,
    position: CellPosition,
    onToggle: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = cellShape(position, 16.dp)

    val checkColor by animateColorAsState(
        targetValue = if (item.done) Verge else if (isDark) MistDark else Mist.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "todayCheckColor",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (item.done) Verge else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Not done",
                        tint = checkColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(Spacing.xs))

            Surface(
                shape = Capsule,
                color = if (isDark) ElevatedDark else Ice,
                border = BorderStroke(0.5.dp, if (isDark) MistDark else Mist.copy(alpha = 0.25f)),
            ) {
                Text(
                    text = item.list.replaceFirstChar { it.titlecase(Locale.ROOT) },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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
