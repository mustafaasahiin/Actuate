package com.actuate.core.audio

import kotlin.math.max

/**
 * Rolling, normalized amplitude history that drives the live waveform.
 *
 * This is deliberately a pure value holder with no Android or Compose dependency:
 * the visualizer is the most-scrutinised thing in the app, so its maths is kept
 * where it can be unit tested rather than buried in a draw lambda.
 *
 * Two properties matter for the look:
 *
 *  1. **Attack is instant, release is exponential.** A new sample is taken at face
 *     value so the bars jump the moment the user speaks, but a quiet sample only
 *     overwrites the tail with `previous * decay` so the waveform collapses instead
 *     of blinking. See [push].
 *  2. **Nothing that arrives from a microphone is trusted.** NaN, infinities and
 *     out-of-range values are all sanitised to `0f..1f` before they can reach the
 *     canvas, where a NaN would silently blank the drawing layer.
 */
class AudioWaveformBuffer(
    val size: Int = BAND_COUNT,
    private val decay: Float = DEFAULT_DECAY,
) {

    init {
        require(size > 0) { "AudioWaveformBuffer needs at least one band, got $size" }
        require(decay in 0f..1f) { "decay must be a 0..1 falloff factor, got $decay" }
    }

    private val bands = FloatArray(size)

    /** The current amplitudes, oldest first, newest last. Always [size] floats in `0f..1f`. */
    fun snapshot(): List<Float> = bands.toList()

    /**
     * Shifts the history left by one band and appends [amplitude].
     *
     * The appended value is `max(amplitude, previousTail * decay)`, which is what makes
     * a loud frame hold the tail up for a beat while a silent frame lets it fall away.
     */
    fun push(amplitude: Float) {
        val sanitized = sanitize(amplitude)
        val previousTail = bands[size - 1]
        for (i in 0 until size - 1) {
            bands[i] = bands[i + 1]
        }
        bands[size - 1] = max(sanitized, previousTail * decay)
    }

    /** Flattens the history to silence — called when recording stops. */
    fun clear() {
        bands.fill(0f)
    }

    companion object {
        /** 16 bands: enough to read as a waveform, few enough to never alias on a phone. */
        const val BAND_COUNT = 16

        /** How much of the previous tail survives one quiet frame. */
        const val DEFAULT_DECAY = 0.85f

        /** Below this the room is silent and the waveform should read as flat. */
        const val SILENCE_FLOOR_DB = -55f

        /** A normal speaking voice at arm's length. */
        const val SPEECH_PEAK_DB = -10f

        /**
         * Maps an RMS level in dBFS onto `0f..1f` for display.
         *
         * The instrument range is [-55, -10] dB rather than the theoretical [-100, 0]:
         * a phone microphone held near a face practically never leaves that band, and
         * mapping the full 100 dB of headroom makes normal speech render as a flat line.
         *
         * Non-finite input (a broken buffer, a divide by zero upstream) maps to silence
         * rather than propagating.
         */
        fun normalizeRmsDb(rmsDb: Float): Float {
            if (!rmsDb.isFinite()) return 0f
            val span = SPEECH_PEAK_DB - SILENCE_FLOOR_DB
            return ((rmsDb - SILENCE_FLOOR_DB) / span).coerceIn(0f, 1f)
        }

        /** Clamps any input into the safe display range; non-finite becomes silence. */
        fun sanitize(amplitude: Float): Float =
            if (!amplitude.isFinite()) 0f else amplitude.coerceIn(0f, 1f)
    }
}
