package com.actuate.domain.usecase

import java.time.Instant

/**
 * Pure free-tier quota math: a rolling 7-day window with a fixed
 * weekly allowance. Kept free of Android dependencies so it is unit-testable.
 */
object QuotaPolicy {

    const val WEEKLY_ALLOWANCE = 3

    /** Timestamps of consumed actions, most recent last. */
    fun remaining(timestamps: List<Instant>, now: Instant): Int {
        val windowStart = now.minusSeconds(WEEK_SECONDS)
        val inWindow = timestamps.count { it.isAfter(windowStart) }
        return (WEEKLY_ALLOWANCE - inWindow).coerceAtLeast(0)
    }

    /** True when consuming [count] more actions stays within the allowance. */
    fun canConsume(timestamps: List<Instant>, now: Instant, count: Int = 1): Boolean =
        remaining(timestamps, now) >= count

    private const val WEEK_SECONDS = 7L * 24 * 60 * 60
}