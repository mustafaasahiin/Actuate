package com.actuate.data.speech.whisper

import android.content.res.AssetManager

object WhisperLib {
    init {
        val libs = listOf("c++_shared", "ggml-base", "ggml-cpu", "ggml", "whisper")
        for (lib in libs) {
            try {
                System.loadLibrary(lib)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Initializes whisper context directly from an asset file descriptor or buffer.
     * @return native context pointer (Long / uintptr_t), or 0L on failure.
     */
    external fun initContextFromAsset(assetManager: AssetManager, modelPath: String): Long

    /**
     * Frees native whisper_context and associated ggml memory.
     */
    external fun freeContext(contextPtr: Long)

    /**
     * Runs whisper inference on 16kHz mono float audio array.
     * @param contextPtr native context pointer returned by initContextFromAsset.
     * @param pcmFloatArray 32-bit float audio normalized to [-1.0f, 1.0f].
     * @param numThreads CPU threads to allocate for inference (recommended: 4).
     * @param language ISO code or "auto" for auto-detection.
     * @return transcribed text string.
     */
    external fun transcribePcm(
        contextPtr: Long,
        pcmFloatArray: FloatArray,
        numThreads: Int,
        language: String = "auto"
    ): String
}
