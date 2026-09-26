package com.actuate.app.ui.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.actuate.core.audio.LocalTactileSound
import com.actuate.core.haptics.rememberTactileFeedback
import com.actuate.core.theme.Ash
import com.actuate.core.theme.CyberBackgroundDark
import com.actuate.core.theme.DangerRose
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.SuccessEmerald
import com.actuate.core.theme.TelemetrySmall
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a row must be dragged before the gesture commits, in dp. */
private const val SWIPE_THRESHOLD_DP = 96

/**
 * Swipe right to complete, swipe left to delete.
 *
 * Implemented with a raw horizontal drag rather than a `SwipeToDismissBox` because the
 * reveal has to show a different intent on each side (emerald complete vs rose delete)
 * and both must return to rest if the drag does not commit.
 *
 * The gesture is announced to accessibility services as two custom actions, so a
 * screen-reader user can complete or delete without a drag at all.
 */
@Composable
fun SwipeActionRow(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    rightLabel: String = "Complete",
    leftLabel: String = "Delete",
    content: @Composable () -> Unit,
) {
    val haptics = rememberTactileFeedback()
    val sound = LocalTactileSound.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var rowWidth by remember { mutableStateOf(0) }

    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(),
        label = "swipeOffset",
    )

    val progress = (abs(animatedOffset) / thresholdPx).coerceIn(0f, 1f)

    val commit: (Boolean) -> Unit = { toTheRight ->
        offsetX = 0f
        sound?.playToggleSnap()
        if (toTheRight) {
            haptics.success()
            onSwipeRight()
        } else {
            haptics.error()
            onSwipeLeft()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(rightLabel) { commit(true); true },
                    CustomAccessibilityAction(leftLabel) { commit(false); true },
                )
            },
    ) {
        // Reveal layers sit behind the row and grow as the drag commits.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(SuccessEmerald.copy(alpha = 0.22f * progress)),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (animatedOffset > 0) {
                Row(
                    modifier = Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = SuccessEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rightLabel.uppercase(),
                        style = TelemetrySmall,
                        color = SuccessEmerald,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(DangerRose.copy(alpha = 0.22f * progress)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (animatedOffset < 0) {
                Text(
                    text = leftLabel.uppercase(),
                    style = TelemetrySmall,
                    color = DangerRose,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(rowWidth) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX >= thresholdPx -> commit(true)
                                offsetX <= -thresholdPx -> commit(false)
                                else -> offsetX = 0f
                            }
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            val max = (rowWidth.takeIf { it > 0 } ?: Int.MAX_VALUE).toFloat()
                            offsetX = (offsetX + dragAmount).coerceIn(-max, max)
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/**
 * Completion control with a glowing cyan check.
 *
 * The check scales in rather than appearing, and the strike-through animates with it,
 * so completing an item reads as an event the user caused rather than a state that
 * quietly changed.
 */
@Composable
fun CyberCheckbox(
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val scale by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "cyberCheckScale",
    )

    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (done) SuccessEmerald.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClickLabel = contentDescription, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(width = 1.5.dp.toPx())
            drawCircle(
                color = if (done) SuccessEmerald else Ash.copy(alpha = 0.6f),
                radius = size.minDimension / 2f - stroke.width / 2f,
                style = stroke,
            )
            if (done) {
                drawCircle(
                    color = HyperCyan.copy(alpha = 0.45f),
                    radius = size.minDimension / 2f - stroke.width / 2f,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = CyberBackgroundDark,
            modifier = Modifier
                .size(13.dp)
                .scale(scale),
        )
    }
}

/**
 * Emoji for a list name.
 *
 * Derived from the words people actually use in list names; anything unmatched gets a
 * neutral marker rather than a wrong glyph.
 */
internal fun emojiForList(name: String): String {
    val words = name.lowercase().split(' ', '-', '_')
    return when {
        words.any { it in setOf("shop", "shopping", "grocery", "groceries", "market") } -> "🛒"
        words.any { it in setOf("work", "office", "job", "client") } -> "💼"
        words.any { it in setOf("goal", "goals", "okr", "target") } -> "🎯"
        words.any { it in setOf("health", "gym", "fitness", "meds") } -> "❤️"
        words.any { it in setOf("travel", "trip", "packing") } -> "✈️"
        words.any { it in setOf("home", "house", "chores") } -> "🏠"
        words.any { it in setOf("read", "books", "study") } -> "📚"
        else -> "▸"
    }
}

/**
 * Notion-grade section header: emoji, name, and a completion ring.
 *
 * The ring is the point — "4/7" as a bare fraction is data, a ring that is more than
 * half closed is information.
 */
@Composable
fun CyberCategoryHeader(
    name: String,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(400),
        label = "listProgress",
    )
    val ringColor = if (fraction >= 1f) SuccessEmerald else HyperCyan

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emojiForList(name), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(width = 2.dp.toPx())
            val radius = size.minDimension / 2f - stroke.width / 2f
            drawCircle(
                color = Ash.copy(alpha = 0.25f),
                radius = radius,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = stroke,
                topLeft = Offset(stroke.width / 2f, stroke.width / 2f),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke.width,
                    size.height - stroke.width,
                ),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$done/$total",
            style = TelemetrySmall,
            color = ringColor,
        )
    }
}

/** Struck-through, de-emphasised text style helper for completed items. */
@Composable
internal fun completedTextDecoration(done: Boolean): TextDecoration? =
    if (done) TextDecoration.LineThrough else null

/** Vertical rhythm used between grouped list rows. */
@Composable
internal fun ListRowSpacer() = Spacer(Modifier.height(2.dp))

/** Small helper so the swipe wrapper can be dropped into an existing Column scope. */
@Composable
internal fun GroupedRow(verticalArrangement: Arrangement.Vertical = Arrangement.Top, content: @Composable () -> Unit) {
    Column(verticalArrangement = verticalArrangement, modifier = Modifier.fillMaxWidth()) { content() }
}
