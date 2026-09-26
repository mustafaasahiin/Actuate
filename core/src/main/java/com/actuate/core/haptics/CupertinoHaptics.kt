package com.actuate.core.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@SuppressLint("MissingPermission")
class CupertinoHaptics(
    private val context: Context,
    private val view: android.view.View?,
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Subtle click when selecting items, tabs, or toggling segmented pills. */
    fun selectionChanged() {
        if (view?.isHapticFeedbackEnabled == true) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    /** Light tap on buttons, chips, and checkboxes. */
    fun impactLight() {
        if (view?.isHapticFeedbackEnabled == true) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
    }

    /** Confirmatory pulse on task completion or successful action execution. */
    fun impactSuccess() {
        if (view?.isHapticFeedbackEnabled == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Subtle warning pulse on destructive actions or error alerts. */
    fun impactWarning() {
        if (view?.isHapticFeedbackEnabled == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 25, 40, 25), -1))
        }
    }
}

@Composable
fun rememberCupertinoHaptics(): CupertinoHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        CupertinoHaptics(context, view)
    }
}
