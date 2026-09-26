package com.actuate.data.speech

import android.content.Context
import com.actuate.data.speech.audio.AudioRecordStreamer
import com.actuate.data.speech.audio.DefaultAudioRecordStreamer
import com.actuate.data.speech.whisper.WhisperEngine
import com.actuate.domain.speech.SpeechTranscriber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device speech recognition powered by whisper.cpp.
 * Combines 16kHz PCM audio streaming with silence-based VAD (Approach A)
 * and native Whisper offline transcription with automatic language detection.
 */
class WhisperSpeechTranscriber(
    context: Context,
    private val modelAssetPath: String = DEFAULT_MODEL_PATH,
    private val language: String = "auto",
    private val numThreads: Int = DEFAULT_THREADS,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val streamer: AudioRecordStreamer = DefaultAudioRecordStreamer(),
    private val engine: WhisperEngine = WhisperEngine(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : SpeechTranscriber {

    private val appContext = context.applicationContext

    private val _isListening = AtomicBoolean(false)
    val isListening: Boolean
        get() = _isListening.get()

    private var activeJob: Job? = null
    private var isDestroyed = false
    private var audioLevelListener: ((Float) -> Unit)? = null
    private var transcribingListener: (() -> Unit)? = null
    override fun setAudioLevelListener(listener: (Float) -> Unit) { audioLevelListener = listener }
    override fun setTranscribingListener(listener: () -> Unit) { transcribingListener = listener }

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (isDestroyed) {
            return false
        }

        if (_isListening.get()) {
            return true
        }

        _isListening.set(true)

        val started = streamer.startRecording(
            onRmsChanged = { rmsDb ->
                if (_isListening.get()) {
                    scope.launch(mainDispatcher) {
                        audioLevelListener?.invoke(rmsDb)
                    }
                }
            },
            onAudioComplete = { pcmSamples ->
                _isListening.set(false)
                handleAudioComplete(pcmSamples, onPartial, onFinal, onError)
            },
            onError = { errorMsg ->
                _isListening.set(false)
                scope.launch(mainDispatcher) {
                    onError(errorMsg)
                }
            }
        )

        if (!started) {
            _isListening.set(false)
            return false
        }

        return true
    }

    private fun handleAudioComplete(
        pcmSamples: FloatArray,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                if (pcmSamples.isEmpty()) {
                    withContext(mainDispatcher) {
                        onError("No speech detected")
                    }
                    return@launch
                }

                withContext(mainDispatcher) {
                    transcribingListener?.invoke()
                }

                if (!engine.isReady()) {
                    val initResult = withTimeoutOrNull(MODEL_INIT_TIMEOUT_MS) {
                        engine.initialize(appContext.assets, modelAssetPath)
                    }
                    if (initResult != true) {
                        withContext(mainDispatcher) {
                            onError("Failed to initialize whisper model")
                        }
                        return@launch
                    }
                }

                val text = withTimeoutOrNull(TRANSCRIPTION_TIMEOUT_MS) {
                    engine.transcribe(
                        pcmSamples = pcmSamples,
                        numThreads = numThreads,
                        language = language
                    )
                }

                if (text == null) {
                    withContext(mainDispatcher) {
                        onError("Transcription timed out. Please try speaking again.")
                    }
                    return@launch
                }

                withContext(mainDispatcher) {
                    if (text.isNotBlank()) {
                        onFinal(text)
                    } else {
                        onError("No speech detected")
                    }
                }
            } catch (t: Throwable) {
                withContext(mainDispatcher) {
                    onError("Transcription error: ${t.message ?: "Unknown error"}")
                }
            }
        }
    }

    override fun stopListening() {
        if (_isListening.getAndSet(false)) {
            streamer.stopRecording()
        }
    }

    fun resetListeningState() {
        _isListening.set(false)
        streamer.stopRecording()
        activeJob?.cancel()
        activeJob = null
    }

    override fun destroy() {
        isDestroyed = true
        resetListeningState()
        streamer.release()
        scope.launch {
            engine.release()
        }
    }

    companion object {
        const val DEFAULT_MODEL_PATH = "models/ggml-base-q5_1.bin"
        val DEFAULT_THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        const val MODEL_INIT_TIMEOUT_MS = 10_000L
        const val TRANSCRIPTION_TIMEOUT_MS = 15_000L
    }
}
