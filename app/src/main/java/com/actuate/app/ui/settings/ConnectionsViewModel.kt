package com.actuate.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.actuate.domain.model.ChannelConnection
import com.actuate.domain.model.IntegrationsStatus
import com.actuate.domain.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Outbound messaging channels a user can connect for cancellation messages. */
enum class Channel(val id: String, val label: String) {
    EMAIL("email", "Email"),
    WHATSAPP("whatsapp", "WhatsApp");

    companion object {
        fun from(id: String): Channel? = entries.firstOrNull { it.id == id }
    }
}

data class ConnectionsUiState(
    val loading: Boolean = true,
    val status: IntegrationsStatus = IntegrationsStatus(),
    /** Channel currently submitting credentials (connect in flight). */
    val connecting: Channel? = null,
    /** Channel currently running a live test send. */
    val testing: Channel? = null,
    /** Channel currently disconnecting. */
    val disconnecting: Channel? = null,
    /** Stored credentials exist but can no longer be decrypted (key rotated / tampering). */
    val corruptedChannel: Channel? = null,
    /** Transient success message shown at the top of the screen. */
    val banner: String? = null,
    /** Error message for the last failed operation. */
    val error: String? = null,
) {
    fun connection(channel: Channel): ChannelConnection = status.connection(channel)
}

fun IntegrationsStatus.connection(channel: Channel): ChannelConnection = when (channel) {
    Channel.EMAIL -> email
    Channel.WHATSAPP -> whatsapp
}

/**
 * Owns the mail/WhatsApp connection lifecycle: status polling, connect flows,
 * live test sends, and disconnects. All network work goes through
 * [ServerRepository]; nothing is faked or stored locally — the server holds
 * the (encrypted) credentials and is the single source of truth.
 */
class ConnectionsViewModel(
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state

    init {
        refresh()
    }

    /** Reloads connection status from the server. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            serverRepository.integrationsStatus()
                .onSuccess { status ->
                    _state.value = _state.value.copy(
                        loading = false,
                        status = status,
                        corruptedChannel = Channel.entries.firstOrNull { status.connection(it).corrupted },
                        error = null,
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = err.message ?: "Couldn't reach the server",
                    )
                }
        }
    }

    fun connectEmail(host: String, port: Int, user: String, password: String) {
        submit(Channel.EMAIL, key = { it.copy(connecting = Channel.EMAIL) }, successBanner = "Email connected & verified") {
            require(host.isNotBlank()) { "SMTP host is required" }
            require(user.isNotBlank()) { "Email username is required" }
            require(password.length >= 8) { "Password must be at least 8 characters" }
            serverRepository.connectEmail(host = host, port = port, user = user, password = password)
        }
    }

    fun connectWhatsApp(phoneNumberId: String, accessToken: String) {
        submit(Channel.WHATSAPP, key = { it.copy(connecting = Channel.WHATSAPP) }, successBanner = "WhatsApp connected & verified") {
            require(phoneNumberId.isNotBlank()) { "Phone number ID is required" }
            require(accessToken.isNotBlank()) { "Access token is required" }
            serverRepository.connectWhatsApp(phoneNumberId = phoneNumberId, accessToken = accessToken)
        }
    }

    /** Sends a test message through the connected channel. */
    fun sendTest(channel: Channel, recipient: String) {
        submit(channel, key = { it.copy(testing = channel) }, successBanner = "Test message sent via ${channel.label}") {
            require(recipient.isNotBlank()) { "Recipient is required" }
            serverRepository.testChannel(channel.id, recipient)
        }
    }

    fun disconnect(channel: Channel) {
        submit(channel, key = { it.copy(disconnecting = channel) }, successBanner = "${channel.label} disconnected") {
            serverRepository.disconnectChannel(channel.id)
        }
    }

    fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun dismissCorrupted() {
        _state.value = _state.value.copy(corruptedChannel = null)
    }

    private fun submit(
        channel: Channel,
        key: (ConnectionsUiState) -> ConnectionsUiState,
        successBanner: String,
        block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _state.value = key(_state.value).copy(error = null, banner = null)
            runCatching { block().getOrThrow() }
                .onSuccess {
                    // Re-pull status so Connected/Verified badges reflect the server.
                    val fresh = serverRepository.integrationsStatus().getOrNull()
                    _state.value = _state.value.copy(
                        connecting = null,
                        testing = null,
                        disconnecting = null,
                        status = fresh ?: _state.value.status,
                        corruptedChannel = fresh?.let { s ->
                            Channel.entries.firstOrNull { s.connection(it).corrupted }
                        },
                        banner = successBanner,
                    )
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(
                        connecting = null,
                        testing = null,
                        disconnecting = null,
                        error = friendlyError(err),
                    )
                }
        }
    }

    private fun friendlyError(err: Throwable): String {
        val raw = err.message ?: "Something went wrong"
        return when {
            raw.contains("smtp_verify_failed", ignoreCase = true) ->
                "Those SMTP credentials were rejected. Check host, username and app password."
            raw.contains("whatsapp_verify_failed", ignoreCase = true) ->
                "WhatsApp verification failed. Check the phone number ID and access token."
            else -> raw
        }
    }
}
