package com.actuate.domain.model

import java.time.Instant

enum class ActionStatus { PENDING, DONE, FAILED, SKIPPED }

/** A persisted record of one executed action. */
data class ActionRecord(
    val id: String,
    val timestamp: Instant,
    val transcript: String,
    val summary: String,
    val actionType: String,
    val status: ActionStatus,
    val message: String = "",
    val destination: Destination = Destination.NONE,
)