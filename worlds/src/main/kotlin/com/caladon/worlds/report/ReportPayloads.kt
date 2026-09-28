package com.caladon.worlds.report

import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.Unit

/**
 * The snapshots a report carries (ARCHITECTURE.md → Reports), one shape per kind. They are written to
 * jsonb as they are and read back as JSON, so the names here are the names the client sees.
 */

/** Which side of the battle the reader was on. */
enum class BattleRole { ATTACKER, DEFENDER, SUPPORTER }

/**
 * A battle as one reader may see it. [defender] is null for a beaten attacker: nobody survived to carry
 * the news back, so it learns nothing of what it met — [wall] and [plunder] are held back with it.
 */
data class BattlePayload(
    val role: BattleRole,
    val attacker: SidePayload,
    val defender: SidePayload?,
    /** Null when nothing was taken. */
    val plunder: ResourcesPayload?,
    val wall: WallPayload?,
)

/** One side's troops: the whole defence, its own and every supporter's, counts as the defending side. */
data class SidePayload(
    val player: String,
    val city: String,
    val units: List<UnitTallyPayload>,
    /** What the battle earned this side: the population it killed. */
    val points: Long,
)

/**
 * One type's three counts. [type] is a [Unit]'s name for troops and `MILITIA` for a barbarian village's
 * men, who are a count rather than a unit type and have no place in the unit table.
 */
data class UnitTallyPayload(val type: String, val name: String, val sent: Int, val lost: Int, val left: Int)

data class ResourcesPayload(val wood: Long, val stone: Long, val silver: Long)

data class WallPayload(val before: Int, val after: Int)

/** What the spy's owner is left with; [seen] is null when the attempt failed. */
data class EspionagePayload(val success: Boolean, val silver: Long, val seen: SeenPayload?)

/** The city as it stood at that moment. The target's Cave silver is not in it: hiding it is the point. */
data class SeenPayload(
    val resources: ResourcesPayload,
    val buildings: List<BuildingLevelPayload>,
    val units: List<UnitCountPayload>,
)

data class BuildingLevelPayload(val type: Building, val level: Int)

data class UnitCountPayload(val type: Unit, val name: String, val count: Int)

/** What a city learns when it catches a spy: by whom, and how close the thing was. */
data class CaughtPayload(val player: String, val city: String, val silver: Long)
