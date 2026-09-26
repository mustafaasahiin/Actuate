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
    val latencyMs: Long? = null,
    val checkingServer: Boolean = false,
    val clearing: Boolean = false,
    val quotaRemaining: Int? = null,
    val emailConnected: Boolean = false,
    val whatsappConnected: Boolean = false,
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
            entitlementProvider.observeIsPro().collect { isPro ->
                _state.value = _state.value.copy(
                    isPro = isPro,
                    planName = if (isPro) "Actuate Pro · Unlimited" else entitlementProvider.displayName,
                )
            }
        }
        checkServerStatus()
        refreshConnections()
    }

    /** Pulls the mail/WhatsApp connection status for the Messaging cell. */
    private fun refreshConnections() {
        viewModelScope.launch {
            serverRepository.integrationsStatus()
                .onSuccess { status ->
                    _state.value = _state.value.copy(
                        emailConnected = status.email.connected,
                        whatsappConnected = status.whatsapp.connected,
                    )
                }
        }
    }

    fun checkServerStatus() {
        viewModelScope.launch {
            _state.value = _state.value.copy(checkingServer = true)
            val config = settingsRepository.readServerConfig()
            if (config.token.isNotBlank()) {
                val start = System.currentTimeMillis()
                val result = serverRepository.me()
                val latency = System.currentTimeMillis() - start
                val session = result.getOrNull()
                val quotaRemaining = session?.quotaRemaining
                val quotaText = quotaRemaining?.let { remaining -> "${remaining} left this week" }
                    ?: "unlimited (Pro)"
                _state.value = _state.value.copy(
                    serverStatus = if (result.isSuccess) {
                        "Connected · $quotaText"
                    } else {
                        "Offline — " + (result.exceptionOrNull()?.message ?: "server error")
                    },
                    latencyMs = if (result.isSuccess) latency else null,
                    quotaRemaining = quotaRemaining,
                    checkingServer = false,
                )
            refreshConnections()
            } else {
                _state.value = _state.value.copy(serverStatus = "Registering…", checkingServer = false)
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

    suspend fun upgradeToPro(promoCode: String = "") {
        entitlementProvider.setPro(true)
        runCatching {
            serverRepository.syncEntitlement(isPro = true, promoCode = promoCode)
        }
    }

    fun setPro(isPro: Boolean) {
        viewModelScope.launch {
            entitlementProvider.setPro(isPro)
        }
    }
}
