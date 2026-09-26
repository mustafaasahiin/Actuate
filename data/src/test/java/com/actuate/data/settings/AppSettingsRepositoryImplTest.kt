package com.actuate.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppSettingsRepositoryImplTest {

    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var secretStore: InMemorySecretStore
    private lateinit var repository: AppSettingsRepositoryImpl

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        secretStore = InMemorySecretStore()
        repository = AppSettingsRepositoryImpl(dataStore, secretStore)
    }

    @Test
    fun hasSeenOnboardingDefaultsToFalse() = runTest {
        val hasSeen = repository.hasSeenOnboarding.first()
        assertFalse(hasSeen)
    }

    @Test
    fun setHasSeenOnboardingUpdatesFlow() = runTest {
        repository.setHasSeenOnboarding(true)
        val hasSeen = repository.hasSeenOnboarding.first()
        assertTrue(hasSeen)
    }

    @Test
    fun serverConfigSavesAndReadsTokensAndUrl() = runTest {
        val config = ServerConfig(
            baseUrl = "https://custom.actuate.ai",
            userId = "usr-42",
            token = "secret-token",
        )
        repository.saveServerConfig(config)

        val read = repository.readServerConfig()
        assertEquals("https://custom.actuate.ai", read.baseUrl)
        assertEquals("usr-42", read.userId)
        assertEquals("secret-token", read.token)
    }

    private class InMemorySecretStore : SecretStore {
        private val secrets = mutableMapOf<String, String>()

        override fun save(key: String, value: String) {
            secrets[key] = value
        }

        override fun read(key: String): String? = secrets[key]

        override fun delete(key: String) {
            secrets.remove(key)
        }
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
