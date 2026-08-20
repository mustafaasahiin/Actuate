package com.actuate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.domain.entitlement.EntitlementProvider
import com.actuate.domain.repository.AppSettingsRepository
import com.actuate.domain.repository.HistoryRepository
import com.actuate.domain.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isPro: Boolean = false,
    val planName: String = "Free",
    val serverStatus: String? = null,
    val clearing: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val historyRepository: HistoryRepository,
    private val entitlementProvider: EntitlementProvider,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val config = settingsRepository.readServerConfig()
            val isPro = entitlementProvider.isPro()
            _state.value = SettingsUiState(
                isPro = isPro,
                planName = entitlementProvider.displayName,
            )
            if (config.token.isNotBlank()) {
                val result = serverRepository.me()
                val session = result.getOrNull()
                val quotaText = session?.quotaRemaining?.let { remaining -> "${remaining} left this week" }
                    ?: "unlimited (Pro)"
                _state.value = _state.value.copy(
                    serverStatus = if (result.isSuccess) {
                        "Connected · $quotaText"
                    } else {
                        "Offline — " + (result.exceptionOrNull()?.message ?: "server error")
                    },
                )
            } else {
                _state.value = _state.value.copy(serverStatus = "Registering…")
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(clearing = true)
            historyRepository.clear()
            _state.value = _state.value.copy(clearing = false)
        }
    }
}