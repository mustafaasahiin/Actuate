package com.actuate.data.speech.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

interface AudioRecordStreamer {
    /**
     * Starts audio recording in background.
     * @param onRmsChanged Callback emitting live RMS energy (dB) for UI pulsation.
     * @param onAudioComplete Callback emitting normalized FloatArray [-1.0f, 1.0f] (16kHz mono)
     *                        when silence VAD triggers or 15s hard safety cap is reached.
     * @param onError Callback emitting error description if AudioRecord initialization fails.
     */
    fun startRecording(
        onRmsChanged: (Float) -> Unit,
        onAudioComplete: (FloatArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean

    /**
     * Manually stops recording and immediately flushes captured buffer to onAudioComplete.
     */
    fun stopRecording()

    /**
     * Cancels recording, drops buffers, and releases AudioRecord hardware.
     */
    fun release()
}

/**
 * Default AudioRecordStreamer implementation for 16kHz mono PCM capture
 * with real-time RMS energy computation and silence-based auto-stop.
 */
class DefaultAudioRecordStreamer(
    private val sampleRate: Int = 16000,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    private val vad: EnergyVad = EnergyVad(sampleRate = sampleRate),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AudioRecordStreamer {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val bufferLock = Any()
    private val recordedChunks = mutableListOf<FloatArray>()
    private var totalSamplesRecorded = 0

    private var onAudioCompleteCallback: ((FloatArray) -> Unit)? = null
    private var onRmsCallback: ((Float) -> Unit)? = null

    @SuppressLint("MissingPermission")
    override fun startRecording(
        onRmsChanged: (Float) -> Unit,
        onAudioComplete: (FloatArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (isRecording.get()) {
            return true
        }

        this.onAudioCompleteCallback = onAudioComplete
        this.onRmsCallback = onRmsChanged

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError("AudioRecord buffer size calculation failed")
            return false
        }

        val bufferSizeInBytes = (minBufferSize * 2).coerceAtLeast(sampleRate * 2) // at least 1 second buffer

        try {
            val record = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInBytes
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onError("Failed to initialize AudioRecord (state != STATE_INITIALIZED)")
                return false
            }

            synchronized(bufferLock) {
                recordedChunks.clear()
                totalSamplesRecorded = 0
                vad.reset()
            }

            record.startRecording()
            audioRecord = record
            isRecording.set(true)

            val chunkSize = 1024 // 64ms at 16kHz
            val shortBuffer = ShortArray(chunkSize)

            recordingJob = coroutineScope.launch {
                while (isActive && isRecording.get()) {
                    val readResult = record.read(shortBuffer, 0, chunkSize)
                    if (readResult > 0) {
                        val floatChunk = vad.convertShortsToFloats(shortBuffer, 0, readResult)
                        synchronized(bufferLock) {
                            recordedChunks.add(floatChunk)
                            totalSamplesRecorded += readResult
                        }

                        val vadResult = vad.processChunk(floatChunk)
                        onRmsCallback?.invoke(vadResult.rmsDb)

                        if (vadResult.shouldStop) {
                            finishAndEmit()
                            break
                        }
                    } else if (readResult < 0) {
                        onError("AudioRecord read error: $readResult")
                        stopRecording()
                        break
                    }
                }
            }

            return true
        } catch (e: SecurityException) {
            onError("Missing RECORD_AUDIO permission: ${e.message}")
            return false
        } catch (e: Exception) {
            onError("Failed to start AudioRecord: ${e.message}")
            return false
        }
    }

    override fun stopRecording() {
        if (!isRecording.getAndSet(false)) {
            return
        }
        finishAndEmit()
    }

    private fun finishAndEmit() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (_: Exception) {}

        val fullAudio: FloatArray
        synchronized(bufferLock) {
            fullAudio = FloatArray(totalSamplesRecorded)
            var offset = 0
            for (chunk in recordedChunks) {
                System.arraycopy(chunk, 0, fullAudio, offset, chunk.size)
                offset += chunk.size
            }
            recordedChunks.clear()
            totalSamplesRecorded = 0
        }

        onAudioCompleteCallback?.invoke(fullAudio)
    }

    override fun release() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        synchronized(bufferLock) {
            recordedChunks.clear()
            totalSamplesRecorded = 0
            vad.reset()
        }
        onAudioCompleteCallback = null
        onRmsCallback = null
    }
}
