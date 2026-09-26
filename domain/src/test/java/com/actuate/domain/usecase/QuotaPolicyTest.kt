package com.actuate.domain.usecase

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaPolicyTest {

    private val now: Instant = Instant.parse("2026-08-19T10:00:00Z")
    private val hour: Long = 3600
    private val weekSeconds: Long = 7 * 24 * 3600

    @Test
    fun `starts with full allowance`() {
        assertEquals(20, QuotaPolicy.remaining(emptyList(), now))
    }

    @Test
    fun `counts only actions inside the rolling window`() {
        val timestamps = listOf(
            now.minusSeconds(2 * hour), // in window
            now.minusSeconds(3 * hour), // in window
        )
        assertEquals(18, QuotaPolicy.remaining(timestamps, now))
    }

    @Test
    fun `drops actions older than seven days`() {
        val timestamps = listOf(now.minusSeconds(weekSeconds + 1))
        assertEquals(20, QuotaPolicy.remaining(timestamps, now))
    }

    @Test
    fun `canConsume respects the allowance`() {
        val timestamps = List(20) { now.minusSeconds((it + 1) * hour) }
        assertFalse(QuotaPolicy.canConsume(timestamps, now))
        assertTrue(QuotaPolicy.canConsume(timestamps, now, count = 0))
    }

    @Test
    fun `two actions require two remaining slots`() {
        val timestamps = List(19) { now.minusSeconds((it + 1) * hour) }
        assertTrue(QuotaPolicy.canConsume(timestamps, now, count = 1))
        assertFalse(QuotaPolicy.canConsume(timestamps, now, count = 2))
    }

    @Test
    fun `an action exactly seven days old is expired`() {
        val timestamps = listOf(now.minusSeconds(weekSeconds))
        assertEquals(20, QuotaPolicy.remaining(timestamps, now))
    }

    @Test
    fun `an action just inside the window still counts`() {
        val timestamps = listOf(now.minusSeconds(weekSeconds - 1))
        assertEquals(19, QuotaPolicy.remaining(timestamps, now))
    }

    @Test
    fun `never goes negative`() {
        val timestamps = List(25) { now.minusSeconds(it * hour) }
        assertEquals(0, QuotaPolicy.remaining(timestamps, now))
    }
}