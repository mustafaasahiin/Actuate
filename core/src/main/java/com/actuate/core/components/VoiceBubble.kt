package com.actuate.core.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ice
import com.actuate.core.theme.SignalBlue

enum class BubbleState { IDLE, LISTENING, BUSY }

/**
 * The voice bubble: an Apple Blue capsule that glows with a soft pulsing
 * Signal Blue radial aura while listening, and deglows when released.
 */
@Composable
fun VoiceBubble(
    state: BubbleState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val listening = state == BubbleState.LISTENING
    val transition = rememberInfiniteTransition(label = "bubble")
    val glowPulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )
    val bubblePulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubblePulse",
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val pulsePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1800)),
        label = "pulsePhase",
    )

    // Smooth enter/exit of the aura (glow vs deglow).
    val aura = remember { Animatable(if (listening) glowPulse else 0f) }
    LaunchedEffect(listening) {
        if (listening) {
            aura.snapTo(glowPulse)
        } else {
            aura.animateTo(0f, tween(durationMillis = 450))
        }
    }
    val scale = if (listening) bubblePulse else 1f

    Box(
        modifier = modifier.size(size + 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Radial glow behind the bubble.
        Canvas(modifier = Modifier.size(size + 28.dp)) {
            val glowRadius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SignalBlue.copy(alpha = 0.55f * aura.value),
                        SignalBlue.copy(alpha = 0.25f * aura.value),
                        SignalBlue.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (state == BubbleState.IDLE) 1f else 1f
                }
                .background(AppleBlue, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            WaveIcon(
                color = Ice,
                modifier = Modifier.size(size * 0.48f),
                listening = listening,
                pulsePhase = pulsePhase,
                barWidth = 4.dp,
            )
        }

        // Busy ring while transcribing/parsing/executing.
        if (state == BubbleState.BUSY) {
            BusyRing(
                modifier = Modifier.size(size + 12.dp),
                color = SignalBlue,
            )
        }
    }
}

@Composable
private fun BusyRing(modifier: Modifier = Modifier, color: Color) {
    val transition = rememberInfiniteTransition(label = "busy")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000)),
        label = "busyProgress",
    )
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = color.copy(alpha = 0.9f),
            startAngle = -90f + progress * 360f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
    }
}