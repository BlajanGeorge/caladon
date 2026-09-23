package com.caladon.worlds.rules

/** Resource cost of one building level or one unit. */
data class Cost(val wood: Long, val stone: Long, val iron: Long) {
    operator fun times(n: Long) = Cost(wood * n, stone * n, iron * n)
    operator fun plus(o: Cost) = Cost(wood + o.wood, stone + o.stone, iron + o.iron)
    companion object { val ZERO = Cost(0, 0, 0) }
}

/** A `(building, level)` a player must already have. */
data class Requirement(val building: Building, val level: Int)

/**
 * The buildings a city can have. Fixed and global. Per-level tables are Tribal Wars' (`base × factor^(L−1)`,
 * see docs/BUILDINGS-PROPOSAL.md); `founded` buildings exist at level 1 in every new city and level 1 is free.
 */
enum class Building(
    val displayName: String,
    val maxLevel: Int,
    val founded: Boolean,
    /** Level-1 cost; level L costs `base × factor^(L−1)`. */
    val baseCost: Cost,
    val costFactors: Triple<Double, Double, Double>,
    /** Cumulative population at level 1 and its growth factor: `popTotal(L) = base × factor^(L−1)`. */
    val basePop: Double,
    val popFactor: Double,
    /** Points at level 1; level L is worth `P1 × 1.2^(L−1)`. */
    val pointsL1: Double,
    /** Base build time in seconds at world speed 1 (before the level and Town Hall factors). */
    val baseBuildTimeSeconds: Double,
    /** To order level 1 (player-built buildings only). */
    val prerequisites: List<Requirement>,
    /** Extra conditions from a given target level on, on top of the Town Hall step rule. */
    val higherRequirements: Map<Int, List<Requirement>> = emptyMap(),
) {
    FARM("Farm", 30, true, Cost(45, 40, 30), Triple(1.30, 1.32, 1.29), 0.0, 1.0, 5.0, 1200.0, emptyList()),
    WOODCUTTER("Woodcutter", 30, true, Cost(50, 60, 40), Triple(1.25, 1.275, 1.245), 5.0, 1.155, 6.0, 900.0, emptyList()),
    STONE_MINE("Stone Mine", 30, true, Cost(65, 50, 40), Triple(1.27, 1.265, 1.24), 10.0, 1.14, 6.0, 900.0, emptyList()),
    IRON_MINE("Iron Mine", 30, true, Cost(75, 65, 70), Triple(1.252, 1.275, 1.24), 10.0, 1.17, 6.0, 1080.0, emptyList()),
    DEPOSIT("Deposit", 30, true, Cost(60, 50, 40), Triple(1.265, 1.27, 1.245), 0.0, 1.15, 6.0, 1020.0, emptyList()),
    TOWN_HALL("Town Hall", 30, true, Cost(90, 80, 70), Triple(1.26, 1.275, 1.26), 5.0, 1.17, 10.0, 900.0, emptyList()),
    BARRACKS(
        "Barracks", 25, false, Cost(200, 170, 90), Triple(1.26, 1.28, 1.26), 7.0, 1.17, 16.0, 1800.0,
        listOf(Requirement(TOWN_HALL, 3)),
        mapOf(10 to listOf(Requirement(TOWN_HALL, 10)), 20 to listOf(Requirement(TOWN_HALL, 20))),
    ),
    ACADEMY(
        "Academy", 20, false, Cost(220, 180, 240), Triple(1.26, 1.275, 1.26), 20.0, 1.17, 19.0, 6000.0,
        listOf(Requirement(TOWN_HALL, 8), Requirement(FARM, 6), Requirement(BARRACKS, 5)),
        mapOf(10 to listOf(Requirement(BARRACKS, 10)), 15 to listOf(Requirement(BARRACKS, 15))),
    ),
    WALL(
        "Wall", 20, false, Cost(50, 100, 20), Triple(1.26, 1.275, 1.26), 5.0, 1.17, 8.0, 3600.0,
        listOf(Requirement(TOWN_HALL, 5)),
        mapOf(10 to listOf(Requirement(STONE_MINE, 10)), 15 to listOf(Requirement(STONE_MINE, 15))),
    ),
    VAULT(
        "Vault", 10, false, Cost(50, 60, 50), Triple(1.25, 1.25, 1.25), 2.0, 1.17, 5.0, 1800.0,
        listOf(Requirement(TOWN_HALL, 5), Requirement(DEPOSIT, 5)),
        mapOf(5 to listOf(Requirement(DEPOSIT, 10))),
    );

    companion object {
        val FOUNDED: List<Building> = entries.filter { it.founded }
    }
}
