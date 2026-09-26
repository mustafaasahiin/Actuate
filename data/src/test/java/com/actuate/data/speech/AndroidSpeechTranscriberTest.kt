package com.actuate.data.speech

import android.content.Context
import android.speech.SpeechRecognizer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSpeechTranscriberTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun recoverableErrorsContainsRequiredCodes() {
        assertTrue(AndroidSpeechTranscriber.RECOVERABLE_ERRORS.contains(SpeechRecognizer.ERROR_CLIENT))
        assertTrue(AndroidSpeechTranscriber.RECOVERABLE_ERRORS.contains(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertTrue(AndroidSpeechTranscriber.RECOVERABLE_ERRORS.contains(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
        assertTrue(AndroidSpeechTranscriber.RECOVERABLE_ERRORS.contains(SpeechRecognizer.ERROR_NO_MATCH))
    }

    @Test
    fun resetListeningStateSetsIsListeningToFalse() {
        every { context.applicationContext } returns context
        val transcriber = AndroidSpeechTranscriber(context)

        assertFalse(transcriber.isListening)
        transcriber.resetListeningState()
        assertFalse(transcriber.isListening)
    }

    @Test
    fun destroyResetsListeningState() {
        every { context.applicationContext } returns context
        val transcriber = AndroidSpeechTranscriber(context)

        transcriber.destroy()
        assertFalse(transcriber.isListening)
    }
}
