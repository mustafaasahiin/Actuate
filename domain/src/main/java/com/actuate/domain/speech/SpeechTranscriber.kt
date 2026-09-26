package com.actuate.domain.speech

/**
 * Wraps the platform speech recognizer. On Android this is
 * [android.speech.SpeechRecognizer] — on-device, no API key.
 */
interface SpeechTranscriber {

    /**
     * Starts listening. Returns false if speech recognition is unavailable.
     * Callbacks are delivered on the main thread.
     */
    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ): Boolean

    fun stopListening()
    /** Optional live microphone levels, in dB. Null support means no waveform is drawn. */
    fun setAudioLevelListener(listener: (Float) -> Unit) {}
    fun setTranscribingListener(listener: () -> Unit) {}
    fun destroy()
}
