package com.actuate.core.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Mist

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
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Frost,
            disabledContainerColor = Frost,
            focusedBorderColor = AppleBlue,
            unfocusedBorderColor = Mist,
            disabledBorderColor = Mist,
            focusedLabelColor = AppleBlue,
            unfocusedLabelColor = Mist,
            cursorColor = AppleBlue,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}