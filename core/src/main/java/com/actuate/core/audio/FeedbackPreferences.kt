package com.actuate.core.audio

import android.content.Context

/**
 * User preference for Actuate's own sound and haptics.
 *
 * Read directly from SharedPreferences on the playback path, so the engine's
 * enabled-check is a cheap synchronous lookup with no flow collection, no Koin
 * dependency and no coroutine — a sound cue must never wait on a subscription.
 *
 * Both default to ON: the feedback language is part of the product, and a user who
 * turns it off has made a deliberate choice we remember.
 */
object FeedbackPreferences {

    private const val PREFS_NAME = "actuate_feedback"

    /** Master switch for the procedural sound engine. */
    const val KEY_SOUND_ENABLED = "sound_enabled"

    /** Master switch for [com.actuate.core.haptics.TactileFeedbackController]. */
    const val KEY_HAPTICS_ENABLED = "haptics_enabled"

    fun isSoundEnabled(context: Context): Boolean = read(context, KEY_SOUND_ENABLED)

    fun isHapticsEnabled(context: Context): Boolean = read(context, KEY_HAPTICS_ENABLED)

    fun setSoundEnabled(context: Context, enabled: Boolean) =
        write(context, KEY_SOUND_ENABLED, enabled)

    fun setHapticsEnabled(context: Context, enabled: Boolean) =
        write(context, KEY_HAPTICS_ENABLED, enabled)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): Boolean =
        runCatching { prefs(context).getBoolean(key, true) }.getOrDefault(true)

    private fun write(context: Context, key: String, enabled: Boolean) {
        runCatching { prefs(context).edit().putBoolean(key, enabled).apply() }
    }
}
