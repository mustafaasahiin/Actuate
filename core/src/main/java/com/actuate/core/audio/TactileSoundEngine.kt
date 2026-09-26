package com.actuate.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Actuate's sound design, synthesized at runtime.
 *
 * Every cue is generated as raw PCM the first time it is needed — there is not a
 * single .mp3/.wav asset in the APK, nothing is downloaded, and synthesis works
 * in airplane mode. The whole palette is ~4 KB of float math.
 *
 * Two hard rules are enforced here:
 *  1. The engine never blocks a caller. Playback is handed to a single-thread
 *     executor and the UI thread returns immediately.
 *  2. The engine never throws into the UI. Audio is a garnish; a device with no
 *     audio output, a muted ringer, or a dead [AudioTrack] must all degrade to
 *     silence, not to a crash on the record button.
 */
class TactileSoundEngine(
    context: Context,
    private val enabledProvider: () -> Boolean = { true },
) {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "actuate-sfx").apply { isDaemon = true }
    }

    private val voices = LinkedHashMap<Cue, Voice>()
    private val voiceLock = Any()

    @Volatile
    private var released = false

    /** `true` while the ringer is in silent or vibrate mode. */
    private fun isSilenced(): Boolean = when (audioManager?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> true
        else -> false
    }

    private fun play(cue: Cue) {
        if (released || !enabledProvider() || isSilenced()) return
        executor.execute {
            if (released) return@execute
            runCatching {
                val voice = synchronized(voiceLock) {
                    voices.getOrPut(cue) { Voice(cue, SAMPLE_RATE) }
                }
                voice.replay()
            }
        }
    }

    /** 1.5ms mechanical transient when recording starts or stops. */
    fun playMicClick() = play(Cue.MIC_CLICK)

    /** 30ms ascending harmonic sweep when a batch of actions is committed. */
    fun playActionActuated() = play(Cue.ACTION_ACTUATED)

    /** 5ms micro-click on a checkbox, destination chip or segmented pill. */
    fun playToggleSnap() = play(Cue.TOGGLE_SNAP)

    /** 45ms low buzz on quota, network or transcription failure. */
    fun playErrorBuzz() = play(Cue.ERROR_BUZZ)

    /** Releases every [AudioTrack]. Safe to call more than once. */
    fun release() {
        released = true
        executor.execute {
            synchronized(voiceLock) {
                voices.values.forEach { it.release() }
                voices.clear()
            }
            executor.shutdown()
        }
    }

    // ------------------------------------------------------------------ cues

    enum class Cue(val durationMs: Double, internal val tag: String) {
        MIC_CLICK(1.5, "mic_click"),
        ACTION_ACTUATED(30.0, "action_actuated"),
        TOGGLE_SNAP(5.0, "toggle_snap"),
        ERROR_BUZZ(45.0, "error_buzz"),
    }

    private class Voice(cue: Cue, sampleRate: Int) {
        private val pcm: ShortArray = synthesize(cue, sampleRate)
        private val track: AudioTrack? = runCatching {
            val minBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(pcm.size * 2)

            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .also { track ->
                    track.write(pcm, 0, pcm.size)
                }
        }.getOrNull()

        fun replay() {
            val track = track ?: return
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            runCatching {
                track.stop()
                // MODE_STATIC buffers must be rewound before replaying.
                track.reloadStaticData()
                track.play()
            }
        }

        fun release() {
            runCatching {
                track?.pause()
                track?.flush()
                track?.release()
            }
        }
    }

    companion object {
        private const val TAG = "TactileSound"

        /** 44.1 kHz mono PCM-16 — the lowest common denominator across Android. */
        const val SAMPLE_RATE = 44_100

        /** Exact sample count for a duration, rounded to the nearest whole sample. */
        fun samplesFor(durationMs: Double, sampleRate: Int = SAMPLE_RATE): Int =
            (durationMs * sampleRate / 1000.0).roundToInt()

        /** PCM for the record-start/stop transient: a 2400 Hz damped pulse. */
        fun generateMicClickPcm(sampleRate: Int = SAMPLE_RATE): ShortArray =
            synthesize(Cue.MIC_CLICK, sampleRate)

        /** PCM for the commit sound: 880 Hz -> 1760 Hz dual-sine sweep. */
        fun generateActionActuatedPcm(sampleRate: Int = SAMPLE_RATE): ShortArray =
            synthesize(Cue.ACTION_ACTUATED, sampleRate)

        /** PCM for the toggle click: a 1200 Hz 5ms transient. */
        fun generateToggleSnapPcm(sampleRate: Int = SAMPLE_RATE): ShortArray =
            synthesize(Cue.TOGGLE_SNAP, sampleRate)

        /** PCM for the failure buzz: a 160 Hz gated square wave. */
        fun generateErrorBuzzPcm(sampleRate: Int = SAMPLE_RATE): ShortArray =
            synthesize(Cue.ERROR_BUZZ, sampleRate)

        internal fun synthesize(cue: Cue, sampleRate: Int): ShortArray {
            val count = samplesFor(cue.durationMs, sampleRate)
            if (count <= 0) return ShortArray(0)
            return when (cue) {
                // Decay is tuned so the pulse is still ~40% of peak at its first crest —
                // faster than this and the 1.5ms cue reads as silence on phone speakers.
                Cue.MIC_CLICK -> dampedTone(2400.0, count, sampleRate, decay = 12.0, gain = 0.30)
                Cue.TOGGLE_SNAP -> dampedTone(1200.0, count, sampleRate, decay = 18.0, gain = 0.22)
                Cue.ACTION_ACTUATED -> harmonicSweep(
                    fromHz = 880.0,
                    toHz = 1760.0,
                    count = count,
                    sampleRate = sampleRate,
                    gain = 0.26,
                )

                Cue.ERROR_BUZZ -> squareBuzz(160.0, count, sampleRate, gain = 0.22)
            }
        }

        /** A sine at [freqHz] with an exponential decay envelope — a "tick". */
        private fun dampedTone(
            freqHz: Double,
            count: Int,
            sampleRate: Int,
            decay: Double,
            gain: Double,
        ): ShortArray {
            val out = ShortArray(count)
            val omega = 2.0 * PI * freqHz / sampleRate
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val envelope = exp(-decay * t)
                out[i] = toPcm(sin(omega * i) * envelope * gain)
            }
            return out
        }

        /**
         * An exponential frequency sweep that also stacks the octave above, which is
         * what gives the commit cue its "confirmation" character rather than a beep.
         */
        private fun harmonicSweep(
            fromHz: Double,
            toHz: Double,
            count: Int,
            sampleRate: Int,
            gain: Double,
        ): ShortArray {
            val out = ShortArray(count)
            val ratio = toHz / fromHz
            var phase = 0.0
            var phaseOctave = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val freq = fromHz * Math.pow(ratio, t)
                phase += 2.0 * PI * freq / sampleRate
                phaseOctave += 2.0 * PI * (freq * 2.0) / sampleRate
                // Fast attack, smooth release: no click at either end.
                val envelope = attackRelease(t)
                val sample = sin(phase) * 0.7 + sin(phaseOctave) * 0.3
                out[i] = toPcm(sample * envelope * gain)
            }
            return out
        }

        /** A gated 160 Hz square wave; the symmetric gate keeps it audible but not harsh. */
        private fun squareBuzz(
            freqHz: Double,
            count: Int,
            sampleRate: Int,
            gain: Double,
        ): ShortArray {
            val out = ShortArray(count)
            val period = sampleRate / freqHz
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val high = (i % period) < (period / 2.0)
                // Two pulses across the cue makes it read as "rejected", not as a tone.
                val gate = if (t < 0.45 || (t > 0.55 && t < 0.95)) 1.0 else 0.0
                out[i] = toPcm((if (high) 1.0 else -1.0) * gate * attackRelease(t) * gain)
            }
            return out
        }

        /** 10% attack, 30% release ramp — kills the DC step at both ends. */
        private fun attackRelease(t: Double): Double {
            val attack = 0.10
            val releaseStart = 0.70
            return when {
                t < attack -> t / attack
                t > releaseStart -> (1.0 - t) / (1.0 - releaseStart)
                else -> 1.0
            }
        }

        /** Clamps a -1..1 float to int16 so a hot mix can never wrap into noise. */
        private fun toPcm(value: Double): Short =
            (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort()
    }
}
