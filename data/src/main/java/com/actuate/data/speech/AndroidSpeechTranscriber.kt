package com.actuate.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.actuate.domain.speech.SpeechTranscriber
import java.util.Locale

/**
 * On-device speech recognition via the platform [SpeechRecognizer].
 * No API key required; results may use on-device language packs when
 * available (offline capable on most devices).
 * 
 * Free improvements: language model selection, partial results, better
 * error messages, and speech timeout configuration.
 */
class AndroidSpeechTranscriber(context: Context) : SpeechTranscriber {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false

    var isListening: Boolean = false
        private set

    /**
     * Set when a session ends in a service-level error (busy, client crash,
     * server failure). The recognizer instance is torn down so the next
     * [startListening] binds to a fresh recognition service instead of
     * silently failing on a dead one.
     */
    private var recreateOnNextStart = false

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (destroyed || recreateOnNextStart || recognizer == null) {
            recognizer?.let { runCatching { it.destroy() } }
            recognizer = null
            destroyed = false
            recreateOnNextStart = false
            recognizer = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } else {
                null
            }
        }
        return recognizer
    }

    private var activeListener: RecognitionListener? = null

    fun resetListeningState() {
        isListening = false
        activeListener = null
    }

    fun reinitializeRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        recreateOnNextStart = false
        recognizer = if (!destroyed && SpeechRecognizer.isRecognitionAvailable(appContext)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        } else {
            null
        }
    }

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        val sr = ensureRecognizer() ?: run {
            resetListeningState()
            return false
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                resetListeningState()
                if (error in RECOVERABLE_ERRORS) {
                    reinitializeRecognizer()
                } else if (error in RECOVERABLE_BY_RECREATE) {
                    recreateOnNextStart = true
                }
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                resetListeningState()
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!text.isNullOrEmpty()) onFinal(text) else onError("Nothing heard")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrEmpty()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        activeListener = listener
        isListening = true
        sr.setRecognitionListener(listener)
        sr.startListening(createIntent())
        return true
    }

    override fun stopListening() {
        resetListeningState()
        ensureRecognizer()?.stopListening()
    }

    override fun destroy() {
        resetListeningState()
        runCatching { recognizer?.destroy() }
        recognizer = null
        destroyed = true
    }

    private fun createIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "com.actuate.app")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
        }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error - please check your microphone"
        SpeechRecognizer.ERROR_CLIENT -> "Client error - recognition service failed"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_NETWORK -> "Network error - check your internet connection"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout - connection is slow"
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing heard - try speaking clearer"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy - please wait a moment"
        SpeechRecognizer.ERROR_SERVER -> "Speech service error - try again later"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Server disconnected - reconnecting"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected - try speaking louder"
        else -> "Recognition error"
    }

    companion object {
        val RECOVERABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        )

        val RECOVERABLE_BY_RECREATE = RECOVERABLE_ERRORS
    }
}