package com.actuate.domain.model

/** Connection state for one outbound messaging channel (email or WhatsApp). */
data class ChannelConnection(
    val channel: String,
    val connected: Boolean = false,
    val verified: Boolean = false,
    val connectedAt: Long? = null,
    val hint: String = "",
    val corrupted: Boolean = false,
)

/** Status of both outbound channels used for cancellation messages. */
data class IntegrationsStatus(
    val email: ChannelConnection = ChannelConnection(channel = "email"),
    val whatsapp: ChannelConnection = ChannelConnection(channel = "whatsapp"),
) {
    val anyConnected: Boolean
        get() = email.connected || whatsapp.connected
}
