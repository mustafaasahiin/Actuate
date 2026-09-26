package com.actuate.core.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.actuate.core.theme.Capsule

/**
 * Text input per design.md: Frost fill, 1dp Mist border, 8dp corners,
 * Apple Blue focus ring, no shadows.
 */
@Composable
fun ActuateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val containerBg = if (isDark) DeepGraphite else Frost
    val focusedContainerBg = if (isDark) DeepGraphite else Color.White
    val borderClr = if (isDark) MistDark else Mist
    val labelClr = if (isDark) AshDark else Ash

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = focusedContainerBg,
            unfocusedContainerColor = containerBg,
            disabledContainerColor = containerBg,
            errorContainerColor = containerBg,
            focusedBorderColor = AppleBlue,
            unfocusedBorderColor = borderClr,
            disabledBorderColor = borderClr,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = AppleBlue,
            unfocusedLabelColor = labelClr,
            errorLabelColor = MaterialTheme.colorScheme.error,
            cursorColor = AppleBlue,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            errorTextColor = MaterialTheme.colorScheme.onSurface,
            errorSupportingTextColor = MaterialTheme.colorScheme.error,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun SpotlightSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    onSearch: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val containerBg = if (isDark) DeepGraphite else Frost
    val borderClr = if (isDark) MistDark else Mist.copy(alpha = 0.35f)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) AshDark else Ash,
            )
        },
        leadingIcon = leadingIcon ?: {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = if (isDark) AshDark else Ash,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear",
                        tint = if (isDark) AshDark else Ash,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else if (trailingContent != null) {
                trailingContent()
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        shape = Capsule,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isDark) DeepGraphite else Color.White,
            unfocusedContainerColor = containerBg,
            focusedBorderColor = AppleBlue,
            unfocusedBorderColor = borderClr,
            cursorColor = AppleBlue,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}