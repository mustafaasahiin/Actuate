package com.actuate.data.speech.whisper

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe engine wrapper for whisper.cpp.
 * Confines all native inference and context operations to a dedicated single-thread dispatcher,
 * preventing concurrency bugs on the underlying C++ whisper_context.
 */
class WhisperEngine(
    private val whisperDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "whisper-inference").apply { isDaemon = true }
    }.asCoroutineDispatcher()
) {
    private var contextPtr: Long = 0L
    private val isInitialized = AtomicBoolean(false)
    private val mutex = Mutex()

    /**
     * Initializes the native whisper context from an Android asset file.
     * @return true if initialization succeeded, false otherwise.
     */
    suspend fun initialize(assetManager: AssetManager, modelAssetPath: String): Boolean = withContext(whisperDispatcher) {
        mutex.withLock {
            if (isInitialized.get() && contextPtr != 0L) {
                return@withContext true
            }

            try {
                val ptr = WhisperLib.initContextFromAsset(assetManager, modelAssetPath)
                if (ptr != 0L) {
                    contextPtr = ptr
                    isInitialized.set(true)
                    true
                } else {
                    false
                }
            } catch (e: Throwable) {
                false
            }
        }
    }

    /**
     * Runs transcription on the provided 16kHz mono PCM FloatArray.
     * @param pcmSamples 32-bit float audio normalized to [-1.0f, 1.0f].
     * @param numThreads CPU threads to allocate for inference (default: 4).
     * @param language ISO code or "auto" for auto-detection.
     * @return Transcribed text trimmed, or empty string on error / no speech.
     */
    suspend fun transcribe(
        pcmSamples: FloatArray,
        numThreads: Int = 4,
        language: String = "auto"
    ): String = withContext(whisperDispatcher) {
        mutex.withLock {
            if (!isInitialized.get() || contextPtr == 0L || pcmSamples.isEmpty()) {
                return@withContext ""
            }

            try {
                WhisperLib.transcribePcm(
                    contextPtr = contextPtr,
                    pcmFloatArray = pcmSamples,
                    numThreads = numThreads,
                    language = language
                ).trim()
            } catch (e: Throwable) {
                ""
            }
        }
    }

    /**
     * Releases the native context pointer and frees associated GGML memory.
     */
    suspend fun release() = withContext(whisperDispatcher) {
        mutex.withLock {
            if (contextPtr != 0L) {
                try {
                    WhisperLib.freeContext(contextPtr)
                } catch (_: Throwable) {}
                contextPtr = 0L
            }
            isInitialized.set(false)
        }
    }

    fun isReady(): Boolean = isInitialized.get() && contextPtr != 0L
}
