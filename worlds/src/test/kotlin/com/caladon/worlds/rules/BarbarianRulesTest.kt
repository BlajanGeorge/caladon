package com.caladon.worlds.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class BarbarianRulesTest {

    private val t0: Instant = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `a village defends with its level's brigands at twenty each`() {
        assertThat(BarbarianRules.brigands(1)).isEqualTo(100)
        assertThat(BarbarianRules.brigands(2)).isEqualTo(200)
        assertThat(BarbarianRules.brigands(3)).isEqualTo(300)
        assertThat(BarbarianRules.defence(1)).isEqualTo(2000.0)
        assertThat(BarbarianRules.defence(3)).isEqualTo(6000.0)
        // A village hardens no further than three, and a level beyond the table is read as the last one.
        assertThat(BarbarianRules.hardened(1)).isEqualTo(2)
        assertThat(BarbarianRules.hardened(3)).isEqualTo(3)
        assertThat(BarbarianRules.brigands(4)).isEqualTo(300)
    }

    @Test
    fun `the store holds a thousand a level and refills a hundred a level an hour`() {
        assertThat(BarbarianRules.ceiling(1)).isEqualTo(1000)
        assertThat(BarbarianRules.ceiling(3)).isEqualTo(3000)
        assertThat(BarbarianRules.refillPerHour(2)).isEqualTo(200.0)

        // Half an hour at level 1 is fifty whole units, and the clock moves by exactly what they took.
        val half = BarbarianRules.settle(BarbarianRules.Store(0, 0, 0, t0), 1, t0.plus(Duration.ofMinutes(30)))
        assertThat(half.wood).isEqualTo(50)
        assertThat(half.settledAt).isEqualTo(t0.plus(Duration.ofMinutes(30)))

        // Ninety seconds earn two units; the thirty-six seconds that bought no third stay as time.
        val part = BarbarianRules.settle(BarbarianRules.Store(0, 0, 0, t0), 1, t0.plusSeconds(90))
        assertThat(part.stone).isEqualTo(2)
        assertThat(part.settledAt).isEqualTo(t0.plusSeconds(72))

        // The ceiling is the ceiling, and a store that is full all round simply moves its clock on.
        val full = BarbarianRules.settle(BarbarianRules.Store(900, 900, 900, t0), 1, t0.plus(Duration.ofHours(5)))
        assertThat(full.wood).isEqualTo(1000)
        val standing = BarbarianRules.settle(BarbarianRules.Store(1000, 1000, 1000, t0), 1, t0.plus(Duration.ofHours(5)))
        assertThat(standing.wood).isEqualTo(1000)
        assertThat(standing.settledAt).isEqualTo(t0.plus(Duration.ofHours(5)))
    }

    @Test
    fun `a village left alone gives back a level a day until it is back at one`() {
        // Nobody has taken it: there is nothing to give back.
        assertThat(BarbarianRules.fallBack(1, null, t0.plus(Duration.ofDays(3))).level).isEqualTo(1)
        // Not a whole day yet.
        assertThat(BarbarianRules.fallBack(3, t0, t0.plus(Duration.ofHours(23))).level).isEqualTo(3)

        val one = BarbarianRules.fallBack(3, t0, t0.plus(Duration.ofHours(25)))
        assertThat(one.level).isEqualTo(2)
        assertThat(one.raidedAt).isEqualTo(t0.plus(Duration.ofHours(24)))

        // Two days take two levels, and at level 1 there is nothing left to measure.
        val back = BarbarianRules.fallBack(3, t0, t0.plus(Duration.ofHours(49)))
        assertThat(back.level).isEqualTo(1)
        assertThat(back.raidedAt).isNull()
        assertThat(BarbarianRules.fallBack(2, t0, t0.plus(Duration.ofDays(9))).level).isEqualTo(1)
    }

    @Test
    fun `the brigands meet the attack as one flat number and a tie goes to the village`() {
        // 100 Axemen (4000) against 100 brigands (2000): (2000/4000)^1.5 = 35% of the raiders.
        val won = BarbarianRules.resolve(mapOf(Unit.AXEMAN to 100), BarbarianRules.brigands(1))
        assertThat(won.attackerWon).isTrue()
        assertThat(won.attackerLeft).containsEntry(Unit.AXEMAN, 65)
        assertThat(won.brigandsLeft).isZero()

        // 10 Axemen (400) against the same 2000: the raid dies and 8 brigands fall with it.
        val lost = BarbarianRules.resolve(mapOf(Unit.AXEMAN to 10), BarbarianRules.brigands(1))
        assertThat(lost.attackerWon).isFalse()
        assertThat(lost.attackerLeft).containsEntry(Unit.AXEMAN, 0)
        assertThat(lost.brigandsLeft).isEqualTo(92)

        // 50 Axemen are exactly 2000: a tie goes to the village, as it does to a defending city.
        assertThat(BarbarianRules.resolve(mapOf(Unit.AXEMAN to 50), BarbarianRules.brigands(1)).attackerWon).isFalse()
    }

    @Test
    fun `a raid takes the least of the deposit's share, the store and what it can carry`() {
        val full = BarbarianRules.Store(1000, 1000, 1000, t0)
        assertThat(BarbarianRules.share(1000)).isEqualTo(200)

        // A level-1 Deposit holds 1000, so 200 of each is the raid's share and it bites first.
        assertThat(BarbarianRules.plunder(full, 1000, 650)).isEqualTo(Triple(200L, 200L, 200L))
        // A big Deposit leaves the store to bite.
        assertThat(BarbarianRules.plunder(BarbarianRules.Store(100, 100, 100, t0), 50_000, 650))
            .isEqualTo(Triple(100L, 100L, 100L))
        // And with room in both, the carry does, filled evenly across the three.
        assertThat(BarbarianRules.plunder(full, 50_000, 650)).isEqualTo(Triple(217L, 217L, 216L))
    }
}
