package com.caladon.worlds.rules

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

class MovementRulesTest {

    private fun resolve(attackers: Map<Unit, Int>, defenders: Map<Unit, Int>, wall: Int = 0) =
        MovementRules.resolve(attackers, mapOf("city" to defenders), wall)

    @Test
    fun `distance is the straight line in fields and travel time the slowest unit's`() {
        assertThat(MovementRules.distance(10, 10, 13, 14)).isEqualTo(5.0)
        assertThat(MovementRules.distance(10, 10, 10, 10)).isEqualTo(0.0)
        assertThat(MovementRules.travelSeconds(5.0, Unit.SPEARMAN.speed)).isEqualTo(5400)   // 18 min per field
        assertThat(MovementRules.travelSeconds(1.5, Unit.LIGHT_CAV.speed)).isEqualTo(900)
        assertThat(MovementRules.slowestSpeed(mapOf(Unit.LIGHT_CAV to 10, Unit.RAM to 1))).isEqualTo(Unit.RAM.speed)
        assertThat(MovementRules.slowestSpeed(mapOf(Unit.LIGHT_CAV to 10, Unit.RAM to 0))).isEqualTo(Unit.LIGHT_CAV.speed)
    }

    @Test
    fun `the stronger side wins, the loser loses everything and the winner the computed fraction`() {
        // 100 Axemen (4000) against 50 Spearmen (750): (750/4000)^1.5 = 8.1% of the attackers.
        val won = resolve(mapOf(Unit.AXEMAN to 100), mapOf(Unit.SPEARMAN to 50))
        assertThat(won.attackPower).isEqualTo(4000.0)
        assertThat(won.defencePower).isEqualTo(750.0)
        assertThat(won.attackerWon).isTrue()
        assertThat(won.attackerLeft).containsEntry(Unit.AXEMAN, 92)
        assertThat(won.defenderLeft.getValue("city")).containsEntry(Unit.SPEARMAN, 0)

        // 10 Axemen (400) against the same wall of spearmen: (400/750)^1.5 = 38.9% of the defenders.
        val lost = resolve(mapOf(Unit.AXEMAN to 10), mapOf(Unit.SPEARMAN to 50))
        assertThat(lost.attackerWon).isFalse()
        assertThat(lost.attackerLeft).containsEntry(Unit.AXEMAN, 0)
        assertThat(lost.defenderLeft.getValue("city")).containsEntry(Unit.SPEARMAN, 31)
    }

    @Test
    fun `the wall raises the defence by the same factor its own panel reports`() {
        // 750 bare defence times BuildingRules.wallFactor(10) = 1.037^10, the one Wall formula in the game.
        val flat = resolve(mapOf(Unit.AXEMAN to 100), mapOf(Unit.SPEARMAN to 50), wall = 10)
        assertThat(flat.defencePower).isCloseTo(750 * BuildingRules.wallFactor(10), offset(0.001))
        assertThat(flat.defencePower).isCloseTo(1078.6, offset(0.1))
        assertThat(flat.attackerLeft).containsEntry(Unit.AXEMAN, 86)
    }

    @Test
    fun `the attack is split into arms and each share meets the defence made for it`() {
        // 1300 of 1700 is cavalry, so most of the spearmen's 45 anti-cavalry defence is what answers.
        val mixed = resolve(mapOf(Unit.LIGHT_CAV to 10, Unit.AXEMAN to 10), mapOf(Unit.SPEARMAN to 10))
        assertThat(mixed.attackPower).isEqualTo(1700.0)
        assertThat(mixed.defencePower).isCloseTo(379.4, offset(0.1))

        val archers = resolve(mapOf(Unit.ARCHER to 10), mapOf(Unit.SPEARMAN to 10))
        assertThat(archers.defencePower).isEqualTo(200.0)   // the Spearman's defenceArcher, 20
    }

    @Test
    fun `an empty city falls without a loss and a tie goes to the defender`() {
        val empty = resolve(mapOf(Unit.AXEMAN to 10), emptyMap())
        assertThat(empty.attackerWon).isTrue()
        assertThat(empty.attackerLeft).containsEntry(Unit.AXEMAN, 10)

        val tie = resolve(mapOf(Unit.AXEMAN to 10), mapOf(Unit.HEAVY_CAV to 2))
        assertThat(tie.attackPower).isEqualTo(tie.defencePower)
        assertThat(tie.attackerWon).isFalse()
    }

    @Test
    fun `every holder of defending troops loses its own share`() {
        val combat = MovementRules.resolve(
            mapOf(Unit.AXEMAN to 10),
            mapOf("own" to mapOf(Unit.SPEARMAN to 50), "support" to mapOf(Unit.SPEARMAN to 50)),
            0,
        )
        assertThat(combat.attackerWon).isFalse()
        // 400 against 1500: (400/1500)^1.5 = 13.8% of each holder's spearmen.
        assertThat(combat.defenderLeft.getValue("own")).containsEntry(Unit.SPEARMAN, 44)
        assertThat(combat.defenderLeft.getValue("support")).containsEntry(Unit.SPEARMAN, 44)
    }

    @Test
    fun `plunder fills the carry evenly and never takes what the vault hides`() {
        assertThat(MovementRules.plunder(Triple(1000, 500, 0), 900)).isEqualTo(Triple(450L, 450L, 0L))
        assertThat(MovementRules.plunder(Triple(100, 100, 100), 900)).isEqualTo(Triple(100L, 100L, 100L))
        assertThat(MovementRules.plunder(Triple(1000, 1000, 1000), 5)).isEqualTo(Triple(2L, 2L, 1L))
        assertThat(MovementRules.plunder(Triple(-50, 300, 300), 600)).isEqualTo(Triple(0L, 300L, 300L))
        assertThat(MovementRules.carry(mapOf(Unit.LIGHT_CAV to 10, Unit.RAM to 1))).isEqualTo(800)
    }
}
