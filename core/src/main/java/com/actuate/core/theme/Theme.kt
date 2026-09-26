package com.actuate.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * High-contrast technical light mode: alabaster canvas, ink type, cobalt energy.
 * Light mode is a first-class citizen, not an afterthought — it is what the app
 * looks like in a bright room or a projector-lit demo hall.
 */
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = ElectricCobalt,
    onPrimary = OnPrimary,
    primaryContainer = Ice,
    onPrimaryContainer = InkPrimary,
    secondary = NeonSky,
    onSecondary = InkPrimary,
    secondaryContainer = Ice,
    onSecondaryContainer = InkPrimary,
    tertiary = HyperCyan,
    onTertiary = InkPrimary,
    background = TechnicalAlabaster,
    onBackground = InkPrimary,
    surface = TechPanelLight,
    surfaceContainer = TechnicalAlabaster,
    surfaceContainerLow = TechPanelLight,
    surfaceContainerHigh = Ice,
    onSurface = InkPrimary,
    surfaceVariant = Ice,
    onSurfaceVariant = InkSecondary,
    outline = Mist,
    outlineVariant = BorderSubtleLight,
    error = DestructiveRed,
    onError = Ice,
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = ElectricCobalt,
    onPrimary = Color.White,
    primaryContainer = SurfaceElevatedDark,
    onPrimaryContainer = Color.White,
    secondary = HyperCyan,
    onSecondary = CyberBackgroundDark,
    secondaryContainer = SurfaceElevatedDark,
    onSecondaryContainer = Color.White,
    tertiary = NeonSky,
    onTertiary = CyberBackgroundDark,
    background = CyberBackgroundDark,
    onBackground = Color.White,
    surface = SurfacePanelDark,
    surfaceContainer = SurfacePanelDark,
    surfaceContainerLow = CyberBackgroundDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = AshDark,
    outline = MistDark,
    outlineVariant = BorderSubtleDark,
    error = DestructiveRedDark,
    onError = Color.White,
)

val ActuateColorScheme: ColorScheme = LightColorScheme

val ColorScheme.buttonPrimaryBackground: Color
    get() = primary

val ColorScheme.buttonPrimaryText: Color
    get() = onPrimary

val ColorScheme.buttonSecondaryBackground: Color
    get() = if (this == DarkColorScheme || background == Onyx) SurfaceElevatedDark else Ice

val ColorScheme.buttonSecondaryBorder: Color
    get() = secondary

val ColorScheme.buttonDestructiveBackground: Color
    get() = if (this == DarkColorScheme || background == Onyx) DestructiveSoftDark else DestructiveSoftLight

val buttonPrimaryBackground: Color
    @Composable
    get() = MaterialTheme.colorScheme.buttonPrimaryBackground

val buttonPrimaryText: Color
    @Composable
    get() = MaterialTheme.colorScheme.buttonPrimaryText

val buttonSecondaryBackground: Color
    @Composable
    get() = MaterialTheme.colorScheme.buttonSecondaryBackground

val buttonSecondaryBorder: Color
    @Composable
    get() = MaterialTheme.colorScheme.buttonSecondaryBorder

val buttonDestructiveBackground: Color
    @Composable
    get() = MaterialTheme.colorScheme.buttonDestructiveBackground


@Composable
fun ActuateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ActuateTypography,
        shapes = ActuateShapes,
        content = content,
    )
}
