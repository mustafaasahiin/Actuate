package com.actuate.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radii from design.md: 8dp containers, 980dp capsules. Nothing else.

val ActuateShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

val Capsule = RoundedCornerShape(percent = 50)

// Spacing scale (4dp grid): 4, 8, 12, 16, 20, 24, 40, 48
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 40.dp
    val xxxl = 48.dp
}