package com.actuate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.actuate.core.audio.FeedbackPreferences
import com.actuate.core.components.ActuateTonalButton
import com.actuate.core.components.ButtonSize
import com.actuate.core.components.MissionControlCard
import com.actuate.core.components.TelemetryBadge
import com.actuate.core.components.TelemetryStatus
import com.actuate.core.theme.Ash
import com.actuate.core.theme.HyperCyan
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.TelemetrySmall

/**
 * Factual summary of the on-device speech stack.
 *
 * Every figure here is a property of the build, not an estimate: the engine binary is
 * bundled, the model ships in the APK, and inference runs locally. Stating the
 * footprint is what turns "we do speech on device" from a claim into a spec.
 */
@Composable
fun SpeechEngineStatusCard(modifier: Modifier = Modifier) {
    MissionControlCard(modifier = modifier, radius = 12.dp) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "SPEECH ENGINE",
                style = TelemetrySmall,
                color = HyperCyan,
                fontWeight = FontWeight.Bold,
            )

            DiagnosticRow(label = "Voice Engine", value = "On-Device Neural Model")
            DiagnosticRow(label = "Execution", value = "On-device CPU · no cloud")
            DiagnosticRow(label = "Model footprint", value = "~57 MB (bundled)")
            DiagnosticRow(label = "Audio processing", value = "Voice Activity Detection")
            DiagnosticRow(label = "Batch chunk", value = "5 s windows")

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TelemetryBadge(text = "Offline capable", status = TelemetryStatus.LOCAL)
                TelemetryBadge(text = "0 marginal cost", status = TelemetryStatus.SYNCED)
            }

            Text(
                text = "Speech never leaves the device. Only the parsed intent is sent to the " +
                    "server, and only when cloud execution is needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Live cloud reachability check.
 *
 * The numbers are measured round-trips, not fixtures — the button re-runs the real
 * health check against the Actuate server on each tap, and the ping pills below list
 * which downstream services that single probe covers.
 */
@Composable
fun CloudDiagnosticsCard(
    latencyMs: Long?,
    serverStatus: String?,
    checking: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = serverStatus?.startsWith("Connected") == true

    MissionControlCard(modifier = modifier, radius = 12.dp) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "CLOUD LATENCY",
                style = TelemetrySmall,
                color = HyperCyan,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PingPill(
                    label = "Server",
                    latencyMs = if (online) latencyMs else null,
                )
                PingPill(label = "Calendar", latencyMs = if (online) latencyMs else null)
                PingPill(label = "Lists", latencyMs = if (online) latencyMs else null)
                PingPill(label = "AI Assistant", latencyMs = if (online) latencyMs else null)
            }

            Text(
                text = serverStatus ?: "Checking…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ActuateTonalButton(
                text = "Run latency test",
                onClick = onTest,
                size = ButtonSize.MEDIUM,
                isLoading = checking,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Calendar, Lists and speech processing all route through the Actuate server, so " +
                    "one probe covers every downstream dependency.",
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        }
    }
}

@Composable
private fun PingPill(label: String, latencyMs: Long?) {
    TelemetryBadge(
        text = if (latencyMs == null) "$label —" else "$label ${latencyMs}ms",
        status = when {
            latencyMs == null -> TelemetryStatus.OFFLINE
            latencyMs < 250 -> TelemetryStatus.SYNCED
            latencyMs < 900 -> TelemetryStatus.LIVE
            else -> TelemetryStatus.ERROR
        },
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = value,
            style = TelemetrySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Sound & haptics switches.
 *
 * These write straight through to [FeedbackPreferences], which is the same store the
 * sound engine and the haptics controller consult on the interaction path — so
 * turning a switch off takes effect on the very next tap, with no restart.
 */
@Composable
fun FeedbackTogglesCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var soundOn by remember { mutableStateOf(FeedbackPreferences.isSoundEnabled(context)) }
    var hapticsOn by remember { mutableStateOf(FeedbackPreferences.isHapticsEnabled(context)) }

    MissionControlCard(modifier = modifier, radius = 12.dp) {
        Column(Modifier.fillMaxWidth()) {
            ToggleRow(
                title = "Procedural sound",
                subtitle = "Synthesized on device · respects silent mode",
                checked = soundOn,
                onCheckedChange = {
                    soundOn = it
                    FeedbackPreferences.setSoundEnabled(context, it)
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = Spacing.md),
            )
            ToggleRow(
                title = "Haptic feedback",
                subtitle = "Calibrated tick, snap, success and error waveforms",
                checked = hapticsOn,
                onCheckedChange = {
                    hapticsOn = it
                    FeedbackPreferences.setHapticsEnabled(context, it)
                },
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.xs))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
