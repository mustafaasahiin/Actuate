package com.actuate.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radii from design.md: 8dp containers, 980dp capsules. Nothing else.

val ActuateShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val StandardCardRadius = 12.dp
val CompactCardRadius = 8.dp
val DialogRadius = 16.dp

val CardShape = RoundedCornerShape(12.dp)
val CardShapeCompact = RoundedCornerShape(8.dp)

val Capsule = RoundedCornerShape(percent = 50)

/**
 * Primary panel shape for the mission-control surfaces.
 *
 * One corner is deliberately much tighter than the others (the "technical
 * micro-corner"). It reads as a machined part rather than a rounded app card,
 * and it gives every stacked panel a consistent orientation cue — the soft
 * corners face content, the hard corner faces the timeline rail.
 */
val CyberCardShape = RoundedCornerShape(
    topStart = 12.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
    bottomStart = 4.dp,
)

/** Compact variant of [CyberCardShape] for nested rows and list surfaces. */
val CyberCardShapeCompact = RoundedCornerShape(
    topStart = 8.dp,
    topEnd = 8.dp,
    bottomEnd = 8.dp,
    bottomStart = 4.dp,
)

/** Pixel-tight frame used by monospace status badges. */
val TelemetryBadgeShape = RoundedCornerShape(4.dp)

// Spacing scale (4dp grid): 4, 8, 12, 16, 20, 24, 40, 48
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 40.dp
    val xxxl = 48.dp
}
