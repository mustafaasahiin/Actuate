package com.actuate.data.entitlement

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.actuate.domain.repository.QuotaRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalQuotaEntitlementProviderTest {

    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var quotaRepository: QuotaRepository
    private lateinit var entitlementProvider: LocalQuotaEntitlementProvider

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        quotaRepository = mockk(relaxed = true)
        entitlementProvider = LocalQuotaEntitlementProvider(dataStore, quotaRepository)
    }

    @Test
    fun isProDefaultsToFalse() = runTest {
        assertFalse(entitlementProvider.isPro())
        assertFalse(entitlementProvider.observeIsPro().first())
    }

    @Test
    fun setProTruePersistsAndUpdatesFlow() = runTest {
        entitlementProvider.setPro(true)
        assertTrue(entitlementProvider.isPro())
        assertTrue(entitlementProvider.observeIsPro().first())
    }

    @Test
    fun setProFalseUpdatesBackToFalse() = runTest {
        entitlementProvider.setPro(true)
        assertTrue(entitlementProvider.isPro())

        entitlementProvider.setPro(false)
        assertFalse(entitlementProvider.isPro())
        assertFalse(entitlementProvider.observeIsPro().first())
    }

    @Test
    fun displayNameIsFree() {
        assertEquals("Free", entitlementProvider.displayName)
    }

    private class InMemoryPreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
