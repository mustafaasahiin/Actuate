package com.actuate.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AttachEmail
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.app.R
import com.actuate.app.ui.onboarding.LanguageSelectionSheet
import com.actuate.app.ui.paywall.PaywallSheet
import com.actuate.app.util.LocaleManager
import com.actuate.core.components.ActuateDestructiveButton
import com.actuate.core.components.ActuateIconButton
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.components.ActuateTonalButton
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.ButtonSize
import com.actuate.core.components.CellPosition
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.CupertinoGroupedCell
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.CobaltVivid
import com.actuate.core.theme.DangerRose
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElectricCyan
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Ice
import com.actuate.core.theme.LinkBlue
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.NotionIndigo
import com.actuate.core.theme.NotionPrismatic
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.core.theme.SuccessEmerald
import com.actuate.core.theme.VividEmerald
import com.actuate.core.theme.WhatsappGreen
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onResetAccount: () -> Unit,
    onOpenConnections: () -> Unit = {},
    showBackButton: Boolean = true,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val localeManager: LocaleManager = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentLanguage by localeManager.currentLanguage.collectAsStateWithLifecycle()
    val haptics = rememberCupertinoHaptics()
    val isDark = isSystemInDarkTheme()

    var showPaywall by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    if (showPaywall) {
        PaywallSheet(
            onDismiss = { showPaywall = false },
            onUpgradeSuccess = { showPaywall = false },
            onUnlockPro = { viewModel.upgradeToPro("SHIPATON2026") },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = { Text(stringResource(R.string.reset_dialog_desc)) },
            confirmButton = {
                ActuateDestructiveButton(
                    text = stringResource(R.string.reset_button),
                    onClick = {
                        showResetDialog = false
                        onResetAccount()
                    },
                    size = ButtonSize.MEDIUM,
                )
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.clear_history_dialog_title)) },
            text = { Text(stringResource(R.string.clear_history_dialog_desc)) },
            confirmButton = {
                ActuateDestructiveButton(
                    text = stringResource(R.string.clear_button),
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearHistory()
                    },
                    size = ButtonSize.MEDIUM,
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (showBackButton) Spacing.xs else Spacing.md,
                    end = Spacing.md,
                    top = Spacing.xs,
                    bottom = Spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                ActuateIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    onClick = {
                        haptics.selectionChanged()
                        onBack()
                    },
                    contentColor = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            BoldEyebrow(
                text = stringResource(R.string.subscription_title),
                dotColor = SolarAmber,
            )

            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                if (state.isPro) {
                    CupertinoGroupedCell(
                        title = stringResource(R.string.pro_unlimited_title),
                        subtitle = stringResource(R.string.pro_unlimited_desc),
                        icon = Icons.Rounded.Star,
                        iconBackground = SolarAmber,
                        position = CellPosition.SINGLE,
                        trailingContent = {
                            Surface(
                                shape = Capsule,
                                color = SolarAmber.copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, SolarAmber.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = SolarAmber,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "PRO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SolarAmber,
                                    )
                                }
                            }
                        },
                    )
                } else {
                    CupertinoGroupedCell(
                        title = stringResource(R.string.free_plan_title),
                        subtitle = stringResource(R.string.free_plan_desc),
                        icon = Icons.Rounded.Star,
                        iconBackground = Ash,
                        position = CellPosition.SINGLE,
                        trailingContent = {
                            Surface(
                                shape = Capsule,
                                color = if (isDark) ElevatedDark else Ice,
                            ) {
                                Text(
                                    text = "FREE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        },
                    )
                }
            }

            if (!state.isPro) {
                Spacer(Modifier.height(Spacing.sm))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) ElevatedDark else Ice,
                    border = BorderStroke(
                        0.5.dp,
                        if (isDark) SolarAmber.copy(alpha = 0.35f) else SolarAmber.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptics.selectionChanged()
                            showPaywall = true
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = SolarAmber.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = SolarAmber,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.upgrade_pro_title),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Surface(
                                    shape = Capsule,
                                    color = SolarAmber.copy(alpha = 0.18f),
                                    border = BorderStroke(0.5.dp, SolarAmber.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        text = "PRO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SolarAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.upgrade_pro_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        ActuatePrimaryButton(
                            text = "Upgrade",
                            onClick = {
                                haptics.selectionChanged()
                                showPaywall = true
                            },
                            size = ButtonSize.SMALL,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            BoldEyebrow(
                text = "MESSAGING",
                dotColor = WhatsappGreen,
            )

            val emailConnected = state.emailConnected
            val whatsappConnected = state.whatsappConnected
            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                CupertinoGroupedCell(
                    title = "Email & WhatsApp",
                    subtitle = when {
                        emailConnected && whatsappConnected -> "Both connected · cancellations send automatically"
                        emailConnected -> "Email connected · connect WhatsApp for full coverage"
                        whatsappConnected -> "WhatsApp connected · connect email for full coverage"
                        else -> "Connect once to send cancellation messages automatically"
                    },
                    icon = Icons.Rounded.AttachEmail,
                    iconBackground = LinkBlue,
                    position = CellPosition.SINGLE,
                    onClick = onOpenConnections,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (emailConnected || whatsappConnected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(SuccessEmerald, CircleShape),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "On",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = SuccessEmerald,
                                )
                                Spacer(Modifier.width(Spacing.xs))
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            BoldEyebrow(
                text = "PREFERENCES",
                dotColor = CobaltVivid,
            )

            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                CupertinoGroupedCell(
                    title = stringResource(R.string.language),
                    subtitle = "English",
                    icon = Icons.Rounded.Language,
                    iconBackground = LinkBlue,
                    position = CellPosition.SINGLE,
                    onClick = null,
                    trailingContent = {
                        Text(
                            text = "English",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            BoldEyebrow(
                text = stringResource(R.string.privacy_storage_title),
                dotColor = NotionIndigo,
            )

            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                CupertinoGroupedCell(
                    title = stringResource(R.string.hardware_keystore_title),
                    subtitle = stringResource(R.string.hardware_keystore_desc),
                    icon = Icons.Rounded.Key,
                    iconBackground = NotionPrismatic,
                    position = CellPosition.FIRST,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Encrypted",
                            tint = Verge,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                CupertinoGroupedCell(
                    title = stringResource(R.string.clear_history_title),
                    subtitle = stringResource(R.string.clear_history_desc),
                    icon = Icons.Rounded.DeleteOutline,
                    iconBackground = DangerRose,
                    position = CellPosition.LAST,
                    onClick = {
                        showClearHistoryDialog = true
                    },
                    trailingContent = {
                        ActuateDestructiveButton(
                            text = stringResource(R.string.clear_button),
                            onClick = { showClearHistoryDialog = true },
                            size = ButtonSize.SMALL,
                        )
                    },
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            BoldEyebrow(
                text = "ACCOUNT",
                dotColor = SolarAmber,
            )

            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                CupertinoGroupedCell(
                    title = stringResource(R.string.reset_account_title),
                    subtitle = stringResource(R.string.reset_account_desc),
                    icon = Icons.Rounded.RestartAlt,
                    iconBackground = SolarAmber,
                    position = CellPosition.SINGLE,
                    onClick = {
                        showResetDialog = true
                    },
                    trailingContent = {
                        ActuateDestructiveButton(
                            text = stringResource(R.string.reset_button),
                            onClick = { showResetDialog = true },
                            size = ButtonSize.SMALL,
                        )
                    },
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            BoldEyebrow(
                text = "DEVELOPER & ADVANCED DIAGNOSTICS",
                dotColor = ElectricCyan,
            )

            val isConnected = state.serverStatus?.startsWith("Connected") == true
            val isChecking = state.checkingServer

            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                CupertinoGroupedCell(
                    title = "Actuate VPS Endpoint",
                    subtitle = "35.232.148.87.sslip.io:8787",
                    icon = Icons.Rounded.CloudSync,
                    iconBackground = if (isConnected) AppleBlue else SolarAmber,
                    position = CellPosition.FIRST,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isConnected) Verge else DangerRose, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Active" else "Offline",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = if (isConnected) Verge else DangerRose,
                            )
                        }
                    },
                )
                CupertinoGroupedCell(
                    title = stringResource(R.string.connection_status),
                    subtitle = state.serverStatus ?: "Checking…",
                    icon = Icons.Rounded.Refresh,
                    iconBackground = SignalBlue,
                    position = CellPosition.LAST,
                    onClick = {
                        viewModel.testConnection()
                    },
                    trailingContent = {
                        ActuateTonalButton(
                            text = stringResource(R.string.test_button),
                            onClick = { viewModel.testConnection() },
                            size = ButtonSize.SMALL,
                            isLoading = isChecking,
                        )
                    },
                )
            }

            Spacer(Modifier.height(Spacing.md))

            SpeechEngineStatusCard(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(Spacing.md))

            CloudDiagnosticsCard(
                latencyMs = state.latencyMs,
                serverStatus = state.serverStatus,
                checking = state.checkingServer,
                onTest = { viewModel.testConnection() },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))

            FeedbackTogglesCard(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(Spacing.xl))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Actuate · 1.1.0",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ShipAThon 2026 · Speak. Actuate.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun SettingsViewModel.testConnection() = checkServerStatus()
