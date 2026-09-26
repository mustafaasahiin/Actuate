package com.actuate.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actuate.app.R
import com.actuate.core.components.ActuateCard
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit,
    onGetStarted: () -> Unit = onDismiss,
    onTryPrompt: ((String) -> Unit)? = null,
    micGranted: Boolean = false,
    notificationsGranted: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isSystemInDarkTheme()) DeepGraphite else Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Mist.copy(alpha = 0.4f),
                width = 36.dp,
                height = 5.dp,
            )
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xs))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Ice, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = AppleBlue,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = Carbon,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Graphite,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )

            Spacer(Modifier.height(Spacing.xl))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FeatureRow(
                    icon = Icons.Rounded.Mic,
                    tint = AppleBlue,
                    title = stringResource(R.string.floating_bubble_feature_title),
                    subtitle = stringResource(R.string.floating_bubble_feature_desc),
                )

                FeatureRow(
                    icon = Icons.Rounded.CalendarMonth,
                    tint = SignalBlue,
                    title = stringResource(R.string.calendar_lists_feature_title),
                    subtitle = stringResource(R.string.calendar_lists_feature_desc),
                )

                FeatureRow(
                    icon = Icons.Rounded.Security,
                    tint = Graphite,
                    title = stringResource(R.string.private_offline_feature_title),
                    subtitle = stringResource(R.string.private_offline_feature_desc),
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.try_saying_header),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Ash,
                )

                Spacer(Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SamplePromptChip(
                        prompt = "Add meeting with Priya tomorrow at 3 PM",
                        onClick = { onTryPrompt?.invoke("Add meeting with Priya tomorrow at 3 PM") },
                    )
                    SamplePromptChip(
                        prompt = "Put milk and eggs on shopping list",
                        onClick = { onTryPrompt?.invoke("Put milk and eggs on shopping list") },
                    )
                    SamplePromptChip(
                        prompt = "Remind me to stretch at 5 PM",
                        onClick = { onTryPrompt?.invoke("Remind me to stretch at 5 PM") },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            ActuateCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.permissions_header),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Ash,
                )
                Spacer(Modifier.height(Spacing.xs))

                PermissionItem(
                    icon = Icons.Rounded.Mic,
                    name = stringResource(R.string.microphone_permission_title),
                    description = stringResource(R.string.microphone_permission_desc),
                    granted = micGranted,
                )

                Spacer(Modifier.height(Spacing.xs))

                PermissionItem(
                    icon = Icons.Rounded.Notifications,
                    name = stringResource(R.string.notifications_permission_title),
                    description = stringResource(R.string.notifications_permission_desc),
                    granted = notificationsGranted,
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            ActuatePrimaryButton(
                text = stringResource(R.string.get_started_button),
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            )

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Ice, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Carbon,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Graphite,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SamplePromptChip(
    prompt: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = Capsule,
        color = Ice,
        border = BorderStroke(1.dp, Mist.copy(alpha = 0.35f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = AppleBlue,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(Spacing.xxs))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = Carbon,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    name: String,
    description: String,
    granted: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) Verge else Graphite,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Carbon,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Ash,
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = Verge,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
