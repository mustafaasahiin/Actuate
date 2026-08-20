package com.actuate.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Voice waveform: five vertical bars with a soft center emphasis.
 * When [listening] is true the outer bars subtly breathe.
 */
@Composable
fun WaveIcon(
    color: Color,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    pulsePhase: Float = 0f,
    barWidth: Dp = 4.dp,
) {
    val widthPx = with(LocalDensity.current) { barWidth.toPx() }
    Canvas(modifier = modifier) {
        val count = 5
        val gap = (size.width - widthPx * count) / (count - 1)
        val baseHeights = listOf(0.45f, 0.7f, 1f, 0.6f, 0.35f)
        baseHeights.forEachIndexed { index, base ->
            val breathing = if (listening) {
                0.12f * sin(pulsePhase + index * 0.9f)
            } else {
                0f
            }
            val height = size.height * (base + breathing).coerceIn(0.18f, 1f)
            val x = index * (widthPx + gap) + widthPx / 2f
            drawLine(
                color = color,
                start = Offset(x, (size.height - height) / 2f),
                end = Offset(x, (size.height + height) / 2f),
                strokeWidth = widthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Static waveform used in non-animated contexts (icons, launcher previews). */
@Composable
fun StaticWaveIcon(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 4.dp,
) {
    WaveIcon(color = color, modifier = modifier, barWidth = barWidth)
}