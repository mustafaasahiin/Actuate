package com.actuate.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.actuate.data.store.actuateDataStore
import com.actuate.domain.model.ServerConfig
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val secretStore: SecretStore,
) : AppSettingsRepository {

    constructor(context: Context, secretStore: SecretStore) : this(context.actuateDataStore, secretStore)

    override val serverConfig: Flow<ServerConfig> =
        dataStore.data.map { prefs ->
            ServerConfig(
                baseUrl = prefs[SERVER_BASE_URL] ?: ServerConfig.DEFAULT_BASE_URL,
                userId = secretStore.read(KEY_SERVER_USER_ID) ?: "",
                token = secretStore.read(KEY_SERVER_TOKEN) ?: "",
            )
        }

    override suspend fun readServerConfig(): ServerConfig = serverConfig.first()

    override suspend fun saveServerConfig(config: ServerConfig) {
        secretStore.save(KEY_SERVER_USER_ID, config.userId)
        secretStore.save(KEY_SERVER_TOKEN, config.token)
        dataStore.edit { prefs ->
            prefs[SERVER_BASE_URL] = config.baseUrl
        }
    }

    override val hasSeenOnboarding: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
        }

    override suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    companion object {
        private const val KEY_SERVER_USER_ID = "server_user_id"
        private const val KEY_SERVER_TOKEN = "server_token"
        private val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }
}