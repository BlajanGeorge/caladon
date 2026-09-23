package com.caladon.worlds.rules

import java.time.Duration
import java.time.Instant
import kotlin.math.floor

/**
 * The lazy, whole-unit settlement of one resource (ARCHITECTURE.md → Resources → Production model):
 * credit `floor(rate × elapsed)` units and move the production clock forward by exactly the time those
 * units took, so the unearned fraction stays as time, never as a decimal. At capacity the clock resets.
 */
object Production {
    data class Settled(val stock: Long, val settledAt: Instant)

    fun settle(stock: Long, ratePerHour: Double, capacity: Long, settledAt: Instant, now: Instant): Settled {
        if (!now.isAfter(settledAt)) return Settled(stock, settledAt)
        if (stock >= capacity) return Settled(stock, now)
        if (ratePerHour <= 0.0) return Settled(stock, now)
        val elapsedMs = Duration.between(settledAt, now).toMillis()
        val earned = floor(ratePerHour * elapsedMs / 3_600_000.0).toLong()
        val next = stock + earned
        if (next >= capacity) return Settled(capacity, now)
        val consumedMs = floor(earned * 3_600_000.0 / ratePerHour).toLong()
        return Settled(next, settledAt.plusMillis(consumedMs))
    }
}
