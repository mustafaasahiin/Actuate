package com.actuate.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.core.components.ActuateCard
import com.actuate.core.components.PillOutlinedButton
import com.actuate.core.components.SectionLabel
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onResetAccount: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Frost)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = Carbon,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            // Subscription
            SectionLabel("Subscription")
            ActuateCard(highlighted = true) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.planName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Carbon,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = if (state.isPro) {
                            "Unlimited actions · multi-app sync"
                        } else {
                            "10 voice actions per week. Upgrade to Pro — \$4.99/mo or \$29.99/yr."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Graphite,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // Server
            SectionLabel("Server")
            ActuateCard(highlighted = true) {
                Text(
                    text = "All parsing and execution runs on the Actuate server — your " +
                        "keys stay server-side, and this app connects automatically. " +
                        "No setup needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = state.serverStatus ?: "Checking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.serverStatus == "Connected" || state.serverStatus?.startsWith("Connected") == true) {
                        Carbon
                    } else {
                        Graphite
                    },
                )
            }
            Spacer(Modifier.height(Spacing.md))

            // Privacy
            SectionLabel("Privacy")
            ActuateCard {
                Text(
                    text = "Transcripts are sent to your Actuate server for parsing; " +
                        "history stays on this device, and the session token is encrypted " +
                        "with the Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite,
                )
                Spacer(Modifier.height(Spacing.sm))
                PillOutlinedButton(
                    text = "Clear transcript history",
                    onClick = viewModel::clearHistory,
                    enabled = !state.clearing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.md))

            // Account
            SectionLabel("Account")
            ActuateCard {
                Text(
                    text = "Resets the device session and registers again automatically. " +
                        "Your account follows this install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite,
                )
                Spacer(Modifier.height(Spacing.sm))
                PillOutlinedButton(
                    text = "Reset account",
                    onClick = onResetAccount,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}