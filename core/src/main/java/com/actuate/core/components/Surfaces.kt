package com.actuate.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import com.actuate.core.theme.AlarmAmber
import com.actuate.core.theme.BorderCyanGlow
import com.actuate.core.theme.BorderSubtleDark
import com.actuate.core.theme.BorderSubtleLight
import com.actuate.core.theme.CalendarCoral
import com.actuate.core.theme.CobaltVivid
import com.actuate.core.theme.DangerRose
import com.actuate.core.theme.ElectricCyan
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.NeonSky
import com.actuate.core.theme.NotionPrismatic
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SuccessEmerald
import com.actuate.core.theme.SurfacePanelDark
import com.actuate.core.theme.TechPanelLight
import com.actuate.core.theme.TelemetryBadgeShape
import com.actuate.core.theme.TelemetrySmall
import com.actuate.core.theme.WarningAmber
import com.actuate.domain.model.Destination
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.Spacing

enum class CellPosition {
    FIRST,
    MIDDLE,
    LAST,
    SINGLE,
}

fun cellShape(position: CellPosition, radius: Dp = 10.dp): Shape = when (position) {
    CellPosition.FIRST -> RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp)
    CellPosition.MIDDLE -> RoundedCornerShape(0.dp)
    CellPosition.LAST -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = radius, bottomEnd = radius)
    CellPosition.SINGLE -> RoundedCornerShape(radius)
}

// ---------------------------------------------------------------------------
// Mission-control surfaces
//
// Elevation is expressed with crisp half-pixel borders and tonal layering, never
// with drop shadows. A 0.5dp border reads as a machined edge on dense panels and
// stays legible against the pitch-void canvas where a soft shadow would be invisible
// anyway.
// ---------------------------------------------------------------------------

/**
 * The workhorse panel of the overhaul: an obsidian surface with a hairline edge,
 * an optional accent stripe, and an optional cyan rim glow for the one card that
 * currently matters (the staged action, the hero event).
 *
 * @param accent when set, a 2dp gradient stripe is welded to the top edge.
 * @param glow raises the border to the cyber glow colour; use for focus, not decoration.
 */
@Composable
fun MissionControlCard(
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp,
    accent: Color? = null,
    glow: Boolean = false,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(radius)
    val borderColor = when {
        glow -> BorderCyanGlow
        isDark -> BorderSubtleDark
        else -> BorderSubtleLight
    }
    val cardBackground = if (isDark) SurfacePanelDark else TechPanelLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, borderColor), shape)
            .background(cardBackground, shape)
            .clip(shape),
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.08f)),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Backwards-compatible entry point for the pre-overhaul call sites. Delegates to
 * [MissionControlCard] so every existing screen re-skins at once instead of drifting.
 */
@Composable
fun CupertinoGroupedCard(
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    MissionControlCard(
        modifier = modifier,
        radius = radius,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Telemetry badges
// ---------------------------------------------------------------------------

/** Connectivity / provenance state rendered by [TelemetryBadge]. */
enum class TelemetryStatus {
    /** Reachable and authoritative — e.g. a synced Google Calendar. */
    LIVE,

    /** Written through and confirmed. */
    SYNCED,

    /** Served from the device, no network involved. */
    LOCAL,

    /** Cached or unreachable; the user should know before trusting the number. */
    OFFLINE,

    /** Fixture / demo data. Never let this masquerade as real. */
    SIMULATED,

    /** Failure. */
    ERROR,
}

/**
 * Monospace status pill: a coloured dot plus a tracked, uppercase label.
 *
 * The mono face is the point — a row of these reads as an instrument panel rather
 * than a row of tags, and the numerals in "142ms" stay column-aligned.
 */
@Composable
fun TelemetryBadge(
    text: String,
    modifier: Modifier = Modifier,
    status: TelemetryStatus = TelemetryStatus.LIVE,
    tint: Color? = null,
) {
    val resolvedTint = tint ?: status.defaultTint()

    Row(
        modifier = modifier
            .clip(TelemetryBadgeShape)
            .background(resolvedTint.copy(alpha = 0.12f))
            .border(
                BorderStroke(0.5.dp, resolvedTint.copy(alpha = 0.45f)),
                TelemetryBadgeShape,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(resolvedTint, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text.uppercase(),
            style = TelemetrySmall,
            color = resolvedTint,
            maxLines = 1,
        )
    }
}

@Composable
private fun TelemetryStatus.defaultTint(): Color = when (this) {
    TelemetryStatus.LIVE -> HyperCyan
    TelemetryStatus.SYNCED -> SuccessEmerald
    TelemetryStatus.LOCAL -> NeonSky
    TelemetryStatus.OFFLINE -> WarningAmber
    TelemetryStatus.SIMULATED -> NotionPrismatic
    TelemetryStatus.ERROR -> DangerRose
}

// ---------------------------------------------------------------------------
// Destination badges
// ---------------------------------------------------------------------------

/** Resolved visual identity for a destination. */
data class DestinationVisual(
    val label: String,
    val color: Color,
    val icon: ImageVector,
)

/**
 * The colour-coded identity of a destination, shared by every surface that routes
 * an action (staging deck, timeline, calendar, history terminal).
 *
 * `null` and [Destination.NONE] deliberately resolve to a neutral identity rather
 * than throwing or rendering a blank: an unrouted action is a real state and the
 * user needs to see it as such.
 */
fun destinationVisual(destination: Destination?): DestinationVisual = when (destination) {
    Destination.CALENDAR -> DestinationVisual("Calendar", CalendarCoral, Icons.Rounded.CalendarMonth)
    Destination.NOTION -> DestinationVisual("Lists", NotionPrismatic, Icons.Rounded.Checklist)
    Destination.REMINDERS -> DestinationVisual("Alarm", AlarmAmber, Icons.Rounded.Alarm)
    Destination.LOCAL -> DestinationVisual("Local", SuccessEmerald, Icons.Rounded.Smartphone)
    Destination.NONE, null -> DestinationVisual("Unrouted", Ash, Icons.Rounded.RemoveCircleOutline)
}

/**
 * Branded tag for the destination an action will land in.
 *
 * The icon and colour together let a judge identify routing without reading a word.
 *
 * @param detail optional suffix, e.g. "Synced" or "Device".
 */
@Composable
fun DestinationBadge(
    destination: Destination?,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val visual = destinationVisual(destination)

    Row(
        modifier = modifier
            .clip(TelemetryBadgeShape)
            .background(visual.color.copy(alpha = 0.12f))
            .border(
                BorderStroke(0.5.dp, visual.color.copy(alpha = 0.45f)),
                TelemetryBadgeShape,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = buildString {
                append(visual.label.uppercase())
                if (!detail.isNullOrBlank()) {
                    append(" · ")
                    append(detail.uppercase())
                }
            },
            style = TelemetrySmall,
            color = visual.color,
            maxLines = 1,
        )
    }
}

@Composable
fun CupertinoGroupedCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color.White,
    iconBackground: Color = AppleBlue,
    position: CellPosition = CellPosition.MIDDLE,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val shape = cellShape(position)
    val clickableModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(iconBackground, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(Spacing.xs))
                trailingContent()
            }
        }

        if (position == CellPosition.FIRST || position == CellPosition.MIDDLE) {
            val insetStart = if (icon != null) 58.dp else Spacing.md
            HorizontalDivider(
                modifier = Modifier.padding(start = insetStart),
                thickness = 0.5.dp,
                color = if (isDark) MistDark else Mist.copy(alpha = 0.3f),
            )
        }
    }
}

/** 8dp container with hairline border — separation via borders, never shadows. */
@Composable
fun ActuateCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isDark) MistDark else Mist.copy(alpha = 0.35f)
    val cardShape = RoundedCornerShape(8.dp)
    val cardBackground = when {
        highlighted && isDark -> ElevatedDark
        highlighted -> Ice
        isDark -> DeepGraphite
        else -> Color.White
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, borderColor), cardShape)
            .background(cardBackground, cardShape)
            .padding(Spacing.md),
        content = content,
    )
}

/** 1dp hairline divider in Mist. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = if (isDark) MistDark else Mist.copy(alpha = 0.35f),
    )
}

/** Section label — caption style, Ash color. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Small status chip. Filled surfaces only — no shadows. */
@Composable
fun StatusChip(text: String, background: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun BoldEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color = SignalBlue,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            ),
            color = textColor,
        )
    }
}

@Composable
fun HeroBanner(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowDotColor: Color = ElectricCyan,
    description: String? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            BoldEyebrow(text = eyebrow, dotColor = eyebrowDotColor)
            Spacer(Modifier.height(2.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (trailingAction != null) {
                trailingAction()
            }
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LayeredCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val borderColor = accentBorder ?: if (isDark) MistDark else Mist.copy(alpha = 0.4f)
    val cardBackground = if (isDark) DeepGraphite else Color.White
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, borderColor), cardShape)
            .background(cardBackground, cardShape)
            .clip(cardShape)
            .padding(Spacing.md),
        content = content,
    )
}

@Composable
fun ModularCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedBorder = borderColor ?: if (isDark) MistDark.copy(alpha = 0.8f) else Mist.copy(alpha = 0.25f)
    val resolvedBg = backgroundColor ?: if (isDark) DeepGraphite else Color.White

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, resolvedBorder), shape)
            .background(resolvedBg, shape)
            .clip(shape),
        content = content,
    )
}

@Composable
fun ModernGlanceCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) ElevatedDark else Color.White
    val border = if (isDark) MistDark else Mist.copy(alpha = 0.25f)
    val shape = RoundedCornerShape(16.dp)

    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        shape = shape,
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
            .height(86.dp)
            .clip(shape)
            .then(clickMod),
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
                    androidx.compose.material3.Icon(
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