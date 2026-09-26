package com.actuate.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.actuate.app.R
import com.actuate.core.components.ActuateCard
import com.actuate.core.components.ActuateIconButton
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.components.ActuateTextField
import com.actuate.core.components.ButtonSize
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.LinkBlue
import com.actuate.core.theme.Spacing

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSwitchToSignup: () -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    existingEmail: String = "",
    showBackButton: Boolean = true,
) {
    val haptics = rememberCupertinoHaptics()
    var email by remember { mutableStateOf(existingEmail) }
    var password by remember { mutableStateOf("") }

    val emailValid = isValidEmail(email)
    val passwordValid = password.length >= 8
    val canSubmit = emailValid && password.isNotBlank() && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xl),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                ActuateIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = onBack,
                    contentDescription = "Back",
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        ActuateCard(modifier = Modifier.fillMaxWidth()) {
            ActuateTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email_label),
                supportingText = {
                    if (email.isNotBlank() && !emailValid) {
                        Text(
                            text = stringResource(R.string.email_invalid_error),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                isError = email.isNotBlank() && !emailValid,
                singleLine = true,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))

            ActuateTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password_label),
                isPassword = true,
                supportingText = {
                    if (password.isNotBlank() && !passwordValid) {
                        Text(
                            text = stringResource(R.string.password_criteria_error),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                isError = password.isNotBlank() && !passwordValid,
                singleLine = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = { if (canSubmit) onLogin(email.trim(), password) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            ActuatePrimaryButton(
                text = stringResource(R.string.login_button),
                onClick = { onLogin(email.trim(), password) },
                enabled = canSubmit,
                isLoading = isLoading,
                size = ButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Text(
            text = stringResource(R.string.account_benefit_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )

        Spacer(Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.new_here),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    haptics.impactLight()
                    onSwitchToSignup()
                },
                shape = Capsule,
                colors = ButtonDefaults.textButtonColors(contentColor = LinkBlue),
            ) {
                Text(
                    text = stringResource(R.string.create_account_button),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
