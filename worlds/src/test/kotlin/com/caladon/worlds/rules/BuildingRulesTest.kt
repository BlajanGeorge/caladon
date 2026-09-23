package com.caladon.worlds.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Numbers checked against docs/BUILDINGS-PROPOSAL.md (Tribal Wars 1:1). */
class BuildingRulesTest {

    @Test
    fun `costs follow base times factor to the level minus one, level 1 free when founded`() {
        assertThat(BuildingRules.cost(Building.WOODCUTTER, 1)).isEqualTo(Cost.ZERO)
        assertThat(BuildingRules.cost(Building.WOODCUTTER, 2)).isEqualTo(Cost(63, 77, 50))
        assertThat(BuildingRules.cost(Building.WOODCUTTER, 30)).isEqualTo(Cost(32_312, 68_857, 23_013))
        assertThat(BuildingRules.cost(Building.FARM, 2)).isEqualTo(Cost(59, 53, 39))
        assertThat(BuildingRules.cost(Building.BARRACKS, 1)).isEqualTo(Cost(200, 170, 90))
        assertThat(BuildingRules.cost(Building.WALL, 2)).isEqualTo(Cost(63, 127, 25))
    }

    @Test
    fun `population is the difference of cumulative totals`() {
        assertThat(BuildingRules.popCost(Building.WOODCUTTER, 1)).isEqualTo(0)
        assertThat(BuildingRules.popCost(Building.WOODCUTTER, 2)).isEqualTo(1)   // 6 − 5
        assertThat(BuildingRules.popCost(Building.BARRACKS, 1)).isEqualTo(7)
        assertThat(BuildingRules.popTotal(Building.TOWN_HALL, 30)).isEqualTo(475)
        assertThat(BuildingRules.popCost(Building.FARM, 10)).isEqualTo(0)
    }

    @Test
    fun `points grow by 1_2 per level`() {
        assertThat(BuildingRules.points(Building.TOWN_HALL, 1)).isEqualTo(10)
        assertThat(BuildingRules.points(Building.TOWN_HALL, 30)).isEqualTo(1978)
        assertThat(BuildingRules.points(Building.BARRACKS, 25)).isEqualTo(1272)
        assertThat(BuildingRules.points(Building.FARM, 30)).isEqualTo(989)
        assertThat(BuildingRules.points(Building.WOODCUTTER, 30)).isEqualTo(1187)
        assertThat(BuildingRules.points(Building.VAULT, 10)).isEqualTo(26)
        assertThat(Building.FOUNDED.sumOf { BuildingRules.points(it, 1) }).isEqualTo(39)
        assertThat(Building.entries.sumOf { BuildingRules.points(it, it.maxLevel) }).isEqualTo(9876)
    }

    @Test
    fun `production capacity farm and vault effects`() {
        assertThat(BuildingRules.production(1)).isEqualTo(30)
        assertThat(BuildingRules.production(2)).isEqualTo(35)
        assertThat(BuildingRules.production(30)).isEqualTo(2400)
        assertThat(BuildingRules.capacity(1)).isEqualTo(1000)
        assertThat(BuildingRules.capacity(2)).isEqualTo(1229)
        assertThat(BuildingRules.capacity(30)).isEqualTo(400_000)
        assertThat(BuildingRules.farmPop(1)).isEqualTo(240)
        assertThat(BuildingRules.farmPop(2)).isEqualTo(281)
        assertThat(BuildingRules.farmPop(30)).isEqualTo(24_000)
        assertThat(BuildingRules.farmGain(2)).isEqualTo(41)
        assertThat((1..10).map { BuildingRules.vault(it) }).containsExactly(150, 200, 267, 356, 474, 632, 843, 1125, 1500, 2000)
    }

    @Test
    fun `build time formula and town hall factor`() {
        assertThat(BuildingRules.buildTimeSeconds(Building.WOODCUTTER, 2, 1)).isCloseTo(95.0, org.assertj.core.data.Offset.offset(0.6)) // 1m35s
        assertThat(BuildingRules.buildTimeSeconds(Building.WOODCUTTER, 30, 1) / 3600).isCloseTo(50.9, org.assertj.core.data.Offset.offset(0.1))
        assertThat(BuildingRules.queueSlots(1)).isEqualTo(2)
        assertThat(BuildingRules.queueSlots(10)).isEqualTo(3)
        assertThat(BuildingRules.queueSlots(20)).isEqualTo(4)
        assertThat(BuildingRules.effect(Building.TOWN_HALL, 1)).isEqualTo(BuildingRules.Effect(95, "% build time"))
        assertThat(BuildingRules.effect(Building.BARRACKS, 1)).isEqualTo(BuildingRules.Effect(63, "% recruit time"))
        assertThat(BuildingRules.effect(Building.WALL, 20)).isEqualTo(BuildingRules.Effect(107, "% defence"))
    }

    @Test
    fun `town hall step rule and cross-building requirements`() {
        val founded = Building.FOUNDED.associateWith { 1 }
        assertThat(BuildingRules.unmetRequirements(Building.FARM, 5, founded)).isEmpty()
        assertThat(BuildingRules.unmetRequirements(Building.FARM, 6, founded)).containsExactly(Requirement(Building.TOWN_HALL, 5))
        assertThat(BuildingRules.unmetRequirements(Building.TOWN_HALL, 30, founded)).isEmpty()
        assertThat(BuildingRules.unmetRequirements(Building.BARRACKS, 1, founded)).containsExactly(Requirement(Building.TOWN_HALL, 3))
        assertThat(BuildingRules.unmetRequirements(Building.BARRACKS, 1, founded + (Building.TOWN_HALL to 3))).isEmpty()
        val levels = founded + mapOf(Building.TOWN_HALL to 9, Building.BARRACKS to 9)
        assertThat(BuildingRules.unmetRequirements(Building.BARRACKS, 10, levels))
            .containsExactly(Requirement(Building.TOWN_HALL, 10))
        val vault = founded + mapOf(Building.TOWN_HALL to 5, Building.DEPOSIT to 5, Building.VAULT to 4)
        assertThat(BuildingRules.unmetRequirements(Building.VAULT, 5, vault)).containsExactly(Requirement(Building.DEPOSIT, 10))
        assertThat(BuildingRules.unmetRequirements(Building.ACADEMY, 1, founded + (Building.TOWN_HALL to 8)))
            .containsExactly(Requirement(Building.FARM, 6), Requirement(Building.BARRACKS, 5))
    }
}
