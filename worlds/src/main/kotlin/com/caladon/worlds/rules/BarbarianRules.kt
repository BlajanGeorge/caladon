package com.caladon.worlds.rules

import java.time.Duration
import java.time.Instant
import kotlin.math.floor

/**
 * What a barbarian village defends with, what it holds and what a raid takes (ARCHITECTURE.md → Raiding
 * barbarians). Pure arithmetic: the village is a level, a store and a clock, and nothing here touches a
 * row or an entity.
 */
object BarbarianRules {
    /** Every village is called this: it has no owner to name it and no two are told apart. */
    const val NAME = "Barbarian village"

    /** Level 1 is a fresh village; a village hardens no further than 3. */
    const val MAX_LEVEL = 3

    const val MILITIA_PER_LEVEL = 100

    /** What one militiaman is worth against every kind of attack: no arms, no Wall. */
    const val DEFENCE_PER_MAN = 20

    const val STORE_PER_LEVEL = 1000L
    const val REFILL_PER_LEVEL_PER_HOUR = 100.0

    /**
     * The share of the raider's own Deposit capacity a raid may take of each resource: a raid is worth
     * what the city that sent it can hold, rather than a fixed prize.
     */
    const val RAID_SHARE = 0.20

    /** Untouched for this long, a village gives up a level of the hardening a raid bought it. */
    val FALL_BACK_AFTER: Duration = Duration.ofHours(24)

    fun militia(level: Int): Int = MILITIA_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)

    /** The whole militia's defence, met by every arm alike. */
    fun defence(level: Int): Double = militia(level).toDouble() * DEFENCE_PER_MAN

    fun ceiling(level: Int): Long = STORE_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)

    fun refillPerHour(level: Int): Double = REFILL_PER_LEVEL_PER_HOUR * level.coerceIn(1, MAX_LEVEL)

    /** A village that has just been taken stands harder the next time. */
    fun hardened(level: Int): Int = (level + 1).coerceAtMost(MAX_LEVEL)

    /** What a raid may take of one resource on the raider's own account. */
    fun share(depositCapacity: Long): Long = floor(depositCapacity * RAID_SHARE).toLong().coerceAtLeast(0)

    /** The store of one village at one instant; the three resources share a clock, refilling as one. */
    data class Store(val wood: Long, val stone: Long, val silver: Long, val settledAt: Instant)

    /**
     * The store brought up to [now], in whole units and with the unearned fraction left as time — the
     * discipline [Production] settles a city's stocks with. Whatever is at the ceiling earns nothing, so
     * a store that is full all round simply moves its clock on.
     */
    fun settle(store: Store, level: Int, now: Instant): Store {
        if (!now.isAfter(store.settledAt)) return store
        val ceiling = ceiling(level)
        if (store.wood >= ceiling && store.stone >= ceiling && store.silver >= ceiling) return store.copy(settledAt = now)
        val rate = refillPerHour(level)
        val elapsedMs = Duration.between(store.settledAt, now).toMillis()
        val earned = floor(rate * elapsedMs / 3_600_000.0).toLong()
        if (earned <= 0) return store
        val consumedMs = floor(earned * 3_600_000.0 / rate).toLong()
        return Store(
            wood = minOf(ceiling, store.wood + earned),
            stone = minOf(ceiling, store.stone + earned),
            silver = minOf(ceiling, store.silver + earned),
            settledAt = store.settledAt.plusMillis(consumedMs),
        )
    }

    /**
     * Where a village stands after being left alone: a level for every whole [FALL_BACK_AFTER] since it
     * was last taken, never below 1. [raidedAt] is null for a village nobody has taken, and becomes null
     * again once it is back at 1 — there is nothing left to give up.
     */
    data class FallBack(val level: Int, val raidedAt: Instant?)

    fun fallBack(level: Int, raidedAt: Instant?, now: Instant): FallBack {
        if (raidedAt == null || level <= 1 || !now.isAfter(raidedAt)) return FallBack(level, raidedAt)
        val steps = Duration.between(raidedAt, now).toMillis() / FALL_BACK_AFTER.toMillis()
        if (steps <= 0) return FallBack(level, raidedAt)
        val fallen = (level - steps).coerceAtLeast(1).toInt()
        return FallBack(fallen, if (fallen <= 1) null else raidedAt.plusMillis(steps * FALL_BACK_AFTER.toMillis()))
    }

    /** What a raid did: who was left standing on each side. */
    data class Raid(val attackerWon: Boolean, val attackerLeft: Map<Unit, Int>, val militiaLeft: Int)

    /**
     * The militia meet the attack as one flat number rather than a roster, with no Wall behind them. A
     * tie goes to the village, and the winner loses the ordinary `(loser/winner)^1.5` of itself.
     */
    fun resolve(attackers: Map<Unit, Int>, militia: Int): Raid {
        val attackPower = attackers.entries.sumOf { it.key.attack.toDouble() * it.value }
        val defencePower = militia.toDouble() * DEFENCE_PER_MAN
        val won = attackPower > defencePower
        val loss = MovementRules.losses(
            if (won) defencePower else attackPower,
            if (won) attackPower else defencePower,
        )
        return Raid(
            attackerWon = won,
            attackerLeft = attackers.mapValues { if (won) MovementRules.survivors(it.value, loss) else 0 },
            militiaLeft = if (won) 0 else MovementRules.survivors(militia, loss),
        )
    }

    /**
     * What the raid carries off: per resource the least of what the village holds and the raider's
     * [share], and no more in total than the survivors can carry, filled evenly as any plunder is.
     */
    fun plunder(store: Store, depositCapacity: Long, carry: Long): Triple<Long, Long, Long> {
        val share = share(depositCapacity)
        return MovementRules.plunder(
            Triple(minOf(store.wood, share), minOf(store.stone, share), minOf(store.silver, share)),
            carry,
        )
    }
}
