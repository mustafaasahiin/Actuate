package com.actuate.data.speech.audio

import com.actuate.core.audio.AudioWaveformBuffer
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Energy-based Voice Activity Detector (VAD).
 * Computes RMS energy in dBFS and tracks speech state to detect auto-stop conditions.
 */
class EnergyVad(
    private val speechThresholdDb: Float = -42.0f,
    private val silenceDurationSeconds: Float = 1.2f,
    private val maxDurationSeconds: Float = 15.0f,
    private val sampleRate: Int = 16000
) {
    private var totalSamplesProcessed: Long = 0L
    private var silenceSamplesCount: Long = 0L
    var isSpeechDetected: Boolean = false
        private set

    private val silenceThresholdSamples: Long = (silenceDurationSeconds * sampleRate).toLong()
    private val maxSamplesLimit: Long = (maxDurationSeconds * sampleRate).toLong()

    /**
     * Calculates Root Mean Square (RMS) in dBFS for a float buffer normalized to [-1.0, 1.0].
     * Returns a value typically between -100.0 dB (silence) and 0.0 dB (full scale).
     */
    fun calculateRmsDb(samples: FloatArray, offset: Int = 0, length: Int = samples.size): Float {
        if (length <= 0) return -100.0f
        var sumSquares = 0.0
        for (i in offset until (offset + length)) {
            val sample = samples[i]
            sumSquares += (sample * sample)
        }
        val meanSquare = sumSquares / length
        val rms = sqrt(meanSquare).toFloat()
        if (rms <= 1e-5f) return -100.0f
        return (20.0f * log10(rms)).coerceIn(-100.0f, 0.0f)
    }

    /**
     * Converts a 16-bit short PCM sample buffer to a normalized float buffer [-1.0f, 1.0f].
     */
    fun convertShortsToFloats(shorts: ShortArray, offset: Int = 0, length: Int = shorts.size): FloatArray {
        val floats = FloatArray(length)
        for (i in 0 until length) {
            floats[i] = (shorts[offset + i] / 32768.0f).coerceIn(-1.0f, 1.0f)
        }
        return floats
    }

    /**
     * Processes a chunk of audio samples.
     * @return VadResult containing RMS and whether recording should stop.
     */
    fun processChunk(samples: FloatArray): VadResult {
        totalSamplesProcessed += samples.size
        val rmsDb = calculateRmsDb(samples)

        if (rmsDb >= speechThresholdDb) {
            isSpeechDetected = true
            silenceSamplesCount = 0L
        } else {
            if (isSpeechDetected) {
                silenceSamplesCount += samples.size
            }
        }

        val hitMaxDuration = totalSamplesProcessed >= maxSamplesLimit
        val hitSilenceCutoff = isSpeechDetected && (silenceSamplesCount >= silenceThresholdSamples)
        val shouldStop = hitMaxDuration || hitSilenceCutoff

        return VadResult(
            rmsDb = rmsDb,
            normalizedLevel = AudioWaveformBuffer.normalizeRmsDb(rmsDb),
            isSpeechActive = isSpeechDetected && (silenceSamplesCount < silenceThresholdSamples),
            shouldStop = shouldStop,
            hitMaxDuration = hitMaxDuration,
            hitSilenceCutoff = hitSilenceCutoff
        )
    }

    fun reset() {
        totalSamplesProcessed = 0L
        silenceSamplesCount = 0L
        isSpeechDetected = false
    }

    data class VadResult(
        val rmsDb: Float,
        /**
         * [rmsDb] mapped onto `0f..1f` over the instrument range -55 dB (silence) to
         * -10 dB (speech peak). This is what the waveform visualizer consumes, so the
         * mapping lives in one place instead of in a draw call.
         */
        val normalizedLevel: Float,
        val isSpeechActive: Boolean,
        val shouldStop: Boolean,
        val hitMaxDuration: Boolean,
        val hitSilenceCutoff: Boolean
    )
}
