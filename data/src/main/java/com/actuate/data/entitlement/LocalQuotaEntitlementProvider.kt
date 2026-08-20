package com.actuate.data.entitlement

import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.repository.QuotaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * v1 entitlement: everyone is on the free tier; Pro is enforced by the
 * 3-actions-per-week rolling quota. Swap for [RevenueCatEntitlementProvider]
 * once a RevenueCat project exists.
 */
class LocalQuotaEntitlementProvider(
    private val quotaRepository: QuotaRepository,
) : EntitlementProvider {

    override val displayName: String = "Free"

    override fun observeIsPro(): Flow<Boolean> =
        quotaRepository.observeRemaining().map { false }

    override suspend fun isPro(): Boolean = false
}