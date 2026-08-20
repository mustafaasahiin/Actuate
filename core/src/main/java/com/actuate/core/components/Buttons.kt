package com.actuate.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.Ice
import com.actuate.core.theme.LinkBlue

/** Filled capsule button — the only place Apple Blue appears as a fill. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 50.dp),
        shape = Capsule,
        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue, contentColor = Ice),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

/** Outlined capsule button — Link Blue border, transparent fill. */
@Composable
fun PillOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 50.dp),
        shape = Capsule,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBlue),
        border = BorderStroke(1.dp, LinkBlue),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

/** Plain text button in Link Blue — used for inline actions in lists. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onClick()
            }
            .padding(8.dp),
    ) {
        Text(
            text = text,
            color = LinkBlue,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}