package com.actuate.data.speech.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class EnergyVadTest {

    @Test
    fun calculateRmsDb_withSilence_returnsMinimumDb() {
        val vad = EnergyVad()
        val silence = FloatArray(1600) { 0.0f }
        val rmsDb = vad.calculateRmsDb(silence)
        assertEquals(-100.0f, rmsDb, 0.01f)
    }

    @Test
    fun calculateRmsDb_withFullScaleSignal_returnsNearZeroDb() {
        val vad = EnergyVad()
        val fullScale = FloatArray(1600) { 1.0f }
        val rmsDb = vad.calculateRmsDb(fullScale)
        assertEquals(0.0f, rmsDb, 0.01f)
    }

    @Test
    fun calculateRmsDb_withSineWave_returnsExpectedRms() {
        val vad = EnergyVad()
        // Sine wave with amplitude 0.5 -> RMS = 0.5 / sqrt(2) ≈ 0.3535 -> 20 * log10(0.3535) ≈ -9.03 dB
        val samples = FloatArray(16000) { i ->
            (0.5 * sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toFloat()
        }
        val rmsDb = vad.calculateRmsDb(samples)
        assertEquals(-9.03f, rmsDb, 0.5f)
    }

    @Test
    fun convertShortsToFloats_normalizesCorrectly() {
        val vad = EnergyVad()
        val shorts = shortArrayOf(0, 32767, -32768, 16384, -16384)
        val floats = vad.convertShortsToFloats(shorts)

        assertEquals(0.0f, floats[0], 0.001f)
        assertEquals(32767f / 32768f, floats[1], 0.001f)
        assertEquals(-1.0f, floats[2], 0.001f)
        assertEquals(0.5f, floats[3], 0.001f)
        assertEquals(-0.5f, floats[4], 0.001f)

        for (sample in floats) {
            assertTrue(sample in -1.0f..1.0f)
        }
    }

    @Test
    fun processChunk_detectsSpeechAndTriggersSilenceCutoff() {
        // Sample rate 16kHz, silence duration 1.2s -> 19200 samples of silence to trigger cutoff
        val vad = EnergyVad(speechThresholdDb = -40.0f, silenceDurationSeconds = 1.2f, sampleRate = 16000)

        // 1. Send 1 chunk of ambient noise (-60 dB)
        val ambient = FloatArray(1600) { 0.001f }
        val initialResult = vad.processChunk(ambient)
        assertFalse(initialResult.isSpeechActive)
        assertFalse(initialResult.shouldStop)
        assertFalse(vad.isSpeechDetected)

        // 2. Send loud speech chunk (-6 dB)
        val loudSpeech = FloatArray(1600) { 0.5f }
        val speechResult = vad.processChunk(loudSpeech)
        assertTrue(speechResult.isSpeechActive)
        assertFalse(speechResult.shouldStop)
        assertTrue(vad.isSpeechDetected)

        // 3. Send 1.0 second of silence (16000 samples) -> should NOT stop yet (need 1.2s = 19200 samples)
        for (i in 0 until 10) {
            val silenceChunk = FloatArray(1600) { 0.0f }
            val midResult = vad.processChunk(silenceChunk)
            assertFalse("Should not stop at 1.0s of silence", midResult.shouldStop)
        }

        // 4. Send 2 more chunks (3200 samples) -> total silence = 19200 samples (1.2s) -> should stop!
        vad.processChunk(FloatArray(1600) { 0.0f })
        val finalResult = vad.processChunk(FloatArray(1600) { 0.0f })
        assertTrue("Should stop after 1.2s of post-speech silence", finalResult.shouldStop)
        assertTrue(finalResult.hitSilenceCutoff)
        assertFalse(finalResult.hitMaxDuration)
    }

    @Test
    fun processChunk_triggersMaxDurationCap() {
        // Max duration 2.0s -> 32000 samples limit
        val vad = EnergyVad(maxDurationSeconds = 2.0f, sampleRate = 16000)

        // Stream continuous audio for 2.0s (32000 samples)
        val chunk = FloatArray(1600) { 0.1f }
        var lastResult = vad.processChunk(chunk)
        for (i in 1 until 20) {
            lastResult = vad.processChunk(chunk)
        }

        assertTrue("Should stop on 2.0s max duration limit", lastResult.shouldStop)
        assertTrue(lastResult.hitMaxDuration)
    }

    @Test
    fun reset_clearsInternalCounters() {
        val vad = EnergyVad()
        val speech = FloatArray(1600) { 0.8f }
        vad.processChunk(speech)
        assertTrue(vad.isSpeechDetected)

        vad.reset()
        assertFalse(vad.isSpeechDetected)
    }
}
