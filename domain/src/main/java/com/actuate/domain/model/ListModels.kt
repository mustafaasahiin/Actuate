package com.actuate.domain.model

/** One user list (e.g. "shopping", "general") returned by the server. */
data class ServerList(
    val name: String,
    val items: List<ServerListItem>,
)

/** A single list item with its done state. */
data class ServerListItem(
    val id: String,
    val text: String,
    val list: String,
    val done: Boolean,
    val createdAt: Long,
)

/** An executed action from server history, used by the Today section. */
data class ServerActionItem(
    val id: String,
    val type: String,
    val title: String,
    val at: java.time.Instant?,
    val done: Boolean,
    val createdAt: Long,
)