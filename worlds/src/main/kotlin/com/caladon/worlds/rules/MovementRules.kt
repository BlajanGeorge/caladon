package com.caladon.worlds.rules

import com.caladon.worlds.resources.ResourceConstants
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Travel, combat and plunder of a troop movement (ARCHITECTURE.md → Army → Movements). Pure arithmetic on
 * unit counts: who wins, what it costs the winner, and what the survivors can carry home.
 */
object MovementRules {
    /** What an attacking unit counts as, so the defence value that meets it can be picked. */
    enum class Arm { INFANTRY, CAVALRY, ARCHER }

    fun arm(u: Unit): Arm = when (u) {
        Unit.LIGHT_CAV, Unit.HEAVY_CAV -> Arm.CAVALRY
        Unit.ARCHER -> Arm.ARCHER
        else -> Arm.INFANTRY
    }

    /** Straight-line distance between two fields, in fields. */
    fun distance(ax: Int, ay: Int, bx: Int, by: Int): Double = hypot((bx - ax).toDouble(), (by - ay).toDouble())

    /** Seconds on the road: the distance at the slowest unit's minutes per field, divided by the world speed. */
    fun travelSeconds(distance: Double, slowestSpeedMinutesPerField: Int): Long =
        Math.round(distance * slowestSpeedMinutesPerField * 60 / ResourceConstants.WORLD_SPEED)

    /** The speed the movement travels at: its slowest unit's. */
    fun slowestSpeed(units: Map<Unit, Int>): Int = units.filterValues { it > 0 }.keys.maxOf { it.speed }

    /** How much the units can carry away in total. */
    fun carry(units: Map<Unit, Int>): Long = units.entries.sumOf { it.key.carry.toLong() * it.value }

    /**
     * Resolves an attack on a city defended by [defenders] (its own troops and any support, keyed per
     * holder so each is reduced on its own row) behind a Wall at [wallLevel]. A tie goes to the defender.
     */
    fun <K> resolve(attackers: Map<Unit, Int>, defenders: Map<K, Map<Unit, Int>>, wallLevel: Int): CombatOf<K> {
        val attack = attackers.filterValues { it > 0 }
        val attackPower = attack.entries.sumOf { it.key.attack.toDouble() * it.value }
        // The attack is split into the three arms by the power each brings; every arm meets the defence
        // value made for it.
        val perArm = Arm.entries.associateWith { a -> attack.entries.filter { arm(it.key) == a }.sumOf { it.key.attack.toDouble() * it.value } }
        val shares = Arm.entries.associateWith { if (attackPower <= 0.0) 0.0 else perArm.getValue(it) / attackPower }
        val defencePower = defenders.values.sumOf { held ->
            held.entries.sumOf { (u, n) ->
                n * (shares.getValue(Arm.INFANTRY) * u.defence +
                    shares.getValue(Arm.CAVALRY) * u.defenceCavalry +
                    shares.getValue(Arm.ARCHER) * u.defenceArcher)
            }
        } * BuildingRules.wallFactor(wallLevel)

        val attackerWon = attackPower > defencePower
        val loss = losses(if (attackerWon) defencePower else attackPower, if (attackerWon) attackPower else defencePower)
        return CombatOf(
            attackPower = attackPower,
            defencePower = defencePower,
            attackerWon = attackerWon,
            attackerLeft = if (attackerWon) attackers.mapValues { survivors(it.value, loss) } else attackers.mapValues { 0 },
            defenderLeft = defenders.mapValues { (_, held) ->
                if (attackerWon) held.mapValues { 0 } else held.mapValues { survivors(it.value, loss) }
            },
        )
    }

    data class CombatOf<K>(
        val attackPower: Double,
        val defencePower: Double,
        val attackerWon: Boolean,
        val attackerLeft: Map<Unit, Int>,
        val defenderLeft: Map<K, Map<Unit, Int>>,
    )

    /** The fraction of itself the winner loses: `(loser / winner)^1.5`. */
    fun losses(loserPower: Double, winnerPower: Double): Double =
        if (winnerPower <= 0.0) 0.0 else (loserPower / winnerPower).coerceIn(0.0, 1.0).pow(1.5)

    private fun survivors(count: Int, loss: Double): Int = count - floor(count * loss).toInt()

    /**
     * What a winning attacker takes: per resource at most what the Vault does not hide, and no more in
     * total than the survivors can carry. The carry is filled evenly across the three resources, so a
     * resource that runs out leaves its share to the others.
     */
    fun plunder(available: Triple<Long, Long, Long>, carry: Long): Triple<Long, Long, Long> {
        val cap = longArrayOf(available.first.coerceAtLeast(0), available.second.coerceAtLeast(0), available.third.coerceAtLeast(0))
        val taken = LongArray(3)
        var left = carry
        while (left > 0) {
            val open = (0..2).filter { cap[it] > taken[it] }
            if (open.isEmpty()) break
            val share = left / open.size
            if (share == 0L) {
                for (i in open) { if (left == 0L) break; taken[i]++; left-- }
                break
            }
            for (i in open) {
                val add = minOf(share, cap[i] - taken[i])
                taken[i] += add
                left -= add
            }
        }
        return Triple(taken[0], taken[1], taken[2])
    }

}
