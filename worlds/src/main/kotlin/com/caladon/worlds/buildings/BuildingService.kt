package com.caladon.worlds.buildings

import com.caladon.users.domain.Role
import com.caladon.worlds.resources.CityAccess
import com.caladon.worlds.resources.CityState
import com.caladon.worlds.resources.ResourceConstants
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.BuildingRules
import com.caladon.worlds.rules.Cost
import com.caladon.worlds.rules.Requirement
import com.caladon.worlds.service.WorldException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/** The build queue: what each building's next level costs, ordering a level, cancelling an order. */
@Service
class BuildingService(
    private val cityAccess: CityAccess,
    private val buildOrderRepository: CityBuildOrderRepository,
) {
    data class NextLevel(
        val level: Int, val cost: Cost, val popCost: Long, val points: Long, val effect: BuildingRules.Effect,
        val buildTimeSeconds: Long, val blockedBy: List<Requirement>,
    )
    data class BuildingView(
        val building: Building, val level: Int, val points: Long, val effect: BuildingRules.Effect, val queued: Int,
        val next: NextLevel?,
    )

    fun views(state: CityState): List<BuildingView> {
        val levels = state.levels()
        val queued = state.buildOrders.groupingBy { it.building }.eachCount()
        return Building.entries.map { b ->
            val level = levels.getValue(b)
            val q = queued[b] ?: 0
            val target = level + q + 1
            val next = if (target > b.maxLevel) null else NextLevel(
                level = target,
                cost = BuildingRules.cost(b, target),
                popCost = BuildingRules.popCost(b, target),
                points = BuildingRules.points(b, target),
                effect = BuildingRules.effect(b, target),
                buildTimeSeconds = buildSeconds(b, target, state.level(Building.TOWN_HALL)),
                blockedBy = BuildingRules.unmetRequirements(b, target, levels),
            )
            BuildingView(b, level, BuildingRules.points(b, level), BuildingRules.effect(b, level), q, next)
        }
    }

    @Transactional(readOnly = false)
    fun list(worldId: Long, cityId: Long, userId: Long, role: Role): Pair<CityState, List<BuildingView>> {
        val state = cityAccess.open(worldId, cityId, userId, role)
        return state to views(state)
    }

    /** Orders the next level of [building]: requirements, queue slots, then payment, then the timed order. */
    @Transactional
    fun upgrade(worldId: Long, cityId: Long, userId: Long, role: Role, building: Building): CityState {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val queuedHere = state.buildOrders.count { it.building == building }
        val target = state.level(building) + queuedHere + 1
        if (target > building.maxLevel) throw WorldException.MaxLevel()

        val unmet = BuildingRules.unmetRequirements(building, target, state.levels())
        if (unmet.isNotEmpty()) throw WorldException.RequirementsNotMet(unmet.associate { it.building.name to it.level.toString() })

        val townHall = state.level(Building.TOWN_HALL)
        if (state.buildOrders.size >= BuildingRules.queueSlots(townHall)) throw WorldException.QueueFull()

        val cost = BuildingRules.cost(building, target)
        val pop = BuildingRules.popCost(building, target)
        val short = state.shortfall(cost)
        if (short.isNotEmpty()) throw WorldException.NotEnoughResources(short.entries.associate { it.key.name.lowercase() to it.value.toString() })
        if (state.resources.population < pop) throw WorldException.NotEnoughPopulation((pop - state.resources.population).toString())
        state.pay(cost, pop)

        val durationMs = buildSeconds(building, target, townHall) * 1000
        val startsAt = state.buildOrders.lastOrNull()?.completesAt?.takeIf { it.isAfter(state.now) } ?: state.now
        val order = buildOrderRepository.save(
            CityBuildOrder(
                cityId = cityId, building = building, targetLevel = target, orderedAt = state.now,
                startedAt = startsAt, completesAt = startsAt.plusMillis(durationMs), durationMs = durationMs,
            ),
        )
        state.buildOrders += order
        return state
    }

    /** Cancels the last order of the queue and refunds it in full; earlier orders → `409 NOT_LAST_IN_QUEUE`. */
    @Transactional
    fun cancel(worldId: Long, cityId: Long, userId: Long, role: Role, orderId: Long): CityState {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val order = state.buildOrders.firstOrNull { it.id == orderId } ?: throw WorldException.OrderNotFound()
        if (state.buildOrders.last() != order) throw WorldException.NotLastInQueue()
        state.refund(BuildingRules.cost(order.building, order.targetLevel), BuildingRules.popCost(order.building, order.targetLevel))
        state.buildOrders.remove(order)
        buildOrderRepository.delete(order)
        return state
    }

    private fun buildSeconds(b: Building, level: Int, townHall: Int): Long =
        Math.round(BuildingRules.buildTimeSeconds(b, level, townHall) / ResourceConstants.WORLD_SPEED)

    companion object {
        fun duration(seconds: Long): Duration = Duration.ofSeconds(seconds)
    }
}
