package com.actuate.core.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The physical half of Actuate's feedback language.
 *
 * Sound and haptics always fire together for the same event, so the app has one
 * feel rather than two competing ones: a snap is both heard and felt on a
 * destination switch, and a commit is a rising sweep in the hand as well as the
 * ear.
 *
 * Every method is total: unsupported hardware, a disabled system haptics
 * setting, or a missing vibrator all resolve to a silent no-op instead of an
 * exception on the interaction path.
 */
@SuppressLint("MissingPermission")
class TactileFeedbackController(
    private val context: Context,
    private val enabledProvider: () -> Boolean = {
        com.actuate.core.audio.FeedbackPreferences.isHapticsEnabled(context)
    },
) {

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    private val hasAmplitudeControl: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            (vibrator?.hasAmplitudeControl() ?: false)

    private fun vibrate(effect: VibrationEffect?) {
        val vibrator = vibrator ?: return
        if (effect == null || !vibrator.hasVibrator()) return
        if (!enabledProvider()) return
        runCatching { vibrator.vibrate(effect) }
    }

    /** Light tick: segmented pills, list selection, scrolling to a new hour. */
    fun tick() = vibrate(predefined(VibrationEffect.EFFECT_TICK, fallbackMs = 8))

    /** Crisp snap: checkbox toggle, destination chip switch, segmented stepper. */
    fun snap() = vibrate(
        if (hasAmplitudeControl) {
            // 3ms at 60% then 6ms at 100% — a mechanical "thock", not a buzz.
            VibrationEffect.createWaveform(longArrayOf(0, 3, 4, 6), intArrayOf(0, 150, 0, 255), -1)
        } else {
            predefined(VibrationEffect.EFFECT_CLICK, fallbackMs = 10)
        },
    )

    /** Confirmatory two-beat: a batch of actions was executed successfully. */
    fun success() = vibrate(
        if (hasAmplitudeControl) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 12, 40, 22),
                intArrayOf(0, 180, 0, 255),
                -1,
            )
        } else {
            predefined(VibrationEffect.EFFECT_DOUBLE_CLICK, fallbackMs = 40)
        },
    )

    /** Rejection: quota exhausted, parse failed, network down. */
    fun error() = vibrate(
        if (hasAmplitudeControl) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 18, 50, 18),
                intArrayOf(0, 255, 0, 200),
                -1,
            )
        } else {
            predefined(VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 30)
        },
    )

    /** A long, celebratory rumble used by the judge-pass confetti burst. */
    fun celebration() = vibrate(
        if (hasAmplitudeControl) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 14, 24, 14, 24, 18, 24, 26),
                intArrayOf(0, 120, 0, 190, 0, 230, 0, 255),
                -1,
            )
        } else {
            predefined(VibrationEffect.EFFECT_DOUBLE_CLICK, fallbackMs = 60)
        },
    )

    private fun predefined(constant: Int, fallbackMs: Long): VibrationEffect? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(constant)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(
                fallbackMs,
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
        } else {
            null
        }
}

@Composable
fun rememberTactileFeedback(): TactileFeedbackController {
    val context = LocalContext.current
    return remember(context) { TactileFeedbackController(context.applicationContext) }
}
