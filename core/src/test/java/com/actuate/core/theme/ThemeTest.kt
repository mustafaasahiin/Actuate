package com.actuate.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun cyberColorTokensMatchDesignSpecification() {
        // Pitch void canvas and panels.
        assertEquals(Color(0xFF07090E), CyberBackgroundDark)
        assertEquals(Color(0xFF0E131F), SurfacePanelDark)
        assertEquals(Color(0xFF161D2E), SurfaceElevatedDark)

        // Electric accents.
        assertEquals(Color(0xFF2563EB), ElectricCobalt)
        assertEquals(Color(0xFF00D2DF), HyperCyan)
        assertEquals(Color(0xFF06B6D4), PulseTeal)
        assertEquals(Color(0xFF38BDF8), NeonSky)

        // Destination roles.
        assertEquals(Color(0xFFFF5252), CalendarCoral)
        assertEquals(Color(0xFF8B5CF6), NotionPrismatic)
        assertEquals(Color(0xFFFFB300), AlarmAmber)

        // Glass borders.
        assertEquals(Color(0x3300D2DF), BorderCyanGlow)
        assertEquals(Color(0xFF1F293D), BorderSubtleDark)

        // Technical light mode.
        assertEquals(Color(0xFFF8FAFC), TechnicalAlabaster)
        assertEquals(Color(0xFFFFFFFF), TechPanelLight)
        assertEquals(Color(0xFF0A0F1D), InkPrimary)
    }

    @Test
    fun legacyTokensAreAliasesSoCallSitesCannotDriftBackToCupertinoBlue() {
        assertEquals(ElectricCobalt, AppleBlue)
        assertEquals(ElectricCobalt, CobaltPrimary)
        assertEquals(ElectricCobalt, CobaltVivid)
        assertEquals(HyperCyan, ElectricCyan)
        assertEquals(NeonSky, SignalBlue)
        assertEquals(CyberBackgroundDark, Onyx)
        assertEquals(CyberBackgroundDark, CanvasDark)
        assertEquals(SurfacePanelDark, DeepGraphite)
        assertEquals(SurfaceElevatedDark, ElevatedDark)
        assertEquals(TechnicalAlabaster, Frost)

        // Tokens the overhaul intentionally left alone.
        assertEquals(Color(0xFFD70015), DestructiveRed)
        assertEquals(Color(0xFFFF453A), DestructiveRedDark)
        assertEquals(Color(0x1AD70015), DestructiveSoftLight)
        assertEquals(Color(0x33FF453A), DestructiveSoftDark)
    }

    @Test
    fun darkColorSchemeProvidesPitchVoidCanvasAndPanelSurfaces() {
        assertEquals(CyberBackgroundDark, DarkColorScheme.background)
        assertEquals(Color.White, DarkColorScheme.onBackground)
        assertEquals(SurfacePanelDark, DarkColorScheme.surface)
        assertEquals(Color.White, DarkColorScheme.onSurface)
        assertEquals(CyberBackgroundDark, DarkColorScheme.surfaceContainerLow)
        assertEquals(SurfaceElevatedDark, DarkColorScheme.surfaceVariant)
        assertEquals(AshDark, DarkColorScheme.onSurfaceVariant)
        assertEquals(BorderSubtleDark, DarkColorScheme.outlineVariant)
        assertEquals(ElectricCobalt, DarkColorScheme.primary)
        assertEquals(Color.White, DarkColorScheme.onPrimary)
        assertEquals(HyperCyan, DarkColorScheme.secondary)
        assertEquals(NeonSky, DarkColorScheme.tertiary)
    }

    @Test
    fun lightColorSchemeProvidesAlabasterCanvasAndInkType() {
        assertEquals(TechnicalAlabaster, LightColorScheme.background)
        assertEquals(InkPrimary, LightColorScheme.onBackground)
        assertEquals(TechPanelLight, LightColorScheme.surface)
        assertEquals(InkPrimary, LightColorScheme.onSurface)
        assertEquals(InkSecondary, LightColorScheme.onSurfaceVariant)
        assertEquals(BorderSubtleLight, LightColorScheme.outlineVariant)
        assertEquals(ElectricCobalt, LightColorScheme.primary)
        assertEquals(NeonSky, LightColorScheme.secondary)
        assertEquals(HyperCyan, LightColorScheme.tertiary)
    }

    @Test
    fun semanticButtonTokensProvideCalibratedTokensForLightAndDark() {
        assertEquals(ElectricCobalt, LightColorScheme.buttonPrimaryBackground)
        assertEquals(OnPrimary, LightColorScheme.buttonPrimaryText)
        assertEquals(Ice, LightColorScheme.buttonSecondaryBackground)
        assertEquals(NeonSky, LightColorScheme.buttonSecondaryBorder)
        assertEquals(DestructiveSoftLight, LightColorScheme.buttonDestructiveBackground)

        assertEquals(ElectricCobalt, DarkColorScheme.buttonPrimaryBackground)
        assertEquals(Color.White, DarkColorScheme.buttonPrimaryText)
        assertEquals(SurfaceElevatedDark, DarkColorScheme.buttonSecondaryBackground)
        assertEquals(HyperCyan, DarkColorScheme.buttonSecondaryBorder)
        assertEquals(DestructiveSoftDark, DarkColorScheme.buttonDestructiveBackground)
    }

    @Test
    fun darkAndLightColorSchemesAreDistinct() {
        assertNotEquals(DarkColorScheme.background, LightColorScheme.background)
        assertNotEquals(DarkColorScheme.surface, LightColorScheme.surface)
        assertNotEquals(DarkColorScheme.onSurface, LightColorScheme.onSurface)
    }

    @Test
    fun telemetryTypographyUsesMonospaceWithTrackedLabels() {
        assertEquals(TelemetryFontFamily, TelemetrySmall.fontFamily)
        assertEquals(11.sp, TelemetrySmall.fontSize)
        assertEquals(0.88.sp, TelemetrySmall.letterSpacing)

        assertEquals(TelemetryFontFamily, TelemetryMedium.fontFamily)
        assertEquals(13.sp, TelemetryMedium.fontSize)
        assertEquals(1.04.sp, TelemetryMedium.letterSpacing)

        // Display weights are tightened so large type reads as designed, not default.
        assertEquals((-1.2).sp, ActuateTypography.displayLarge.letterSpacing)
        assertEquals((-1.1).sp, ActuateTypography.displayMedium.letterSpacing)
        assertEquals((-0.9).sp, ActuateTypography.displaySmall.letterSpacing)
    }

    @Test
    fun cyberShapesCarryTheTechnicalMicroCorner() {
        assertEquals(
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomEnd = 12.dp,
                bottomStart = 4.dp,
            ),
            CyberCardShape,
        )
        assertEquals(RoundedCornerShape(4.dp), TelemetryBadgeShape)
        assertNotEquals(CyberCardShape, CyberCardShapeCompact)
    }
}
