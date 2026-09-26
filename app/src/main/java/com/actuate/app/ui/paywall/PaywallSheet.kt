package com.actuate.app.ui.paywall

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actuate.app.R
import com.actuate.core.components.ActuateCard
import com.actuate.core.components.ActuatePrimaryButton
import com.actuate.core.components.ActuateTextField
import com.actuate.core.components.ActuateTonalButton
import com.actuate.core.components.ButtonSize
import com.actuate.core.components.Hairline
import com.actuate.core.components.StatusChip
import com.actuate.core.audio.LocalTactileSound
import com.actuate.core.haptics.rememberTactileFeedback
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.LinkBlue
import com.actuate.core.theme.Mist
import com.actuate.core.theme.Pebble
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.data.entitlement.RevenueCatEntitlementProvider
import com.actuate.domain.entitlement.EntitlementProvider
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class PaywallPlan {
    MONTHLY,
    ANNUAL,
    LIFETIME,
}

private const val PROMO_JUDGE_PASS = "SHIPATON2026"

/**
 * Actuate Pro Upgrade Paywall.
 *
 * Follows design.md strictly:
 * - Apple design language, near-white canvas
 * - 8dp card corners, 980dp capsule buttons
 * - Apple Blue #0071E3 for primary action and active selection states
 * - Mist hairline borders (1dp), zero drop shadows
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    onDismiss: () -> Unit,
    onUpgradeSuccess: () -> Unit = onDismiss,
    onUnlockPro: suspend () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val entitlementProvider: EntitlementProvider = koinInject()
    val rcProvider = entitlementProvider as? RevenueCatEntitlementProvider

    var selectedPlan by remember { mutableStateOf(PaywallPlan.ANNUAL) }
    var isSubscribing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var subscriptionError by remember { mutableStateOf<String?>(null) }

    var isLoadingOfferings by remember { mutableStateOf(false) }
    var offeringLoadError by remember { mutableStateOf<String?>(null) }
    var currentOffering by remember { mutableStateOf<Offering?>(rcProvider?.getCachedOffering()) }
    var retryTrigger by remember { mutableStateOf(0) }

    var isPromoExpanded by remember { mutableStateOf(false) }
    var promoCode by remember { mutableStateOf("") }
    var promoError by remember { mutableStateOf<String?>(null) }
    var promoSuccess by remember { mutableStateOf<String?>(null) }
    var celebrating by remember { mutableStateOf(false) }
    val soundEngine = LocalTactileSound.current
    val haptics = rememberTactileFeedback()

    LaunchedEffect(retryTrigger) {
        if (Purchases.isConfigured) {
            isLoadingOfferings = currentOffering == null
            offeringLoadError = null
            try {
                Purchases.sharedInstance.getOfferingsWith(
                    onError = { error ->
                        isLoadingOfferings = false
                        if (currentOffering == null) {
                            val cached = rcProvider?.getCachedOffering()
                            if (cached != null) {
                                currentOffering = cached
                                offeringLoadError = null
                            } else {
                                offeringLoadError = if (error.code == PurchasesErrorCode.ConfigurationError) {
                                    "Subscription plans are being configured. Tap Retry or unlock with the judge pass below."
                                } else {
                                    "Unable to connect to paywall. Tap Retry or unlock with the judge pass below."
                                }
                            }
                        }
                    },
                    onSuccess = { offerings ->
                        isLoadingOfferings = false
                        val resolved = offerings.current
                            ?: offerings.getOffering("pro")
                            ?: offerings.getOffering("active")
                            ?: offerings.all.values.firstOrNull()

                        if (resolved != null && resolved.availablePackages.isNotEmpty()) {
                            currentOffering = resolved
                            rcProvider?.cacheOffering(resolved)
                            offeringLoadError = null
                        } else if (currentOffering == null) {
                            offeringLoadError = "Subscription plans are currently updating. Tap Retry or unlock with the judge pass below."
                        }
                    }
                )
            } catch (e: Exception) {
                isLoadingOfferings = false
                if (currentOffering == null) {
                    offeringLoadError = "Unable to connect to paywall. Tap Retry or unlock with the judge pass below."
                }
            }
        } else {
            isLoadingOfferings = false
            if (currentOffering == null) {
                offeringLoadError = "In-app purchases are currently unavailable. You can unlock full access with the judge pass below."
            }
        }
    }

    // The confetti is drawn behind the sheet's own content, inside the sheet's layout,
    // so it cannot escape the sheet bounds or steal touches from the buttons.
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
        Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xs))

            // Actuate Pro Badge
            Box(
                modifier = Modifier
                    .background(Ice, Capsule)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.actuate_pro_badge),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppleBlue,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // Header Title
            Text(
                text = stringResource(R.string.speak_without_limits_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xs))

            // Subtitle
            Text(
                text = stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )

            Spacer(Modifier.height(Spacing.lg))

            // Feature List Card
            ActuateCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PaywallFeatureRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Unlimited voice & text actions",
                        description = "Bypass the 20 actions/week limit with unconstrained speed",
                    )
                    Hairline()
                    PaywallFeatureRow(
                        icon = Icons.Rounded.Sync,
                        title = "Live Google Calendar & Lists sync",
                        description = "Seamless bidirectional updates to your connected accounts",
                    )
                    Hairline()
                    PaywallFeatureRow(
                        icon = Icons.Rounded.Bolt,
                        title = "Priority offline rule execution",
                        description = "Deterministic local parsing and execution when offline",
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            val cachedPrices = remember { rcProvider?.getCachedPrices() ?: emptyMap() }
            val annualPrice = currentOffering?.annual?.product?.price?.formatted?.let { formatted ->
                if (formatted.contains("/")) formatted else "$formatted / year"
            } ?: cachedPrices["annual"] ?: "$29.99 / year"

            val monthlyPrice = currentOffering?.monthly?.product?.price?.formatted?.let { formatted ->
                if (formatted.contains("/")) formatted else "$formatted / month"
            } ?: cachedPrices["monthly"] ?: "$4.99 / month"

            val lifetimePrice = currentOffering?.lifetime?.product?.price?.formatted
                ?: cachedPrices["lifetime"] ?: "$79.99 one-time"

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PlanCard(
                    title = stringResource(R.string.annual_plan_title),
                    price = annualPrice,
                    subtitle = stringResource(R.string.annual_plan_subtitle),
                    badge = "Best Value",
                    selected = selectedPlan == PaywallPlan.ANNUAL,
                    onClick = { selectedPlan = PaywallPlan.ANNUAL },
                )

                PlanCard(
                    title = stringResource(R.string.monthly_plan_title),
                    price = monthlyPrice,
                    subtitle = stringResource(R.string.monthly_plan_subtitle),
                    badge = null,
                    selected = selectedPlan == PaywallPlan.MONTHLY,
                    onClick = { selectedPlan = PaywallPlan.MONTHLY },
                )

                PlanCard(
                    title = "Lifetime Access",
                    price = lifetimePrice,
                    subtitle = "One-time purchase · Unlimited forever",
                    badge = "VIP Access",
                    selected = selectedPlan == PaywallPlan.LIFETIME,
                    onClick = { selectedPlan = PaywallPlan.LIFETIME },
                )
            }

            Spacer(Modifier.height(Spacing.md))

            if (offeringLoadError != null) {
                ActuateCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Ice, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Unable to load subscription plans right now.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Carbon,
                            )
                            Spacer(Modifier.height(Spacing.xxs))
                            Text(
                                text = offeringLoadError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Graphite,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        ActuateTonalButton(
                            text = "Retry",
                            onClick = { retryTrigger++ },
                            enabled = !isLoadingOfferings,
                            isLoading = isLoadingOfferings,
                            size = ButtonSize.SMALL,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
            }

            if (!subscriptionError.isNullOrBlank()) {
                Text(
                    text = subscriptionError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
                Spacer(Modifier.height(Spacing.xs))
            }

            ActuatePrimaryButton(
                text = when {
                    isSubscribing -> stringResource(R.string.processing_button)
                    isLoadingOfferings -> "Loading plans…"
                    else -> stringResource(R.string.subscribe_pro_button)
                },
                enabled = !isSubscribing && !isLoadingOfferings,
                isLoading = isSubscribing,
                onClick = {
                    if (!isSubscribing && !isLoadingOfferings) {
                        subscriptionError = null
                        val rcProvider = entitlementProvider as? RevenueCatEntitlementProvider
                        if (currentOffering != null) {
                            if (rcProvider != null && rcProvider.isReady()) {
                                isSubscribing = true
                                val activity = context as? Activity
                                if (activity != null) {
                                    val packageToBuy = when (selectedPlan) {
                                        PaywallPlan.MONTHLY -> currentOffering?.monthly ?: currentOffering?.availablePackages?.firstOrNull { it.identifier == "\$rc_monthly" || it.identifier.contains("month", ignoreCase = true) }
                                        PaywallPlan.ANNUAL -> currentOffering?.annual ?: currentOffering?.availablePackages?.firstOrNull { it.identifier == "\$rc_annual" || it.identifier.contains("year", ignoreCase = true) || it.identifier.contains("annual", ignoreCase = true) }
                                        PaywallPlan.LIFETIME -> currentOffering?.lifetime ?: currentOffering?.availablePackages?.firstOrNull { it.identifier == "\$rc_lifetime" || it.identifier.contains("life", ignoreCase = true) }
                                    } ?: currentOffering?.availablePackages?.firstOrNull()
                                    if (packageToBuy != null) {
                                        rcProvider.purchasePackage(
                                            activity = activity,
                                            packageToPurchase = packageToBuy,
                                            onSuccess = {
                                                scope.launch {
                                                    entitlementProvider.setPro(true)
                                                    onUnlockPro()
                                                    isSubscribing = false
                                                    onUpgradeSuccess()
                                                }
                                            },
                                            onError = { error, userCancelled ->
                                                isSubscribing = false
                                                if (!userCancelled) {
                                                    subscriptionError = error.message
                                                }
                                            },
                                        )
                                    } else {
                                        isSubscribing = false
                                        subscriptionError = "No subscription packages found in RevenueCat offering."
                                    }
                                } else {
                                    isSubscribing = false
                                    subscriptionError = "Activity context is required for payment."
                                }
                            } else {
                                subscriptionError = "RevenueCat is not configured. Put your RevenueCat public key in revenuecat.xml, or enter the Judge Pass 'SHIPATON2026' below to test Pro."
                            }
                        } else if (offeringLoadError != null) {
                            subscriptionError = "Unable to load subscription plans. Tap Retry above or use the judge code below."
                            retryTrigger++
                        } else if (rcProvider != null && rcProvider.isReady()) {
                            isSubscribing = true
                            val activity = context as? Activity
                            if (activity != null) {
                                Purchases.sharedInstance.getOfferingsWith(
                                    onError = { error ->
                                        isSubscribing = false
                                        subscriptionError = "Failed to load products: ${error.message}"
                                    },
                                    onSuccess = { offerings ->
                                        val offering = offerings.current
                                        currentOffering = offering
                                        val packageToBuy = when (selectedPlan) {
                                            PaywallPlan.MONTHLY -> offering?.monthly ?: offering?.availablePackages?.firstOrNull { it.identifier == "\$rc_monthly" || it.identifier.contains("month", ignoreCase = true) }
                                            PaywallPlan.ANNUAL -> offering?.annual ?: offering?.availablePackages?.firstOrNull { it.identifier == "\$rc_annual" || it.identifier.contains("year", ignoreCase = true) || it.identifier.contains("annual", ignoreCase = true) }
                                            PaywallPlan.LIFETIME -> offering?.lifetime ?: offering?.availablePackages?.firstOrNull { it.identifier == "\$rc_lifetime" || it.identifier.contains("life", ignoreCase = true) }
                                        } ?: offering?.availablePackages?.firstOrNull()
                                        if (packageToBuy != null) {
                                            rcProvider.purchasePackage(
                                                activity = activity,
                                                packageToPurchase = packageToBuy,
                                                onSuccess = {
                                                    scope.launch {
                                                        entitlementProvider.setPro(true)
                                                        onUnlockPro()
                                                        isSubscribing = false
                                                        onUpgradeSuccess()
                                                    }
                                                },
                                                onError = { error, userCancelled ->
                                                    isSubscribing = false
                                                    if (!userCancelled) {
                                                        subscriptionError = error.message
                                                    }
                                                },
                                            )
                                        } else {
                                            isSubscribing = false
                                            subscriptionError = "No subscription packages found in RevenueCat offering."
                                        }
                                    },
                                )
                            } else {
                                isSubscribing = false
                                subscriptionError = "Activity context is required for payment."
                            }
                        } else {
                            subscriptionError = "RevenueCat is not configured. Put your RevenueCat public key in revenuecat.xml, or enter the Judge Pass 'SHIPATON2026' below to test Pro."
                        }
                    }
                },
                size = ButtonSize.LARGE,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.xs))

            ActuateTonalButton(
                text = stringResource(R.string.restore_purchases_button),
                onClick = {
                    if (!isRestoring && !isSubscribing) {
                        subscriptionError = null
                        if (Purchases.isConfigured) {
                            isRestoring = true
                            try {
                                Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                                    override fun onReceived(customerInfo: CustomerInfo) {
                                        isRestoring = false
                                        val isPro = customerInfo.entitlements["pro"]?.isActive == true ||
                                            customerInfo.entitlements["pro_access"]?.isActive == true ||
                                            customerInfo.entitlements["pro_monthly"]?.isActive == true ||
                                            customerInfo.entitlements["pro_yearly"]?.isActive == true ||
                                            customerInfo.activeSubscriptions.isNotEmpty()

                                        if (isPro) {
                                            scope.launch {
                                                entitlementProvider.setPro(true)
                                                onUnlockPro()
                                                onUpgradeSuccess()
                                            }
                                        } else {
                                            subscriptionError = "No active Pro subscription found to restore."
                                        }
                                    }

                                    override fun onError(error: PurchasesError) {
                                        isRestoring = false
                                        subscriptionError = "Restore failed: ${error.message}"
                                    }
                                })
                            } catch (e: Exception) {
                                isRestoring = false
                                subscriptionError = "Restore failed: ${e.message ?: "Unknown error"}"
                            }
                        } else {
                            subscriptionError = "In-app purchases are not configured on this device. Use the judge code below to test Pro."
                        }
                    }
                },
                enabled = !isRestoring && !isSubscribing,
                isLoading = isRestoring,
                size = ButtonSize.MEDIUM,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            Hairline(Modifier.padding(vertical = Spacing.xs))

            // Judge / Demo Pass Unlock Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        isPromoExpanded = !isPromoExpanded
                        promoError = null
                    }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ConfirmationNumber,
                    contentDescription = null,
                    tint = LinkBlue,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xxs))
                Text(
                    text = stringResource(R.string.redeem_judge_code),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = LinkBlue,
                )
                Spacer(Modifier.width(Spacing.xxs))
                Icon(
                    imageVector = if (isPromoExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LinkBlue,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Expandable Promo/Judge Pass input field
            AnimatedVisibility(
                visible = isPromoExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        ActuateTextField(
                            value = promoCode,
                            onValueChange = {
                                promoCode = it
                                promoError = null
                            },
                            label = "Promo / Judge Code",
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        ActuateTonalButton(
                            text = "Redeem",
                            size = ButtonSize.MEDIUM,
                            onClick = {
                                if (promoCode.trim().equals(PROMO_JUDGE_PASS, ignoreCase = true)) {
                                    promoSuccess = context.getString(R.string.judge_code_success)
                                    promoError = null
                                    // Unlock is announced on both channels: the golden burst is
                                    // the visual, the rising sweep and the four-beat rumble are
                                    // what makes the moment land physically.
                                    celebrating = true
                                    soundEngine?.playActionActuated()
                                    haptics.celebration()
                                    scope.launch {
                                        entitlementProvider.setPro(true)
                                        onUnlockPro()
                                        delay(500)
                                        onUpgradeSuccess()
                                    }
                                } else {
                                    promoError = context.getString(R.string.judge_code_invalid)
                                    soundEngine?.playErrorBuzz()
                                    haptics.error()
                                }
                            },
                        )
                    }

                    if (promoError != null) {
                        Text(
                            text = promoError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.xxs),
                        )
                    }

                    if (promoSuccess != null) {
                        Text(
                            text = promoSuccess!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Verge,
                            modifier = Modifier.padding(horizontal = Spacing.xxs),
                        )
                    }

                    if (celebrating) {
                        JudgePassBadge()
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
        }

        // Celebrate on top of the sheet content, but only while the burst runs.
        ConfettiBurst(play = celebrating, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PaywallFeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Ice, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppleBlue,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Carbon,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Graphite,
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    subtitle: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) AppleBlue else Mist.copy(alpha = 0.35f)
    val containerColor = if (selected) Ice else Color.White

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Carbon,
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(Spacing.xs))
                        StatusChip(
                            text = badge,
                            background = AppleBlue,
                            contentColor = Ice,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Carbon,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Graphite,
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (selected) AppleBlue else Pebble,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Ice,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
