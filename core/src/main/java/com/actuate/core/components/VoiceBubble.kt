package com.actuate.core.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.actuate.core.audio.AudioWaveformBuffer
import com.actuate.core.audio.LocalTactileSound
import com.actuate.core.audio.TactileSoundEngine
import com.actuate.core.haptics.TactileFeedbackController
import com.actuate.core.haptics.rememberTactileFeedback
import com.actuate.core.theme.CyberBackgroundDark
import com.actuate.core.theme.ElectricCobalt
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.NotionPrismatic
import com.actuate.core.theme.SuccessEmerald
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class BubbleState {
    /** Waiting. Breathing cobalt orb with a cyan rim glow. */
    IDLE,

    /** Recording. The orb is driven by live microphone RMS. */
    LISTENING,

    /** Understanding / executing. Dual counter-rotating orbit rings. */
    BUSY,

    /** Momentary confirmation. Emerald burst that decays back to [IDLE]. */
    SUCCESS,
}

/**
 * The app's physical centre of gravity: one button that starts, stops and confirms
 * a voice capture.
 *
 * Three things make it feel alive rather than animated:
 *
 *  1. The [LISTENING] spectrum is drawn from [waveform], which is real microphone
 *     RMS history. With no audio it is flat — there is no decorative idle loop
 *     pretending to be a microphone.
 *  2. Every state change is announced twice: [sound] plays a 1.5ms mechanical
 *     transient and [haptics] fires a snap, so the button is felt before it is seen.
 *  3. All drawing happens in dedicated Canvas sub-composables, so a 60fps waveform
 *     cannot recompose the screen that owns this bubble.
 *
 * @param waveform normalized 0..1 bands, oldest first. Empty renders the calm halo.
 */
@Composable
fun VoiceBubble(
    state: BubbleState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    audioLevel: Float? = null,
    waveform: List<Float> = emptyList(),
    sound: TactileSoundEngine? = LocalTactileSound.current,
    haptics: TactileFeedbackController? = rememberTactileFeedback(),
) {
    val transition = rememberInfiniteTransition(label = "bubbleTransition")

    val haloScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )

    val haloAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )

    // One full revolution every ~4s, i.e. a slow 15rpm telemetry sweep.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spectrumPhase",
    )

    val dynamicScale = if (state == BubbleState.LISTENING && audioLevel != null) {
        1.0f + (audioLevel.coerceIn(0f, 1f) * 0.2f)
    } else {
        1.0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size * 1.6f),
    ) {
        when (state) {
            BubbleState.IDLE -> Unit // the orb's own rim glow is enough

            BubbleState.LISTENING -> {
                if (waveform.isNotEmpty()) {
                    ListeningSpectrum(
                        waveform = waveform,
                        phase = phase,
                        modulator = audioLevel,
                        modifier = Modifier.size(size * 1.6f),
                    )
                } else {
                    // Microphone produced nothing (muted, blocked, or not started yet):
                    // fall back to the calm halo instead of faking a signal.
                    Box(
                        modifier = Modifier
                            .size(size)
                            .scale(haloScale * dynamicScale)
                            .background(ElectricCobalt.copy(alpha = haloAlpha), CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(size * 1.15f)
                        .background(ElectricCobalt.copy(alpha = 0.12f), CircleShape),
                )
            }

            BubbleState.BUSY -> {
                OrbitRing(
                    phase = phase,
                    dotCount = 3,
                    color = HyperCyan,
                    modifier = Modifier.size(size * 1.45f),
                )
                OrbitRing(
                    phase = -phase,
                    dotCount = 2,
                    color = NotionPrismatic,
                    modifier = Modifier.size(size * 1.6f),
                )
            }

            BubbleState.SUCCESS -> {
                ShockwaveRings(
                    phase = phase,
                    modifier = Modifier.size(size * 1.6f),
                )
            }
        }

        Surface(
            onClick = {
                // Sound and haptics fire together on every tap so the button has one feel.
                sound?.playMicClick()
                haptics?.snap()
                onClick()
            },
            enabled = state != BubbleState.BUSY,
            shape = CircleShape,
            color = when (state) {
                BubbleState.LISTENING -> ElectricCobalt
                BubbleState.SUCCESS -> SuccessEmerald
                else -> ElectricCobalt
            },
            border = BorderStroke(
                width = 1.dp,
                color = when (state) {
                    BubbleState.LISTENING -> HyperCyan.copy(alpha = 0.9f)
                    BubbleState.SUCCESS -> SuccessEmerald.copy(alpha = 0.9f)
                    BubbleState.BUSY -> CyberBackgroundDark.copy(alpha = 0.4f)
                    BubbleState.IDLE -> HyperCyan.copy(alpha = 0.55f)
                },
            ),
            modifier = Modifier.size(size).scale(dynamicScale),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (state) {
                    BubbleState.IDLE -> Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Start recording",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.44f),
                    )

                    BubbleState.LISTENING -> Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop recording",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.44f),
                    )

                    BubbleState.BUSY -> CircularProgressIndicator(
                        color = HyperCyan,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(size * 0.45f),
                    )

                    BubbleState.SUCCESS -> Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Executed",
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.48f),
                    )
                }
            }
        }
    }
}

/**
 * Sixteen symmetrical radial bars driven by live microphone RMS.
 *
 * Isolated in its own composable so the per-frame invalidation from [phase] and
 * [modulator] stops here instead of recomposing the Home screen tree.
 */
@Composable
private fun ListeningSpectrum(
    waveform: List<Float>,
    phase: Float,
    modulator: Float?,
    modifier: Modifier = Modifier,
) {
    // Re-read only when the band values actually change, not on every draw.
    val bands = waveform.take(AudioWaveformBuffer.BAND_COUNT)
    val energy = (modulator ?: bands.lastOrNull() ?: 0f).coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val innerRadius = this.size.minDimension * 0.36f
        val barWidth = 3.dp.toPx()
        val minBar = 3.dp.toPx()
        val maxBar = 16.dp.toPx()

        // Mirror the bands so the ring reads as left/right symmetrical rather than
        // as a spiral that happens to wrap around.
        val mirrored = buildList {
            addAll(bands)
            addAll(bands.reversed())
        }
        val count = mirrored.size.coerceAtLeast(1)

        for (i in 0 until count) {
            val amplitude = mirrored.getOrElse(i) { 0f }
            val angle = (i.toFloat() / count) * 2f * PI + phase
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val length = minBar + amplitude * maxBar * (0.5f + energy * 0.5f)

            val start = Offset(
                center.x + direction.x * innerRadius,
                center.y + direction.y * innerRadius,
            )
            val end = Offset(
                center.x + direction.x * (innerRadius + length),
                center.y + direction.y * (innerRadius + length),
            )

            // Cyan at the tip, cobalt at the base: reads as energy leaving the orb.
            drawLine(
                color = HyperCyan.copy(alpha = 0.55f + amplitude * 0.45f),
                start = start,
                end = end,
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = ElectricCobalt.copy(alpha = 0.5f),
                start = start,
                end = Offset(
                    center.x + direction.x * (innerRadius + length * 0.4f),
                    center.y + direction.y * (innerRadius + length * 0.4f),
                ),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Counter-rotating particle ring used by [BubbleState.BUSY]. */
@Composable
private fun OrbitRing(
    phase: Float,
    dotCount: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 2.dp.toPx()

        drawCircle(
            color = color.copy(alpha = 0.18f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        for (i in 0 until dotCount) {
            val angle = (i.toFloat() / dotCount) * 2f * PI + phase
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawCircle(
                color = color.copy(alpha = 0.35f),
                radius = 4.dp.toPx(),
                center = Offset(
                    center.x + direction.x * radius,
                    center.y + direction.y * radius,
                ),
            )
            drawCircle(
                color = color,
                radius = 2.dp.toPx(),
                center = Offset(
                    center.x + direction.x * radius,
                    center.y + direction.y * radius,
                ),
            )
        }
    }
}

/** Expanding emerald shockwaves for [BubbleState.SUCCESS]. */
@Composable
private fun ShockwaveRings(
    phase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2f

        val fullTurn = (2 * PI).toFloat()

        // Two rings out of phase so the burst reads as a pulse, not a single blip.
        for (offset in listOf(0f, 0.5f)) {
            val progress = ((phase / fullTurn) + offset) % 1f
            val radius = maxRadius * (0.45f + progress * 0.55f)
            val alpha = (1f - progress) * 0.55f
            drawCircle(
                color = SuccessEmerald.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
