package com.actuate.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.actuate.core.components.ActuateIconButton
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.components.ActuateTextField
import com.actuate.core.components.ActuateTonalButton
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.ButtonSize
import com.actuate.core.components.CellPosition
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.CupertinoGroupedCell
import com.actuate.core.components.TextLink
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Ice
import com.actuate.core.theme.LinkBlue
import com.actuate.core.theme.NotionIndigo
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.SuccessEmerald
import com.actuate.core.theme.Verge
import com.actuate.core.theme.WhatsappGreen
import org.koin.androidx.compose.koinViewModel

/**
 * One-time connection flow for outbound messaging channels. Cancellation
 * messages are dispatched server-side through these connections — the app
 * itself never sees or stores the credentials.
 */
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberCupertinoHaptics()

    var editingChannel by remember { mutableStateOf<Channel?>(null) }
    var testingChannel by remember { mutableStateOf<Channel?>(null) }
    var disconnectChannel by remember { mutableStateOf<Channel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            Text(
                text = "Messaging Connections",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            state.banner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessEmerald.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = SuccessEmerald,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SuccessEmerald,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissBanner) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            state.error?.let { error ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            state.corruptedChannel?.let { channel ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SolarAmber.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp)) {
                        Text(
                            text = "${channel.label} credentials need reconnecting",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = SolarAmber,
                        )
                        Text(
                            text = "The stored credentials could not be verified. Please connect ${channel.label} again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextLink(
                            text = "Dismiss",
                            onClick = viewModel::dismissCorrupted,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            Text(
                text = "Connect your email and WhatsApp once. When you cancel an event with an attendee, " +
                    "Actuate sends the cancellation message for you — automatically, in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))

            BoldEyebrow(text = "Channels", dotColor = LinkBlue)

            ChannelCard(
                channel = Channel.EMAIL,
                title = "Email (SMTP)",
                connectedSubtitle = state.connection(Channel.EMAIL).hint.ifBlank { "Connected" },
                disconnectedSubtitle = "Send cancellation emails from your own address",
                icon = Icons.Rounded.Email,
                iconColor = LinkBlue,
                busy = state.connecting == Channel.EMAIL || state.testing == Channel.EMAIL ||
                    state.disconnecting == Channel.EMAIL,
                connected = state.connection(Channel.EMAIL).connected,
                verified = state.connection(Channel.EMAIL).verified,
                connectedAt = state.connection(Channel.EMAIL).connectedAt,
                onConnect = {
                    haptics.selectionChanged()
                    editingChannel = Channel.EMAIL
                },
                onTest = {
                    haptics.selectionChanged()
                    testingChannel = Channel.EMAIL
                },
                onDisconnect = {
                    haptics.selectionChanged()
                    disconnectChannel = Channel.EMAIL
                },
            )

            Spacer(Modifier.height(Spacing.sm))

            ChannelCard(
                channel = Channel.WHATSAPP,
                title = "WhatsApp Business",
                connectedSubtitle = state.connection(Channel.WHATSAPP).hint.ifBlank { "Connected" },
                disconnectedSubtitle = "Send cancellations through the WhatsApp Cloud API",
                icon = Icons.Rounded.Whatsapp,
                iconColor = WhatsappGreen,
                busy = state.connecting == Channel.WHATSAPP || state.testing == Channel.WHATSAPP ||
                    state.disconnecting == Channel.WHATSAPP,
                connected = state.connection(Channel.WHATSAPP).connected,
                verified = state.connection(Channel.WHATSAPP).verified,
                connectedAt = state.connection(Channel.WHATSAPP).connectedAt,
                onConnect = {
                    haptics.selectionChanged()
                    editingChannel = Channel.WHATSAPP
                },
                onTest = {
                    haptics.selectionChanged()
                    testingChannel = Channel.WHATSAPP
                },
                onDisconnect = {
                    haptics.selectionChanged()
                    disconnectChannel = Channel.WHATSAPP
                },
            )

            Spacer(Modifier.height(Spacing.lg))

            BoldEyebrow(text = "How your data is protected", dotColor = NotionIndigo)

            CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
                PrivacyCell(
                    position = CellPosition.FIRST,
                    title = "Encrypted at rest",
                    subtitle = "Credentials are AES-256-GCM encrypted on the server before storage. The app never sees them.",
                )
                PrivacyCell(
                    position = CellPosition.MIDDLE,
                    title = "Verified before saving",
                    subtitle = "A live connection test runs before anything is stored. Bad credentials are never saved.",
                )
                PrivacyCell(
                    position = CellPosition.MIDDLE,
                    title = "Sent only on your command",
                    subtitle = "Messages go out only when you cancel an event and choose Send — never on a schedule.",
                )
                PrivacyCell(
                    position = CellPosition.LAST,
                    title = "Disconnect anytime",
                    subtitle = "Disconnecting wipes the stored credentials from the server immediately.",
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextLink(
                    text = "Refresh status",
                    onClick = { viewModel.refresh() },
                )
                Spacer(Modifier.width(Spacing.sm))
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    editingChannel?.let { channel ->
        ConnectChannelSheet(
            channel = channel,
            onDismiss = { editingChannel = null },
            onConnectEmail = { host, port, user, password ->
                viewModel.connectEmail(host, port, user, password)
                editingChannel = null
            },
            onConnectWhatsApp = { phoneId, token ->
                viewModel.connectWhatsApp(phoneId, token)
                editingChannel = null
            },
        )
    }

    testingChannel?.let { channel ->
        TestChannelDialog(
            channel = channel,
            isSending = state.testing == channel,
            onDismiss = { testingChannel = null },
            onSend = { recipient ->
                viewModel.sendTest(channel, recipient)
                testingChannel = null
            },
        )
    }

    disconnectChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = { disconnectChannel = null },
            title = { Text("Disconnect ${channel.label}?") },
            text = { Text("Stored credentials will be wiped from the server. Future cancellation messages will skip ${channel.label}.") },
            confirmButton = {
                ActuatePrimaryButton(
                    text = "Disconnect",
                    onClick = {
                        viewModel.disconnect(channel)
                        disconnectChannel = null
                    },
                    size = ButtonSize.MEDIUM,
                )
            },
            dismissButton = {
                TextButton(onClick = { disconnectChannel = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PrivacyCell(
    position: CellPosition,
    title: String,
    subtitle: String,
) {
    CupertinoGroupedCell(
        title = title,
        subtitle = subtitle,
        icon = Icons.Rounded.Lock,
        iconBackground = NotionIndigo,
        position = position,
    )
}

@Composable
private fun ChannelCard(
    channel: Channel,
    title: String,
    connectedSubtitle: String,
    disconnectedSubtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    busy: Boolean,
    connected: Boolean,
    verified: Boolean,
    connectedAt: Long?,
    onConnect: () -> Unit,
    onTest: () -> Unit,
    onDisconnect: () -> Unit,
) {
    CupertinoGroupedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (busy) {
                            Spacer(Modifier.width(Spacing.sm))
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        }
                    }
                    Text(
                        text = if (connected) connectedSubtitle else disconnectedSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (connected) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (verified) SuccessEmerald.copy(alpha = 0.15f) else SolarAmber.copy(alpha = 0.15f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (verified) Icons.Rounded.Verified else Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = if (verified) SuccessEmerald else SolarAmber,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (verified) "Verified" else "Connected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (verified) SuccessEmerald else SolarAmber,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!connected) {
                    ActuatePrimaryButton(
                        text = "Connect",
                        onClick = onConnect,
                        size = ButtonSize.SMALL,
                    )
                } else {
                    ActuateTonalButton(
                        text = "Send test",
                        onClick = onTest,
                        size = ButtonSize.SMALL,
                    )
                    ActuateTonalButton(
                        text = "Disconnect",
                        onClick = onDisconnect,
                        size = ButtonSize.SMALL,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConnectChannelSheet(
    channel: Channel,
    onDismiss: () -> Unit,
    onConnectEmail: (host: String, port: Int, user: String, password: String) -> Unit,
    onConnectWhatsApp: (phoneNumberId: String, accessToken: String) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("587") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var phoneNumberId by rememberSaveable { mutableStateOf("") }
    var accessToken by rememberSaveable { mutableStateOf("") }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = when (channel) {
                    Channel.EMAIL -> "Connect Email (SMTP)"
                    Channel.WHATSAPP -> "Connect WhatsApp Business"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (channel) {
                    Channel.EMAIL ->
                        "Use an app password (Gmail: myaccount.google.com → Security → App passwords). " +
                            "Credentials are verified live, then encrypted before storage."
                    Channel.WHATSAPP ->
                        "From the Meta for Developers dashboard, copy the Phone Number ID and a permanent access token."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (channel) {
                Channel.EMAIL -> {
                    ActuateTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = "SMTP host (e.g. smtp.gmail.com)",
                    )
                    ActuateTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = "Port (587 STARTTLS / 465 TLS)",
                        keyboardType = KeyboardType.Number,
                    )
                    ActuateTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = "Email username",
                        keyboardType = KeyboardType.Email,
                    )
                    ActuateTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "App password",
                        isPassword = true,
                    )
                    ActuatePrimaryButton(
                        text = "Verify & Connect",
                        onClick = {
                            onConnectEmail(
                                host.trim(),
                                port.toIntOrNull() ?: 587,
                                user.trim(),
                                password,
                            )
                        },
                        enabled = host.isNotBlank() && user.isNotBlank() && password.length >= 8,
                        size = ButtonSize.LARGE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Channel.WHATSAPP -> {
                    ActuateTextField(
                        value = phoneNumberId,
                        onValueChange = { phoneNumberId = it.filter(Char::isDigit).take(20) },
                        label = "Phone Number ID",
                        keyboardType = KeyboardType.Number,
                    )
                    ActuateTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = "Permanent access token",
                        isPassword = true,
                    )
                    ActuatePrimaryButton(
                        text = "Verify & Connect",
                        onClick = { onConnectWhatsApp(phoneNumberId.trim(), accessToken.trim()) },
                        enabled = phoneNumberId.isNotBlank() && accessToken.isNotBlank(),
                        size = ButtonSize.LARGE,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TestChannelDialog(
    channel: Channel,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var recipient by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text("Send a test via ${channel.label}") },
        text = {
            Column {
                Text(
                    text = when (channel) {
                        Channel.EMAIL -> "Send a short test email to verify the connection end-to-end."
                        Channel.WHATSAPP -> "Send a short WhatsApp message to a number with country code, e.g. +14155552671."
                    },
                )
                Spacer(Modifier.height(Spacing.sm))
                ActuateTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = if (channel == Channel.EMAIL) "Recipient email" else "Recipient number (with country code)",
                    keyboardType = if (channel == Channel.EMAIL) KeyboardType.Email else KeyboardType.Phone,
                    enabled = !isSending,
                )
            }
        },
        confirmButton = {
            ActuatePrimaryButton(
                text = "Send",
                onClick = { onSend(recipient.trim()) },
                enabled = recipient.isNotBlank() && !isSending,
                size = ButtonSize.MEDIUM,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        },
    )
}
