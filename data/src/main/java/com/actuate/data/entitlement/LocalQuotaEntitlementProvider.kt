package com.actuate.data.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.actuate.data.store.actuateDataStore
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.repository.QuotaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * v1 entitlement: users start on the free tier; Pro status can be unlocked
 * via promo/judge pass or subscription and persists in DataStore.
 * Swap for [RevenueCatEntitlementProvider] once a RevenueCat project exists.
 */
class LocalQuotaEntitlementProvider(
    private val dataStore: DataStore<Preferences>,
    private val quotaRepository: QuotaRepository,
) : EntitlementProvider {

    constructor(context: Context, quotaRepository: QuotaRepository) : this(
        dataStore = context.actuateDataStore,
        quotaRepository = quotaRepository,
    )

    override val displayName: String = "Free"

    override fun observeIsPro(): Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_IS_PRO] ?: false
        }

    override suspend fun isPro(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[KEY_IS_PRO] ?: false
    }

    override suspend fun setPro(isPro: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_PRO] = isPro
        }
    }

    companion object {
        val KEY_IS_PRO = booleanPreferencesKey("is_pro")
    }
}
