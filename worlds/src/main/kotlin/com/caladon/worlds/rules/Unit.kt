package com.caladon.worlds.rules

/**
 * The ten land units (ARCHITECTURE.md → Army). Stats are Tribal Wars' own. All are recruited from the
 * Barracks at or above [barracksLevel]; all but the Spearman must first be studied in the Academy at or
 * above [academyLevel].
 */
enum class Unit(
    val displayName: String,
    val role: String,
    val cost: Cost,
    val population: Int,
    /** Base recruit time in seconds at world speed 1, before the Barracks factor. */
    val baseRecruitSeconds: Long,
    val attack: Int,
    val defence: Int,
    val defenceCavalry: Int,
    val defenceArcher: Int,
    /** Minutes per map field. */
    val speed: Int,
    val carry: Int,
    val barracksLevel: Int,
    /** Academy level needed to study the unit; null when no study is needed. */
    val academyLevel: Int?,
    val studyCost: Cost?,
) {
    SPEARMAN("Spearman", "cheap defence, strong vs cavalry", Cost(50, 30, 10), 1, 1020, 10, 15, 45, 20, 18, 25, 1, null, null),
    SWORDSMAN("Swordsman", "defence vs infantry", Cost(30, 30, 70), 1, 1500, 25, 50, 15, 40, 22, 15, 3, 1, Cost(400, 500, 300)),
    SCOUT("Scout", "espionage, does not fight", Cost(50, 50, 20), 2, 900, 0, 2, 1, 2, 9, 0, 5, 2, Cost(560, 480, 480)),
    AXEMAN("Axeman", "cheap attack, weak defence", Cost(60, 30, 40), 1, 1320, 40, 10, 5, 10, 18, 10, 5, 3, Cost(700, 840, 820)),
    ARCHER("Archer", "defence vs archers", Cost(100, 30, 60), 1, 1800, 15, 50, 40, 5, 18, 10, 8, 5, Cost(640, 560, 740)),
    LIGHT_CAV("Light Cavalry", "fast attack, big carry (raiding)", Cost(125, 100, 250), 4, 1800, 130, 30, 40, 30, 10, 80, 10, 8, Cost(2200, 2400, 2000)),
    RAM("Ram", "breaks the Wall", Cost(300, 200, 200), 5, 4800, 2, 20, 50, 20, 30, 0, 12, 10, Cost(1200, 1600, 800)),
    HEAVY_CAV("Heavy Cavalry", "strong attack and defence, expensive", Cost(200, 150, 600), 6, 3600, 150, 200, 80, 180, 11, 50, 15, 13, Cost(3000, 2400, 2000)),
    CATAPULT("Catapult", "destroys buildings", Cost(320, 400, 100), 8, 7200, 100, 100, 50, 100, 30, 0, 18, 16, Cost(1600, 2000, 1200)),
    NOBLEMAN("Nobleman", "conquest: lowers loyalty, takes the city", Cost(40_000, 50_000, 50_000), 100, 18_000, 30, 100, 50, 100, 35, 0, 20, 20, Cost(15_000, 25_000, 10_000));

    val needsStudy: Boolean get() = academyLevel != null
}

object UnitRules {
    /** Seconds to recruit one unit at Barracks [barracksLevel] (world speed 1): `base × 2/3 × 1.06^(−L)`. */
    fun recruitSeconds(u: Unit, barracksLevel: Int): Double = u.baseRecruitSeconds * BuildingRules.recruitFactor(barracksLevel)

    /** Seconds to study a unit at Academy [academyLevel]: `2 × base × 1.1^(−L)` (placeholder, see the doc). */
    fun studySeconds(u: Unit, academyLevel: Int): Double = 2.0 * u.baseRecruitSeconds * BuildingRules.studyFactor(academyLevel)
}
