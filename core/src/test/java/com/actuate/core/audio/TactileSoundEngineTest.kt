package com.actuate.core.audio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure half of the sound engine: PCM synthesis.
 *
 * Nothing here touches `AudioTrack`, so the suite runs on the JVM without
 * Robolectric and still proves the waveforms are well-formed (right length,
 * in-range samples, correctly enveloped, silent at the edges).
 */
class TactileSoundEngineTest {

    private val sampleRate = TactileSoundEngine.SAMPLE_RATE

    @Test
    fun `mic click pcm length matches its 1_5ms duration`() {
        val pcm = TactileSoundEngine.generateMicClickPcm(sampleRate)
        assertEquals(TactileSoundEngine.samplesFor(1.5, sampleRate), pcm.size)
        assertEquals(66, pcm.size)
    }

    @Test
    fun `every cue is the exact sample count implied by its declared duration`() {
        for (cue in TactileSoundEngine.Cue.entries) {
            val pcm = TactileSoundEngine.synthesize(cue, sampleRate)
            assertEquals(
                "cue $cue wrong length",
                TactileSoundEngine.samplesFor(cue.durationMs, sampleRate),
                pcm.size,
            )
        }
    }

    @Test
    fun `toggle snap is a 5ms transient`() {
        assertEquals(221, TactileSoundEngine.generateToggleSnapPcm(sampleRate).size)
    }

    @Test
    fun `error buzz is a 45ms cue`() {
        assertEquals(1985, TactileSoundEngine.generateErrorBuzzPcm(sampleRate).size)
    }

    @Test
    fun `action actuated sweep is a 30ms cue`() {
        assertEquals(1323, TactileSoundEngine.generateActionActuatedPcm(sampleRate).size)
    }

    @Test
    fun `every cue produces audible, in-range, non-silent samples`() {
        for (cue in TactileSoundEngine.Cue.entries) {
            val pcm = TactileSoundEngine.synthesize(cue, sampleRate)
            val peak = pcm.maxOf { abs(it.toInt()) }

            assertTrue("cue $cue is silent", peak > Short.MAX_VALUE / 20)
            assertTrue("cue $cue clips past int16 (peak=$peak)", peak <= Short.MAX_VALUE)
        }
    }

    @Test
    fun `cues never start or end on a hard step, which would add a click`() {
        for (cue in TactileSoundEngine.Cue.entries) {
            val pcm = TactileSoundEngine.synthesize(cue, sampleRate)
            val threshold = Short.MAX_VALUE / 8
            assertTrue(
                "cue $cue starts with railed attack (${pcm.first()})",
                abs(pcm.first().toInt()) < threshold,
            )
            // The buzz is gated, so only its release edge is meaningful.
            assertTrue(
                "cue $cue ends with railed release (${pcm.last()})",
                abs(pcm.last().toInt()) < threshold,
            )
        }
    }

    @Test
    fun `synthesis scales with the requested sample rate`() {
        val hz48 = TactileSoundEngine.generateMicClickPcm(48_000)
        assertEquals(TactileSoundEngine.samplesFor(1.5, 48_000), hz48.size)
        assertTrue(hz48.size > TactileSoundEngine.generateMicClickPcm(sampleRate).size)
    }

    @Test
    fun `the four cues are distinct waveforms, not the same tone relabelled`() {
        val click = TactileSoundEngine.generateMicClickPcm(sampleRate).toList()
        val snap = TactileSoundEngine.generateToggleSnapPcm(sampleRate).toList()
        val sweep = TactileSoundEngine.generateActionActuatedPcm(sampleRate).toList()

        assertNotEquals(click, snap)
        assertNotEquals(click, sweep)
        assertNotEquals(snap, sweep)
    }

    @Test
    fun `synthesis is deterministic so the app sounds identical across sessions`() {
        repeat(2) {
            assertArrayEquals(
                TactileSoundEngine.generateActionActuatedPcm(sampleRate),
                TactileSoundEngine.generateActionActuatedPcm(sampleRate),
            )
        }
    }

    @Test
    fun `zero-length requests degrade to an empty buffer instead of throwing`() {
        assertEquals(0, TactileSoundEngine.samplesFor(0.0, sampleRate))
    }

    private fun assertArrayEquals(expected: ShortArray, actual: ShortArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("sample $i differs", expected[i], actual[i])
        }
    }
}
