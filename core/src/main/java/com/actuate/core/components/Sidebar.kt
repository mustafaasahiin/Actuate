package com.actuate.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.Spacing
import kotlinx.coroutines.delay

enum class SidebarState { COLLAPSED, EXPANDED }

/**
 * Right-edge floating sidebar.
 *
 * COLLAPSED — a small wave pill at the right edge that dims to
 * semi-transparent after a few seconds of inactivity.
 * EXPANDED — a vertical strip slides in from the right: the voice bubble
 * on top, then history and settings icons. Tapping the anchor collapses it.
 */
@Composable
fun ActuateSidebar(
    state: SidebarState,
    bubbleState: BubbleState,
    statusText: String?,
    quotaText: String?,
    onToggle: () -> Unit,
    onVoiceClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dimmed by remember(state) { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state == SidebarState.COLLAPSED) {
            dimmed = false
            delay(6000)
            dimmed = true
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        // Collapsed anchor pill.
        if (state == SidebarState.COLLAPSED) {
            Box(
                modifier = Modifier
                    .padding(end = Spacing.sm, top = Spacing.xxl)
                    .size(width = 44.dp, height = 52.dp)
                    .alpha(if (dimmed) 0.35f else 1f)
                    .background(AppleBlue, Capsule)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                WaveIcon(
                    color = Ice,
                    modifier = Modifier.size(20.dp),
                    barWidth = 3.dp,
                )
            }
        }

        // Expanded strip — slides in from the right edge.
        AnimatedVisibility(
            visible = state == SidebarState.EXPANDED,
            enter = slideInHorizontally(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                initialOffsetX = { it },
            ) + fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)),
            exit = slideOutHorizontally(
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
                targetOffsetX = { it },
            ) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .padding(end = Spacing.sm, top = Spacing.xxl)
                    .width(84.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(horizontal = Spacing.xs, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Collapse handle.
                Row(
                    modifier = Modifier
                        .clip(Capsule)
                        .background(Frost)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggle,
                        )
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaveIcon(
                        color = Graphite,
                        modifier = Modifier.size(14.dp),
                        barWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Collapse",
                        color = Graphite,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                // The voice bubble — primary element.
                VoiceBubble(
                    state = bubbleState,
                    onClick = onVoiceClick,
                    size = 68.dp,
                )

                // Live status beneath the bubble.
                if (statusText != null) {
                    Text(
                        text = statusText,
                        color = SignalBlue,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(Spacing.xs))
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = Mist,
                )

                IconButton(onClick = onHistoryClick) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "History",
                        tint = Carbon,
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Carbon,
                    )
                }

                // Free-tier quota hint.
                if (quotaText != null) {
                    Text(
                        text = quotaText,
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}