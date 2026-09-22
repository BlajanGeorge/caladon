package com.caladon.worlds.resources

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** The lazy accrual rule: `stock = min(capacity, stock + rate * elapsedMinutes)`, fractional part kept. */
object Settlement {
    private const val SCALE = 3

    fun elapsedMinutes(settledAt: Instant, now: Instant): Double {
        val millis = Duration.between(settledAt, now).toMillis()
        return if (millis <= 0) 0.0 else millis / 60_000.0
    }

    fun settle(stock: BigDecimal, ratePerMinute: Double, capacity: Int, elapsedMinutes: Double): BigDecimal {
        val produced = BigDecimal(ratePerMinute * elapsedMinutes).setScale(SCALE, RoundingMode.HALF_UP)
        val cap = BigDecimal(capacity)
        val next = stock.setScale(SCALE, RoundingMode.HALF_UP) + produced
        return if (next > cap) cap.setScale(SCALE) else next
    }
}
