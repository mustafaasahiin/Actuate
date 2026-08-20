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
 */
class AndroidSpeechTranscriber(context: Context) : SpeechTranscriber {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (destroyed || recognizer == null) {
            recognizer?.let { runCatching { it.destroy() } }
            destroyed = false
            recognizer = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            } else {
                null
            }
        }
        return recognizer
    }

    private var activeListener: RecognitionListener? = null

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        val sr = ensureRecognizer() ?: return false
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                activeListener = null
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                activeListener = null
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
        sr.setRecognitionListener(listener)
        sr.startListening(createIntent())
        return true
    }

    override fun stopListening() {
        ensureRecognizer()?.stopListening()
    }

    override fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        destroyed = true
        activeListener = null
    }

    private fun createIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "com.actuate.app")
        }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing heard"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech service error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Recognition error"
    }
}