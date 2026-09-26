package com.actuate.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.actuate.app.R
import com.actuate.app.util.SupportedLanguage
import com.actuate.core.components.ActuateCard
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.components.ButtonSize
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.TextLink
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.Spacing

/**
 * Dedicated full-screen language selection screen for onboarding and top-level navigation.
 * Displays interactive cards for all supported languages, committing user selection
 * on continue or providing a skip option.
 */
@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onContinue: () -> Unit,
    initialLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    var selectedLanguage by rememberSaveable { mutableStateOf(initialLanguage) }

    val infiniteTransition = rememberInfiniteTransition(label = "languageIconTransition")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "languageIconScale",
    )
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "languageIconAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.md))

        // Animated language icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
                .background(if (isDark) ElevatedDark else Ice, CircleShape)
                .border(
                    width = 1.dp,
                    color = if (isDark) MistDark else Mist.copy(alpha = 0.35f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = SignalBlue,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { alpha = iconAlpha },
            )
        }

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = stringResource(R.string.select_language_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = stringResource(R.string.select_language_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xl))

        // Grouped interactive language cards
        CupertinoGroupedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SupportedLanguage.entries.forEach { language ->
                    val isSelected = language == selectedLanguage
                    val rowBorder = if (isSelected) AppleBlue else if (isDark) MistDark else Mist.copy(alpha = 0.35f)
                    val rowBg = if (isSelected) {
                        if (isDark) ElevatedDark else Ice
                    } else {
                        if (isDark) DeepGraphite else Color.White
                    }

                    Surface(
                        onClick = { selectedLanguage = language },
                        shape = RoundedCornerShape(8.dp),
                        color = rowBg,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, rowBorder),
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = language.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) AppleBlue else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = getEnglishName(language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(AppleBlue, CircleShape)
                                        } else {
                                            Modifier.border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isDark) MistDark else Mist.copy(alpha = 0.5f),
                                                ),
                                                CircleShape,
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        ActuatePrimaryButton(
            text = stringResource(R.string.continue_button),
            onClick = {
                onLanguageSelected(selectedLanguage)
                onContinue()
            },
            size = ButtonSize.LARGE,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.xs))

        TextLink(
            text = stringResource(R.string.skip),
            onClick = onContinue,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(Spacing.lg))
    }
}

/**
 * Returns the canonical English name for each supported language.
 */
private fun getEnglishName(language: SupportedLanguage): String = when (language) {
    SupportedLanguage.ENGLISH -> "English"
}
