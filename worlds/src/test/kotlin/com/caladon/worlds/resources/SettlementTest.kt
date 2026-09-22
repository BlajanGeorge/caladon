package com.caladon.worlds.resources

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class SettlementTest {
    private val t0 = Instant.parse("2026-09-22T19:00:00Z")

    @Test
    fun `elapsed minutes are fractional and never negative`() {
        assertThat(Settlement.elapsedMinutes(t0, t0.plusSeconds(90))).isEqualTo(1.5)
        assertThat(Settlement.elapsedMinutes(t0, t0.minusSeconds(5))).isEqualTo(0.0)
    }

    @Test
    fun `accrues rate times elapsed minutes and keeps the fractional part`() {
        val next = Settlement.settle(BigDecimal("500"), 30.0, 2000, 1.5)
        assertThat(next).isEqualByComparingTo("545.000")
        val carry = Settlement.settle(BigDecimal("500.250"), 30.0, 2000, 0.01) // +0.3
        assertThat(carry).isEqualByComparingTo("500.550")
        assertThat(carry.scale()).isEqualTo(3)
    }

    @Test
    fun `is capped at capacity and never lowers a stock`() {
        assertThat(Settlement.settle(BigDecimal("1990"), 30.0, 2000, 10.0)).isEqualByComparingTo("2000")
        assertThat(Settlement.settle(BigDecimal("2000"), 30.0, 2000, 1.0)).isEqualByComparingTo("2000")
        assertThat(Settlement.settle(BigDecimal("700"), 30.0, 2000, 0.0)).isEqualByComparingTo("700")
    }
}
