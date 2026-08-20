package com.actuate.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.actuate.core.R

// Type scale from design.md (SF Pro Display/Text, Inter fallback).
// Inter is bundled (OFL) to land closest to SF Pro's look on Android.

@OptIn(ExperimentalTextApi::class)
private val system = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

val ActuateTypography = Typography(
    // display — 56dp, 600, +0.011em
    displayLarge = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = 0.6.sp,
    ),
    // heading — 40dp, 600, +0.011em
    displayMedium = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.44.sp,
    ),
    // heading-sm — 28dp, 600, +0.007em
    headlineLarge = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.2.sp,
    ),
    // subheading — 21dp, 300/600, -0.005em
    headlineMedium = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.Light,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = -0.1.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = -0.1.sp,
    ),
    // body — 17dp, 400, -0.016em
    bodyLarge = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        letterSpacing = -0.27.sp,
    ),
    // body-sm — 14dp, 400, -0.016em
    bodyMedium = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = -0.22.sp,
    ),
    // caption — 12dp, 400, -0.022em
    bodySmall = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = -0.26.sp,
    ),
    // Label style for buttons — 17dp medium
    labelLarge = TextStyle(
        fontFamily = system,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.16.sp,
    ),
)