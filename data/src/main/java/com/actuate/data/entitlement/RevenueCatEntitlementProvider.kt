package com.actuate.data.entitlement

import android.app.Activity
import android.content.Context
import com.actuate.domain.entitlement.EntitlementProvider
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class RevenueCatEntitlementProvider(
    private val appContext: Context,
    private val fallback: LocalQuotaEntitlementProvider,
    private val apiKeyProvider: () -> String = { "" },
    private val appUserIdProvider: () -> String = { "" },
) : EntitlementProvider {

    private val isConfigured: Boolean
    private val _proState = MutableStateFlow(false)
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var _cachedOffering: Offering? = null

    private val prefs by lazy {
        appContext.getSharedPreferences("actuate_revenuecat_cache", Context.MODE_PRIVATE)
    }

    fun getCachedOffering(): Offering? = _cachedOffering

    fun getCachedPrices(): Map<String, String> {
        val annual = prefs.getString("annual_price", "$29.99 / year") ?: "$29.99 / year"
        val monthly = prefs.getString("monthly_price", "$4.99 / month") ?: "$4.99 / month"
        val lifetime = prefs.getString("lifetime_price", "$79.99 one-time") ?: "$79.99 one-time"
        return mapOf(
            "annual" to annual,
            "monthly" to monthly,
            "lifetime" to lifetime,
        )
    }

    fun cacheOffering(offering: Offering) {
        _cachedOffering = offering
        runCatching {
            val editor = prefs.edit()
            val annualPkg = offering.annual ?: offering.availablePackages.firstOrNull {
                it.identifier == "\$rc_annual" ||
                    it.identifier.contains("annual", ignoreCase = true) ||
                    it.identifier.contains("year", ignoreCase = true) ||
                    it.product.id.contains("annual", ignoreCase = true) ||
                    it.product.id.contains("year", ignoreCase = true)
            }
            annualPkg?.product?.price?.formatted?.let {
                val formatted = if (it.contains("/")) it else "$it / year"
                editor.putString("annual_price", formatted)
            }
            val monthlyPkg = offering.monthly ?: offering.availablePackages.firstOrNull {
                it.identifier == "\$rc_monthly" ||
                    it.identifier.contains("month", ignoreCase = true) ||
                    it.product.id.contains("month", ignoreCase = true)
            }
            monthlyPkg?.product?.price?.formatted?.let {
                val formatted = if (it.contains("/")) it else "$it / month"
                editor.putString("monthly_price", formatted)
            }
            val lifetimePkg = offering.lifetime ?: offering.availablePackages.firstOrNull {
                it.identifier == "\$rc_lifetime" ||
                    it.identifier.contains("life", ignoreCase = true) ||
                    it.product.id.contains("life", ignoreCase = true)
            }
            lifetimePkg?.product?.price?.formatted?.let {
                editor.putString("lifetime_price", it)
            }
            editor.apply()
        }
    }

    init {
        val apiKey = apiKeyProvider().trim()
        val userId = appUserIdProvider().trim()
        if (!Purchases.isConfigured && apiKey.isNotBlank()) {
            runCatching {
                val builder = PurchasesConfiguration.Builder(appContext, apiKey)
                if (userId.isNotBlank()) {
                    builder.appUserID(userId)
                }
                Purchases.configure(builder.build())
            }
        }
        isConfigured = Purchases.isConfigured
        if (isConfigured) {
            if (userId.isNotBlank()) {
                runCatching {
                    Purchases.sharedInstance.logIn(userId)
                }
            }
            runCatching {
                Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
                    checkProEntitlement(customerInfo)
                }

                Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        checkProEntitlement(customerInfo)
                    }

                    override fun onError(error: PurchasesError) {
                    }
                })
            }
            runCatching {
                Purchases.sharedInstance.getOfferingsWith(
                    onError = {},
                    onSuccess = { offerings ->
                        val resolved = offerings.getOffering("default")
                            ?: offerings["default"]
                            ?: offerings.all.entries.firstOrNull { it.key.equals("default", ignoreCase = true) }?.value
                            ?: offerings.current
                            ?: offerings.getOffering("pro")
                            ?: offerings.getOffering("active")
                            ?: offerings.all.values.firstOrNull { it.availablePackages.isNotEmpty() }
                            ?: offerings.all.values.firstOrNull()
                        if (resolved != null) {
                            cacheOffering(resolved)
                        }
                    }
                )
            }
        }
    }

    fun fetchDefaultOffering(
        onSuccess: (Offering) -> Unit,
        onError: (PurchasesError) -> Unit = {},
    ) {
        if (!isConfigured || !Purchases.isConfigured) {
            onError(PurchasesError(PurchasesErrorCode.StoreProblemError, "RevenueCat is not configured"))
            return
        }
        try {
            Purchases.sharedInstance.getOfferingsWith(
                onError = { error -> onError(error) },
                onSuccess = { offerings ->
                    val resolved = offerings.getOffering("default")
                        ?: offerings["default"]
                        ?: offerings.all.entries.firstOrNull { it.key.equals("default", ignoreCase = true) }?.value
                        ?: offerings.current
                        ?: offerings.getOffering("pro")
                        ?: offerings.getOffering("active")
                        ?: offerings.all.values.firstOrNull { it.availablePackages.isNotEmpty() }
                        ?: offerings.all.values.firstOrNull()
                    if (resolved != null) {
                        cacheOffering(resolved)
                        onSuccess(resolved)
                    } else {
                        onError(PurchasesError(PurchasesErrorCode.ConfigurationError, "No default offering found in RevenueCat"))
                    }
                }
            )
        } catch (e: Exception) {
            onError(PurchasesError(PurchasesErrorCode.StoreProblemError, e.message ?: "Failed to fetch offerings"))
        }
    }

    override val displayName: String = if (isConfigured && Purchases.isConfigured) "RevenueCat" else fallback.displayName

    override fun observeIsPro(): Flow<Boolean> =
        combine(_proState, fallback.observeIsPro()) { rcPro, fallbackPro ->
            rcPro || fallbackPro
        }

    override suspend fun isPro(): Boolean {
        if (fallback.isPro()) {
            _proState.value = true
            return true
        }
        if (!isConfigured || !Purchases.isConfigured) {
            return false
        }
        return try {
            suspendCancellableCoroutine { continuation ->
                Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                    override fun onReceived(customerInfo: CustomerInfo) {
                        val active = checkProEntitlement(customerInfo)
                        continuation.resume(active)
                    }

                    override fun onError(error: PurchasesError) {
                        continuation.resume(_proState.value)
                    }
                })
            }
        } catch (e: Exception) {
            fallback.isPro()
        }
    }

    override suspend fun setPro(isPro: Boolean) {
        fallback.setPro(isPro)
        _proState.value = isPro
    }

    fun isReady(): Boolean = isConfigured && Purchases.isConfigured

    fun purchasePackage(
        activity: Activity,
        packageToPurchase: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (PurchasesError, Boolean) -> Unit,
    ) {
        if (!isConfigured || !Purchases.isConfigured) {
            onError(PurchasesError(com.revenuecat.purchases.PurchasesErrorCode.StoreProblemError, "RevenueCat is not configured"), false)
            return
        }

        try {
            val params = PurchaseParams.Builder(activity, packageToPurchase).build()
            Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    val active = checkProEntitlement(customerInfo)
                    _proState.value = active
                    scope.launch {
                        fallback.setPro(true)
                    }
                    onSuccess(customerInfo)
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    onError(error, userCancelled)
                }
            })
        } catch (e: Exception) {
            onError(PurchasesError(com.revenuecat.purchases.PurchasesErrorCode.StoreProblemError, e.message ?: "Failed to initiate purchase"), false)
        }
    }

    fun checkProEntitlement(info: CustomerInfo): Boolean {
        val hasActiveNamedEntitlement = runCatching {
            info.entitlements["pro"]?.isActive == true ||
                info.entitlements["pro_access"]?.isActive == true ||
                info.entitlements["pro_monthly"]?.isActive == true ||
                info.entitlements["pro_yearly"]?.isActive == true
        }.getOrDefault(false)

        val hasExtraNamedEntitlement = runCatching {
            info.entitlements["pro_annual"]?.isActive == true ||
                info.entitlements["premium"]?.isActive == true
        }.getOrDefault(false)

        val hasAnyActiveEntitlement = runCatching {
            info.entitlements.all.values.any { it.isActive }
        }.getOrDefault(false)

        val hasActiveSubscription = runCatching {
            info.activeSubscriptions.isNotEmpty()
        }.getOrDefault(false)

        val active = hasActiveNamedEntitlement || hasExtraNamedEntitlement || hasAnyActiveEntitlement || hasActiveSubscription
        if (active) {
            _proState.value = true
            scope.launch {
                fallback.setPro(true)
            }
        }
        return active
    }
}
