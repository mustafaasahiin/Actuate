package com.actuate.app.ui.home

import com.actuate.core.audio.AudioWaveformBuffer
import com.actuate.data.speech.audio.EnergyVad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The waveform shown while recording must be a faithful readout of the microphone,
 * so the pipeline's maths is tested directly rather than eyeballed on a device.
 *
 * Covers: buffer shifting, decay maths, dB normalization, and the hostile inputs a
 * real audio buffer produces (NaN, infinities, out-of-range values).
 */
class AudioWaveformPipelineTest {

    private val bands = AudioWaveformBuffer.BAND_COUNT

    // ------------------------------------------------------------- normalization

    @Test
    fun `silence floor and speech peak map to the ends of the range`() {
        assertEquals(0f, AudioWaveformBuffer.normalizeRmsDb(-55f), 1e-6f)
        assertEquals(1f, AudioWaveformBuffer.normalizeRmsDb(-10f), 1e-6f)
    }

    @Test
    fun `levels outside the instrument range clamp instead of wrapping`() {
        assertEquals(0f, AudioWaveformBuffer.normalizeRmsDb(-100f), 1e-6f)
        assertEquals(0f, AudioWaveformBuffer.normalizeRmsDb(-55.01f), 1e-6f)
        assertEquals(1f, AudioWaveformBuffer.normalizeRmsDb(0f), 1e-6f)
        assertEquals(1f, AudioWaveformBuffer.normalizeRmsDb(12f), 1e-6f)
    }

    @Test
    fun `the mapping is linear across the middle of the range`() {
        // -32.5 dB is exactly halfway between -55 and -10.
        assertEquals(0.5f, AudioWaveformBuffer.normalizeRmsDb(-32.5f), 1e-4f)
        assertEquals(0.2f, AudioWaveformBuffer.normalizeRmsDb(-46f), 1e-4f)
    }

    @Test
    fun `non-finite levels become silence rather than NaN reaching the canvas`() {
        assertEquals(0f, AudioWaveformBuffer.normalizeRmsDb(Float.NaN), 0f)
        assertEquals(0f, AudioWaveformBuffer.normalizeRmsDb(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, AudioWaveformBuffer.normalizeRmsDb(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `the VAD publishes the normalized level alongside the raw dB`() {
        val vad = EnergyVad(sampleRate = 16_000)
        val loud = FloatArray(1_600) { 0.5f }
        val result = vad.processChunk(loud)

        assertEquals(AudioWaveformBuffer.normalizeRmsDb(result.rmsDb), result.normalizedLevel, 1e-6f)
        assertTrue(result.normalizedLevel in 0f..1f)
    }

    @Test
    fun `a silent VAD chunk reports a flat level`() {
        val vad = EnergyVad(sampleRate = 16_000)
        val result = vad.processChunk(FloatArray(1_600))

        assertEquals(-100f, result.rmsDb, 1e-3f)
        assertEquals(0f, result.normalizedLevel, 1e-6f)
    }

    // ------------------------------------------------------------------- buffer

    @Test
    fun `the buffer always holds exactly the configured number of bands`() {
        val buffer = AudioWaveformBuffer()
        assertEquals(bands, buffer.snapshot().size)

        repeat(5) { buffer.push(0.5f) }
        assertEquals(bands, buffer.snapshot().size)
    }

    @Test
    fun `feeding five samples produces sixteen normalized floats in range`() {
        val buffer = AudioWaveformBuffer()
        repeat(5) { buffer.push(AudioWaveformBuffer.normalizeRmsDb(-20f)) }

        val snapshot = buffer.snapshot()
        assertEquals(bands, snapshot.size)
        assertTrue(snapshot.all { it in 0f..1f })
    }

    @Test
    fun `pushing shifts history left and appends the newest value last`() {
        val buffer = AudioWaveformBuffer(size = 4, decay = 0f)

        buffer.push(0.1f)
        assertEquals(listOf(0f, 0f, 0f, 0.1f), buffer.snapshot())

        buffer.push(0.2f)
        assertEquals(listOf(0f, 0f, 0.1f, 0.2f), buffer.snapshot())

        buffer.push(0.3f)
        assertEquals(listOf(0f, 0.1f, 0.2f, 0.3f), buffer.snapshot())

        buffer.push(0.4f)
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), buffer.snapshot())

        // The oldest value falls off the front once the window is full.
        buffer.push(0.5f)
        assertEquals(listOf(0.2f, 0.3f, 0.4f, 0.5f), buffer.snapshot())
    }

    @Test
    fun `a quiet frame decays the tail by the configured factor instead of snapping to zero`() {
        val buffer = AudioWaveformBuffer(size = 4, decay = 0.85f)

        buffer.push(1f)
        assertEquals(1f, buffer.snapshot().last(), 1e-6f)

        buffer.push(0f)
        assertEquals(0.85f, buffer.snapshot().last(), 1e-6f)

        buffer.push(0f)
        assertEquals(0.85f * 0.85f, buffer.snapshot().last(), 1e-6f)

        buffer.push(0f)
        assertEquals(0.85f * 0.85f * 0.85f, buffer.snapshot().last(), 1e-6f)
    }

    @Test
    fun `attack is instant even while the tail is still decaying`() {
        val buffer = AudioWaveformBuffer(size = 3, decay = 0.85f)

        buffer.push(0.2f)
        buffer.push(0f)
        buffer.push(1f)

        // The new loud frame is taken at face value, not blended down.
        assertEquals(1f, buffer.snapshot().last(), 1e-6f)
    }

    @Test
    fun `the tail never rises above the loudest recent frame`() {
        val buffer = AudioWaveformBuffer(size = 4, decay = 0.5f)
        buffer.push(0.4f)
        buffer.push(0f)

        // max(0, 0.4 * 0.5) == 0.2, and the decayed copy can never exceed 0.4.
        assertEquals(0.2f, buffer.snapshot().last(), 1e-6f)
        assertTrue(buffer.snapshot().all { it <= 0.4f + 1e-6f })
    }

    // ---------------------------------------------------------- hostile inputs

    @Test
    fun `NaN and infinite amplitudes are clamped to zero without crashing`() {
        val buffer = AudioWaveformBuffer(size = 4)

        buffer.push(Float.NaN)
        buffer.push(Float.NEGATIVE_INFINITY)
        buffer.push(Float.POSITIVE_INFINITY)

        // The shifted history still carries nothing but real numbers.
        assertEquals(listOf(0f, 0f, 0f, 0f), buffer.snapshot())

        buffer.push(0.9f)
        assertTrue(buffer.snapshot().all { it.isFinite() && it in 0f..1f })
    }

    @Test
    fun `out-of-range amplitudes are clamped into the display range`() {
        val buffer = AudioWaveformBuffer(size = 3, decay = 0f)

        buffer.push(-4f)
        assertEquals(0f, buffer.snapshot().last(), 0f)

        buffer.push(9f)
        assertEquals(1f, buffer.snapshot().last(), 0f)
    }

    @Test
    fun `clear flattens the history when recording stops`() {
        val buffer = AudioWaveformBuffer()
        repeat(bands) { buffer.push(1f) }
        assertTrue(buffer.snapshot().any { it > 0f })

        buffer.clear()
        assertEquals(List(bands) { 0f }, buffer.snapshot())
    }

    @Test
    fun `the buffer rejects a nonsensical configuration rather than misbehaving later`() {
        val zeroBands = runCatching { AudioWaveformBuffer(size = 0) }
        val badDecay = runCatching { AudioWaveformBuffer(decay = 1.5f) }

        assertTrue(zeroBands.isFailure)
        assertTrue(badDecay.isFailure)
    }
}
