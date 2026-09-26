package com.actuate.domain.model

import java.time.Instant

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
    val at: Instant? = null,
    val end: Instant? = null,
    val location: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val description: String? = null,
    val pendingSync: Boolean = false,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    constructor(
        id: String,
        type: String,
        title: String,
        at: Instant?,
        done: Boolean,
        createdAt: Long,
    ) : this(
        id = id,
        type = type,
        title = title,
        at = at,
        end = null,
        location = null,
        attendees = emptyList(),
        description = null,
        pendingSync = false,
        done = done,
        createdAt = createdAt,
    )
}
