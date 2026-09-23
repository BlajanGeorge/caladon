package com.caladon.worlds.rules

import kotlin.math.floor
import kotlin.math.pow

/** Every per-level number of the Buildings design, computed from the formulas (no stored tables). */
object BuildingRules {
    const val POINTS_GROWTH = 1.2
    const val PRODUCTION_GROWTH = 1.163118
    const val CAPACITY_GROWTH = 1.2294934
    const val PRODUCTION_L1 = 30.0
    const val CAPACITY_L1 = 1000.0
    const val FARM_L1 = 240.0
    /** Farm population at max level = FARM_MAX_MULT × FARM_L1. */
    const val FARM_MAX_MULT = 100.0
    const val VAULT_L1 = 150.0
    /** Tribal Wars uses table values for the last Vault levels instead of the 4/3 curve. */
    private val VAULT_TABLE = mapOf(8 to 1125L, 9 to 1500L, 10 to 2000L)

    /** Resources to reach [level] (from level − 1). Level 1 of a founded building is free. */
    fun cost(b: Building, level: Int): Cost {
        if (b.founded && level == 1) return Cost.ZERO
        val (fw, fs, fi) = b.costFactors
        return Cost(
            Tw.curve(b.baseCost.wood.toDouble(), fw, level),
            Tw.curve(b.baseCost.stone.toDouble(), fs, level),
            Tw.curve(b.baseCost.iron.toDouble(), fi, level),
        )
    }

    /** Total population held by the building at [level] (0 at level 0). */
    fun popTotal(b: Building, level: Int): Long = if (level <= 0) 0 else Tw.curve(b.basePop, b.popFactor, level)

    /** Population that reaching [level] takes. Level 1 of a founded building is free. */
    fun popCost(b: Building, level: Int): Long {
        if (b.founded && level == 1) return 0
        return popTotal(b, level) - popTotal(b, level - 1)
    }

    /** Points the building is worth at [level] (cumulative); 0 at level 0. */
    fun points(b: Building, level: Int): Long = if (level <= 0) 0 else Tw.curve(b.pointsL1, POINTS_GROWTH, level)

    /** Per-hour production of a resource building at [level] (world speed not applied). */
    fun production(level: Int): Long = if (level <= 0) 0 else Tw.curve(PRODUCTION_L1, PRODUCTION_GROWTH, level)

    /** Max stock per resource at Deposit [level]. */
    fun capacity(level: Int): Long = Tw.curve(CAPACITY_L1, CAPACITY_GROWTH, level)

    /** Population the Farm provides in total at [level]: `floor(240 × 100^((L−1)/29))`. */
    fun farmPop(level: Int): Long = if (level <= 0) 0 else floor(FARM_L1 * FARM_MAX_MULT.pow((level - 1) / 29.0)).toLong()

    /** Population credited when Farm [level] completes. */
    fun farmGain(level: Int): Long = farmPop(level) - farmPop(level - 1)

    /** Resources per kind hidden from plunder at Vault [level]. */
    fun vault(level: Int): Long = if (level <= 0) 0 else VAULT_TABLE[level] ?: Tw.curve(VAULT_L1, 4.0 / 3.0, level)

    /** Build-queue slots at Town Hall [level]: 2, 3 from 10, 4 from 20. */
    fun queueSlots(townHallLevel: Int): Int = 2 + (if (townHallLevel >= 10) 1 else 0) + (if (townHallLevel >= 20) 1 else 0)

    /** Town Hall step rule: the Town Hall level needed to reach [level] of any other building. */
    fun townHallNeeded(level: Int): Int = 5 * ((level - 1) / 5)

    /** Build time factor of the Town Hall: `1.05^(−L)`. */
    fun townHallFactor(level: Int): Double = 1.05.pow(-level)

    /** Recruit time factor of the Barracks: `2/3 × 1.06^(−L)`. */
    fun recruitFactor(barracksLevel: Int): Double = (2.0 / 3.0) * 1.06.pow(-barracksLevel)

    /** Study time factor of the Academy: `1.1^(−L)`. */
    fun studyFactor(academyLevel: Int): Double = 1.1.pow(-academyLevel)

    /** Wall defence multiplier: `1.037^L`. */
    fun wallFactor(level: Int): Double = 1.037.pow(level)

    /**
     * Seconds to build [level] of [b] at Town Hall [townHallLevel] (world speed 1):
     * `base × 1.18 × 1.2^(L−1−14/(L−1)) × 1.05^(−TH)`; exponent −13 for L ≤ 2.
     */
    fun buildTimeSeconds(b: Building, level: Int, townHallLevel: Int): Double {
        val e = if (level <= 2) -13.0 else level - 1 - 14.0 / (level - 1)
        return b.baseBuildTimeSeconds * 1.18 * 1.2.pow(e) * townHallFactor(townHallLevel)
    }

    /** The building's job at [level], as a number, and the unit it is expressed in. */
    fun effect(b: Building, level: Int): Effect = when (b) {
        Building.FARM -> Effect(farmPop(level), "population")
        Building.WOODCUTTER, Building.STONE_MINE, Building.IRON_MINE -> Effect(production(level), "per hour")
        Building.DEPOSIT -> Effect(capacity(level), "capacity")
        Building.TOWN_HALL -> Effect(Tw.round(100 * townHallFactor(level)), "% build time")
        Building.BARRACKS -> Effect(Tw.round(100 * recruitFactor(level)), "% recruit time")
        Building.ACADEMY -> Effect(Tw.round(100 * studyFactor(level)), "% study time")
        Building.WALL -> Effect(Tw.round(100 * (wallFactor(level) - 1)), "% defence")
        Building.VAULT -> Effect(vault(level), "hidden per resource")
    }

    data class Effect(val value: Long, val unit: String)

    /**
     * Requirements to order [level] of [b] that [levels] (completed levels, 0 when absent) do not meet:
     * the level-1 prerequisites, the Town Hall step rule, and the higher-level conditions.
     */
    fun unmetRequirements(b: Building, level: Int, levels: Map<Building, Int>): List<Requirement> {
        fun has(r: Requirement) = (levels[r.building] ?: 0) >= r.level
        val needed = mutableListOf<Requirement>()
        if (level == 1) needed += b.prerequisites
        if (b != Building.TOWN_HALL) {
            val th = townHallNeeded(level)
            if (th > 0) needed += Requirement(Building.TOWN_HALL, th)
        }
        for ((from, reqs) in b.higherRequirements) if (level >= from) needed += reqs
        return needed.filterNot(::has).distinct()
    }
}
