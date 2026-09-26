package com.actuate.app.ui.paywall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.GoldDeep
import com.actuate.core.theme.GoldLight
import com.actuate.core.theme.GoldPrimary
import com.actuate.core.theme.TelemetrySmall
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun cos(value: Float): Float = kotlin.math.cos(value.toDouble()).toFloat()

private fun sin(value: Float): Float = kotlin.math.sin(value.toDouble()).toFloat()

private val GOLD = GoldPrimary
private val GOLD_DEEP = GoldDeep
private val GOLD_PALE = GoldLight

/**
 * Canvas particle burst for the judge-pass unlock.
 *
 * Seeded with a fixed value on purpose: the celebration is part of the demo, and a
 * deterministic burst means two runs of the same build look identical on camera and
 * the effect can be screenshot-diffed.
 *
 * Draws once through a single [Animatable] progress value rather than per-particle
 * animation state, so the whole burst is one invalidation per frame.
 */
@Composable
fun ConfettiBurst(
    play: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 90,
    seed: Int = 2026,
) {
    if (!play) return

    val progress = remember { Animatable(0f) }
    LaunchedEffect(play) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2200, easing = LinearEasing),
        )
    }

    val particles = remember(particleCount, seed) {
        val random = Random(seed)
        List(particleCount) {
            ConfettiParticle(
                angle = random.nextDouble(0.0, 2 * Math.PI),
                speed = random.nextDouble(0.25, 1.0).toFloat(),
                drift = random.nextDouble(-0.25, 0.25).toFloat(),
                spin = random.nextDouble(-540.0, 540.0).toFloat(),
                size = random.nextDouble(4.0, 10.0).toFloat(),
                delay = random.nextDouble(0.0, 0.25).toFloat(),
                color = when (random.nextInt(3)) {
                    0 -> GOLD
                    1 -> GOLD_PALE
                    else -> GOLD_DEEP
                },
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2f, size.height * 0.28f)
        val t = progress.value

        particles.forEach { particle ->
            val local = ((t - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach

            // Launch outward, then arc: horizontal velocity persists, vertical gains pull.
            val distance: Float = particle.speed * size.height * 0.85f * local
            val angle: Float = particle.angle.toFloat()
            val x: Float = origin.x + cos(angle) * distance + particle.drift * size.width * local
            val y: Float = origin.y + sin(angle) * distance + 0.9f * size.height * local * local

            rotate(degrees = particle.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = particle.color.copy(alpha = (1f - local).coerceIn(0f, 1f)),
                    topLeft = Offset(x, y),
                    size = Size(particle.size, particle.size * 1.8f),
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val angle: Double,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val size: Float,
    val delay: Float,
    val color: Color,
)

/**
 * The golden status plaque shown once the judge pass is active.
 *
 * Gold is used nowhere else in the product, which is exactly why it works here: the
 * badge is instantly legible as "this build is unlocked" in a screenshot.
 */
@Composable
fun JudgePassBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GOLD.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, GOLD.copy(alpha = 0.55f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(GOLD.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = GOLD,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "ACTUATE PRO · UNLIMITED JUDGE PASS",
            style = TelemetrySmall.copy(fontWeight = FontWeight.Bold),
            color = GOLD,
        )
    }
}

