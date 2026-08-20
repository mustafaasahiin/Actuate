package com.actuate.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ActuateColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = OnPrimary,
    primaryContainer = Ice,
    onPrimaryContainer = Carbon,
    secondary = LinkBlue,
    onSecondary = Ice,
    secondaryContainer = Pebble,
    onSecondaryContainer = Carbon,
    tertiary = SignalBlue,
    onTertiary = Carbon,
    background = Frost,
    onBackground = Carbon,
    surface = Frost,
    onSurface = Carbon,
    surfaceVariant = Ice,
    onSurfaceVariant = Graphite,
    outline = Mist,
    outlineVariant = Mist,
    error = Color(0xFFD70015),
    onError = Ice,
)

@Composable
fun ActuateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ActuateColorScheme,
        typography = ActuateTypography,
        shapes = ActuateShapes,
        content = content,
    )
}