package com.actuate.data.entitlement

import com.actuate.domain.entitlement.EntitlementProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * RevenueCat adapter, prepared but dormant.
 *
 * Activation steps when a RevenueCat project exists:
 *  1. Add `maven { url = uri("https://repo.revenuecat.com") }` to
 *     dependencyResolutionManagement in settings.gradle.kts.
 *  2. Uncomment `revenuecat` in gradle/libs.versions.toml and add
 *     `implementation(libs.revenuecat)` to app/build.gradle.kts.
 *  3. Put your public SDK key in app/src/main/res/values/revenuecat.xml:
 *     <string name="revenuecat_public_key">appl_...</string>
 *  4. Set `org.gradle.project.revenuecat.enabled=true` in gradle.properties.
 *  5. In AppConfig (data module), instantiate Purchases.configure and switch
 *     the DI binding from LocalQuotaEntitlementProvider to this provider.
 *
 * The free/Pro products map to entitlements "pro_monthly" ($4.99/mo) and
 * "pro_yearly" ($29.99/yr) as configured in your RevenueCat dashboard.
 */
class RevenueCatEntitlementProvider : EntitlementProvider {

    private val isPro = MutableStateFlow(false)

    override val displayName: String = "RevenueCat"

    override fun observeIsPro(): Flow<Boolean> = isPro

    override suspend fun isPro(): Boolean = isPro.value

    /*
    // Reference implementation (requires the purchases-android SDK):
    class RevenueCatEntitlementProvider(
        private val appContext: Context,
    ) : EntitlementProvider {
        init {
            Purchases.configure(
                PurchasesConfiguration.Builder(
                    appContext,
                    appContext.getString(R.string.revenuecat_public_key),
                ).build(),
            )
        }

        override fun observeIsPro(): Flow<Boolean> = callbackFlow {
            val listener = CustomerInfoUpdateListener { info ->
                trySend(isPro(info))
            }
            Purchases.sharedInstance.customerInfoUpdateListener = listener
            Purchases.sharedInstance.getCustomerInfo { info, _ ->
                info?.let { trySend(isPro(it)) }
            }
            awaitClose { Purchases.sharedInstance.customerInfoUpdateListener = null }
        }

        override suspend fun isPro(): Boolean =
            suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.getCustomerInfo { info, _ ->
                    cont.resume(info?.let(::isPro) ?: false)
                }
            }

        private fun isPro(info: CustomerInfo): Boolean =
            info.entitlements.active.containsKey("pro")
    }
     */
}