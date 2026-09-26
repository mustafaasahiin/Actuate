package com.actuate.data.speech.whisper

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperEngineTest {

    @Test
    fun isReady_initiallyFalse() {
        val testDispatcher = StandardTestDispatcher()
        val engine = WhisperEngine(testDispatcher)
        assertFalse(engine.isReady())
    }

    @Test
    fun transcribe_whenNotInitialized_returnsEmptyString() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = WhisperEngine(testDispatcher)
        val result = engine.transcribe(FloatArray(16000) { 0.1f })
        assertEquals("", result)
    }

    @Test
    fun transcribe_withEmptySamples_returnsEmptyString() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = WhisperEngine(testDispatcher)
        val result = engine.transcribe(FloatArray(0))
        assertEquals("", result)
    }

    @Test
    fun release_whenNotInitialized_isIdempotent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val engine = WhisperEngine(testDispatcher)
        engine.release()
        engine.release()
        assertFalse(engine.isReady())
    }
}
