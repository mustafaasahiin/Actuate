package com.actuate.domain.model

/** Where an action landed after execution. */
enum class Destination { CALENDAR, NOTION, REMINDERS, LOCAL, NONE }

/** Outcome of executing a single [com.actuate.domain.model.ParsedAction]. */
data class ExecutionResult(
    val actionId: String,
    val destination: Destination,
    val success: Boolean,
    val message: String,
)

/** Outcome of a full voice round-trip (transcript -> parse -> execute). */
data class VoiceRunResult(
    val transcript: String,
    val executed: List<ExecutionResult>,
    val remainingQuota: Int?,
)