package com.actuate.core.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.haptics.rememberTactileFeedback
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElectricCobalt
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.Ice
import com.actuate.core.theme.LinkBlue
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.Pebble
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.Spacing

/**
 * Standard sizing tiers for Actuate buttons.
 */
enum class ButtonSize(val height: Dp, val horizontalPadding: Dp) {
    LARGE(50.dp, 24.dp),
    MEDIUM(40.dp, 16.dp),
    SMALL(32.dp, 12.dp),
}

/**
 * Tier 1: Primary Action Button.
 * Solid Apple Blue fill, white text, built-in loading spinner,
 * 0.965f spring press physics, and light Cupertino haptics.
 */
@Composable
fun ActuatePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.LARGE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberCupertinoHaptics()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "primaryButtonPressScale",
    )
    val isDark = isSystemInDarkTheme()

    val textStyle = when (size) {
        ButtonSize.LARGE -> MaterialTheme.typography.labelLarge
        ButtonSize.MEDIUM -> MaterialTheme.typography.labelMedium
        ButtonSize.SMALL -> MaterialTheme.typography.labelSmall
    }

    val spinnerSize = when (size) {
        ButtonSize.LARGE -> 18.dp
        ButtonSize.MEDIUM -> 16.dp
        ButtonSize.SMALL -> 14.dp
    }

    Button(
        onClick = {
            if (enabled && !isLoading) {
                haptics.impactLight()
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = Capsule,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppleBlue,
            contentColor = Color.White,
            disabledContainerColor = if (isLoading) AppleBlue else (if (isDark) DeepGraphite else Pebble),
            disabledContentColor = if (isLoading) Color.White else Ash,
        ),
        contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
        interactionSource = interactionSource,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            } else if (leadingIcon != null) {
                leadingIcon()
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            }

            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = textStyle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            if (!isLoading && trailingIcon != null) {
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                trailingIcon()
            }
        }
    }
}

/**
 * Tier 2: Tonal Button.
 * Subtle elevated surface (Ice in light, ElevatedDark in dark) with 1dp hairline border
 * for chips, filters, and secondary actions. Features 0.965f spring press physics and light haptics.
 */
@Composable
fun ActuateTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.MEDIUM,
    contentColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberCupertinoHaptics()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tonalButtonPressScale",
    )
    val isDark = isSystemInDarkTheme()
    val containerBg = if (isDark) ElevatedDark else Ice
    val borderClr = if (isDark) MistDark else Mist.copy(alpha = 0.35f)
    val resolvedContentColor = contentColor ?: (if (isDark) Color.White else Carbon)

    val textStyle = when (size) {
        ButtonSize.LARGE -> MaterialTheme.typography.labelLarge
        ButtonSize.MEDIUM -> MaterialTheme.typography.labelMedium
        ButtonSize.SMALL -> MaterialTheme.typography.labelSmall
    }

    val spinnerSize = when (size) {
        ButtonSize.LARGE -> 18.dp
        ButtonSize.MEDIUM -> 16.dp
        ButtonSize.SMALL -> 14.dp
    }

    Button(
        onClick = {
            if (enabled && !isLoading) {
                haptics.impactLight()
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = Capsule,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerBg,
            contentColor = resolvedContentColor,
            disabledContainerColor = if (isLoading) containerBg else containerBg.copy(alpha = 0.5f),
            disabledContentColor = if (isLoading) resolvedContentColor else Ash,
        ),
        border = BorderStroke(1.dp, if (enabled || isLoading) borderClr else borderClr.copy(alpha = 0.35f)),
        contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
        interactionSource = interactionSource,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    strokeWidth = 2.dp,
                    color = resolvedContentColor,
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            } else if (leadingIcon != null) {
                leadingIcon()
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            }

            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = textStyle,
                    color = resolvedContentColor,
                    textAlign = TextAlign.Center,
                )
            }

            if (!isLoading && trailingIcon != null) {
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                trailingIcon()
            }
        }
    }
}

/**
 * Tier 3: Outlined Button.
 * 1dp outline with SignalBlue in dark mode for WCAG AA compliance (LinkBlue in light mode).
 * Features 0.965f spring press physics and light haptics.
 */
@Composable
fun ActuateOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.LARGE,
    contentColor: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberCupertinoHaptics()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "outlinedButtonPressScale",
    )
    val isDark = isSystemInDarkTheme()
    val defaultBlue = if (isDark) SignalBlue else LinkBlue
    val resolvedColor = contentColor ?: defaultBlue

    val textStyle = when (size) {
        ButtonSize.LARGE -> MaterialTheme.typography.labelLarge
        ButtonSize.MEDIUM -> MaterialTheme.typography.labelMedium
        ButtonSize.SMALL -> MaterialTheme.typography.labelSmall
    }

    val spinnerSize = when (size) {
        ButtonSize.LARGE -> 18.dp
        ButtonSize.MEDIUM -> 16.dp
        ButtonSize.SMALL -> 14.dp
    }

    OutlinedButton(
        onClick = {
            if (enabled && !isLoading) {
                haptics.impactLight()
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = Capsule,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = resolvedColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = if (isLoading) resolvedColor else Ash,
        ),
        border = BorderStroke(1.dp, if (enabled || isLoading) resolvedColor else resolvedColor.copy(alpha = 0.38f)),
        contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
        interactionSource = interactionSource,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    strokeWidth = 2.dp,
                    color = resolvedColor,
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            } else if (leadingIcon != null) {
                leadingIcon()
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            }

            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = textStyle,
                    color = resolvedColor,
                    textAlign = TextAlign.Center,
                )
            }

            if (!isLoading && trailingIcon != null) {
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                trailingIcon()
            }
        }
    }
}

/**
 * Tier 4: Destructive Button.
 * Tonal soft red background with warning haptics for account reset, history deletion,
 * and dangerous actions. Features 0.965f spring press physics.
 */
@Composable
fun ActuateDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.LARGE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberCupertinoHaptics()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "destructiveButtonPressScale",
    )
    val isDark = isSystemInDarkTheme()
    val redColor = if (isDark) Color(0xFFFF453A) else Color(0xFFD70015)
    val containerColor = if (isDark) redColor.copy(alpha = 0.22f) else redColor.copy(alpha = 0.10f)
    val borderColor = redColor.copy(alpha = if (isDark) 0.35f else 0.20f)

    val textStyle = when (size) {
        ButtonSize.LARGE -> MaterialTheme.typography.labelLarge
        ButtonSize.MEDIUM -> MaterialTheme.typography.labelMedium
        ButtonSize.SMALL -> MaterialTheme.typography.labelSmall
    }

    val spinnerSize = when (size) {
        ButtonSize.LARGE -> 18.dp
        ButtonSize.MEDIUM -> 16.dp
        ButtonSize.SMALL -> 14.dp
    }

    Button(
        onClick = {
            if (enabled && !isLoading) {
                haptics.impactWarning()
                onClick()
            }
        },
        enabled = enabled && !isLoading,
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = Capsule,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = redColor,
            disabledContainerColor = if (isLoading) containerColor else containerColor.copy(alpha = 0.5f),
            disabledContentColor = if (isLoading) redColor else Ash,
        ),
        border = BorderStroke(1.dp, if (enabled || isLoading) borderColor else borderColor.copy(alpha = 0.35f)),
        contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
        interactionSource = interactionSource,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    strokeWidth = 2.dp,
                    color = redColor,
                )
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            } else if (leadingIcon != null) {
                leadingIcon()
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
            }

            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = textStyle,
                    color = redColor,
                    textAlign = TextAlign.Center,
                )
            }

            if (!isLoading && trailingIcon != null) {
                if (text.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                trailingIcon()
            }
        }
    }
}

/**
 * Tier 5: Icon Button.
 * Circular touch target (default 40dp) with 0.965f spring scale physics and light haptics
 * for navigation back, close, and send buttons.
 */
@Composable
fun ActuateIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptics = rememberCupertinoHaptics()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconButtonPressScale",
    )

    IconButton(
        onClick = {
            if (enabled) {
                haptics.impactLight()
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier),
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = Ash,
        ),
    ) {
        content()
    }
}

/**
 * Convenience overload for ActuateIconButton with an ImageVector icon.
 */
@Composable
fun ActuateIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    ActuateIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        containerColor = containerColor,
        contentColor = contentColor,
        border = border,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

// -------------------------------------------------------------------------
// Luminous cyber CTA
// -------------------------------------------------------------------------

/**
 * The commit button: a luminous cobalt-to-cyan capsule with a cyan rim glow.
 *
 * Reserved for the one irreversible-in-the-moment action on a screen —
 * "ACTUATE 3 ACTIONS", "Unlock Pro", "Start listening". Several on one screen
 * and the hierarchy collapses, so do not.
 *
 * Press physics are a 0.97 spring (heavier than the 0.965 used elsewhere) because
 * the capsule is large and needs to feel weighty, and the haptic is the [snap]
 * pattern rather than a light tap so it registers as a commit, not a selection.
 *
 * @param sound optional engine; when supplied the commit sound fires with the tap.
 */
@Composable
fun CyberCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.LARGE,
    sound: com.actuate.core.audio.TactileSoundEngine? = null,
    haptics: com.actuate.core.haptics.TactileFeedbackController? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val tactile = haptics ?: rememberTactileFeedback()
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cyberCapsulePressScale",
    )

    val textStyle = when (size) {
        ButtonSize.LARGE -> MaterialTheme.typography.labelLarge
        ButtonSize.MEDIUM -> MaterialTheme.typography.labelMedium
        ButtonSize.SMALL -> MaterialTheme.typography.labelSmall
    }
    val spinnerSize = when (size) {
        ButtonSize.LARGE -> 18.dp
        ButtonSize.MEDIUM -> 16.dp
        ButtonSize.SMALL -> 14.dp
    }
    val gradient = if (enabled || isLoading) {
        Brush.horizontalGradient(listOf(ElectricCobalt, HyperCyan))
    } else {
        Brush.horizontalGradient(listOf(Ash.copy(alpha = 0.35f), Ash.copy(alpha = 0.25f)))
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(Capsule)
            .background(gradient)
            .border(
                BorderStroke(1.dp, if (enabled) HyperCyan.copy(alpha = 0.55f) else Color.Transparent),
                Capsule,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
            ) {
                tactile.snap()
                sound?.playActionActuated()
                onClick()
            }
            .padding(horizontal = size.horizontalPadding, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(spinnerSize),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                if (text.isNotEmpty()) Spacer(Modifier.width(Spacing.xs))
            } else if (leadingIcon != null) {
                leadingIcon()
                if (text.isNotEmpty()) Spacer(Modifier.width(Spacing.xs))
            }

            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = textStyle.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            if (!isLoading && trailingIcon != null) {
                if (text.isNotEmpty()) Spacer(Modifier.width(Spacing.xs))
                trailingIcon()
            }
        }
    }
}

// -------------------------------------------------------------------------
// Legacy Button Aliases (Kept for backwards compatibility during migration)
// -------------------------------------------------------------------------

/** Filled capsule button — the only place Apple Blue appears as a fill. */
@Deprecated(
    message = "Use ActuatePrimaryButton instead for 5-tier button hierarchy support.",
    replaceWith = ReplaceWith("ActuatePrimaryButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)"),
)
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ActuatePrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = ButtonSize.LARGE,
    )
}

/** Outlined capsule button — Link Blue border in light mode, Signal Blue in dark mode. */
@Deprecated(
    message = "Use ActuateOutlinedButton instead for 5-tier button hierarchy support.",
    replaceWith = ReplaceWith("ActuateOutlinedButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)"),
)
@Composable
fun PillOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ActuateOutlinedButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = ButtonSize.LARGE,
    )
}

/** Plain text button in Link Blue — used for inline actions in lists. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberCupertinoHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "textLinkPressScale",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                haptics.impactLight()
                onClick()
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) LinkBlue else Ash,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
