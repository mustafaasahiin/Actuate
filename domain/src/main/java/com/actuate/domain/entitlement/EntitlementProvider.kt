package com.actuate.domain.entitlement

import kotlinx.coroutines.flow.Flow

/**
 * Decides whether the user is on the Pro tier.
 *
 * v1 ships [LocalQuotaEntitlementProvider] (free tier, 3 actions/week).
 * A RevenueCat-backed provider is prepared in the data layer and activated
 * when a RevenueCat project is configured.
 */
interface EntitlementProvider {
    val displayName: String
    fun observeIsPro(): Flow<Boolean>
    suspend fun isPro(): Boolean
}