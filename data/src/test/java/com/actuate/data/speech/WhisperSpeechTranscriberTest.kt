package com.actuate.data.speech

import android.content.Context
import com.actuate.data.speech.audio.AudioRecordStreamer
import com.actuate.data.speech.whisper.WhisperEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WhisperSpeechTranscriberTest {

    private val context: Context = mockk(relaxed = true)
    private val streamer: AudioRecordStreamer = mockk(relaxed = true)
    private val engine: WhisperEngine = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var transcriber: WhisperSpeechTranscriber

    @Before
    fun setup() {
        every { context.applicationContext } returns context
        transcriber = WhisperSpeechTranscriber(
            context = context,
            mainDispatcher = testDispatcher,
            streamer = streamer,
            engine = engine
        )
    }

    @Test
    fun resetListeningStateSetsIsListeningToFalse() {
        assertFalse(transcriber.isListening)
        transcriber.resetListeningState()
        assertFalse(transcriber.isListening)
    }

    @Test
    fun destroyResetsListeningState() {
        transcriber.destroy()
        assertFalse(transcriber.isListening)
        verify { streamer.release() }
    }

    @Test
    fun startListening_startsStreamer() {
        every { streamer.startRecording(any(), any(), any()) } returns true

        val started = transcriber.startListening(
            onPartial = {},
            onFinal = {},
            onError = {}
        )

        assertTrue(started)
        assertTrue(transcriber.isListening)
        verify { streamer.startRecording(any(), any(), any()) }
    }

    @Test
    fun stopListening_stopsStreamer() {
        every { streamer.startRecording(any(), any(), any()) } returns true
        transcriber.startListening(
            onPartial = {},
            onFinal = {},
            onError = {}
        )
        assertTrue(transcriber.isListening)

        transcriber.stopListening()
        assertFalse(transcriber.isListening)
        verify { streamer.stopRecording() }
    }

    @Test
    fun onAudioComplete_withValidAudio_callsTranscribeAndDeliversOnFinal() = runTest {
        val testTranscriber = WhisperSpeechTranscriber(
            context = context,
            mainDispatcher = testDispatcher,
            streamer = streamer,
            engine = engine,
            scope = this
        )

        var onCompleteCaptured: ((FloatArray) -> Unit)? = null
        every {
            streamer.startRecording(any(), captureLambda(), any())
        } answers {
            onCompleteCaptured = secondArg()
            true
        }

        every { engine.isReady() } returns true
        coEvery { engine.transcribe(any(), any(), any()) } returns "Schedule meeting tomorrow"

        var finalResult = ""
        testTranscriber.startListening(
            onPartial = {},
            onFinal = { text -> finalResult = text },
            onError = {}
        )

        val sampleAudio = FloatArray(16000) { 0.1f }
        onCompleteCaptured?.invoke(sampleAudio)
        testScheduler.advanceUntilIdle()

        assertEquals("Schedule meeting tomorrow", finalResult)
    }

    @Test
    fun onAudioComplete_withEmptyAudio_callsOnError() = runTest {
        val testTranscriber = WhisperSpeechTranscriber(
            context = context,
            mainDispatcher = testDispatcher,
            streamer = streamer,
            engine = engine,
            scope = this
        )

        var onCompleteCaptured: ((FloatArray) -> Unit)? = null
        every {
            streamer.startRecording(any(), captureLambda(), any())
        } answers {
            onCompleteCaptured = secondArg()
            true
        }

        var errorResult = ""
        testTranscriber.startListening(
            onPartial = {},
            onFinal = {},
            onError = { error -> errorResult = error }
        )

        onCompleteCaptured?.invoke(FloatArray(0))
        testScheduler.advanceUntilIdle()

        assertEquals("No speech detected", errorResult)
    }
}
