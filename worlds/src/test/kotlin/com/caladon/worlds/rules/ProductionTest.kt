package com.caladon.worlds.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ProductionTest {
    private val t0 = Instant.parse("2026-09-22T19:00:00Z")

    @Test
    fun `credits only whole units and moves the clock by exactly the time they took`() {
        // 30/h = one unit every 2 minutes. After 3 minutes: +1 unit, clock at +2 min.
        val s = Production.settle(500, 30.0, 1000, t0, t0.plus(Duration.ofMinutes(3)))
        assertThat(s.stock).isEqualTo(501)
        assertThat(s.settledAt).isEqualTo(t0.plus(Duration.ofMinutes(2)))
        // Settling again 1 minute later completes the second unit: no unit is ever lost or doubled.
        val s2 = Production.settle(s.stock, 30.0, 1000, s.settledAt, t0.plus(Duration.ofMinutes(4)))
        assertThat(s2.stock).isEqualTo(502)
        assertThat(s2.settledAt).isEqualTo(t0.plus(Duration.ofMinutes(4)))
    }

    @Test
    fun `frequent settlement yields the same total as one settlement`() {
        var stock = 500L; var at = t0
        for (i in 1..120) { // every 30 s for an hour at 30/h
            val s = Production.settle(stock, 30.0, 1000, at, t0.plusSeconds(30L * i)); stock = s.stock; at = s.settledAt
        }
        assertThat(stock).isEqualTo(530)
        assertThat(Production.settle(500, 30.0, 1000, t0, t0.plus(Duration.ofHours(1))).stock).isEqualTo(530)
    }

    @Test
    fun `caps at capacity and resets the clock there`() {
        val s = Production.settle(990, 30.0, 1000, t0, t0.plus(Duration.ofHours(1)))
        assertThat(s.stock).isEqualTo(1000)
        assertThat(s.settledAt).isEqualTo(t0.plus(Duration.ofHours(1)))
        val full = Production.settle(1000, 30.0, 1000, t0, t0.plus(Duration.ofMinutes(5)))
        assertThat(full.stock).isEqualTo(1000)
        assertThat(full.settledAt).isEqualTo(t0.plus(Duration.ofMinutes(5)))
    }

    @Test
    fun `never lowers a stock or moves the clock backwards`() {
        val s = Production.settle(700, 30.0, 1000, t0, t0.minusSeconds(5))
        assertThat(s.stock).isEqualTo(700)
        assertThat(s.settledAt).isEqualTo(t0)
        assertThat(Production.settle(700, 30.0, 1000, t0, t0.plusSeconds(10)).stock).isEqualTo(700)
    }

    @Test
    fun `high rates settle many units per minute`() {
        val s = Production.settle(0, 2400.0, 400_000, t0, t0.plus(Duration.ofMinutes(1)))
        assertThat(s.stock).isEqualTo(40)
        assertThat(s.settledAt).isEqualTo(t0.plus(Duration.ofMinutes(1)))
    }
}
