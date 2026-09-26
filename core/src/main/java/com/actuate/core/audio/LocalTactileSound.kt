package com.actuate.core.audio

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ambient sound engine for the whole composition.
 *
 * `:core` must not know about DI frameworks, so instead of reaching for a Koin
 * singleton the app provides the engine once at the root and every component in
 * this module picks it up by default:
 *
 * ```
 * CompositionLocalProvider(LocalTactileSound provides engine) { ActuateApp() }
 * ```
 *
 * `null` is a valid value and means "silent" — useful in previews, screenshot
 * tests and any host that has not wired audio yet. Call sites that pass an
 * explicit engine still win, so a screen can opt into a different sound without
 * touching the tree.
 */
val LocalTactileSound = staticCompositionLocalOf<TactileSoundEngine?> { null }
