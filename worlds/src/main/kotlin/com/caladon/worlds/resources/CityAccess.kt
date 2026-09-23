package com.caladon.worlds.resources

import com.caladon.users.domain.Role
import com.caladon.worlds.buildings.CityBuildOrderRepository
import com.caladon.worlds.buildings.CityBuildingRepository
import com.caladon.worlds.domain.CityResources
import com.caladon.worlds.domain.Resource
import com.caladon.worlds.domain.WorldState
import com.caladon.worlds.repository.CityRepository
import com.caladon.worlds.repository.CityResourcesRepository
import com.caladon.worlds.repository.CitySlotRepository
import com.caladon.worlds.repository.WorldRepository
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.BuildingRules
import com.caladon.worlds.service.WorldException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Opens one of the caller's cities for reading or mutation: validates ownership, takes the row lock on
 * `city_resources` (the city lock), and advances the city to `now` — completing every due build order
 * in chronological order, settling resources with the old rates up to each completion before applying
 * its effect, then settling to `now`. Callers run inside a transaction; JPA flushes the changes at commit.
 */
@Component
class CityAccess(
    private val worldRepository: WorldRepository,
    private val cityRepository: CityRepository,
    private val citySlotRepository: CitySlotRepository,
    private val cityResourcesRepository: CityResourcesRepository,
    private val cityBuildingRepository: CityBuildingRepository,
    private val buildOrderRepository: CityBuildOrderRepository,
    private val clock: Clock,
) {
    fun open(worldId: Long, cityId: Long, userId: Long, role: Role): CityState {
        val world = worldRepository.findById(worldId).orElse(null) ?: throw WorldException.CityNotFound()
        if (world.state == WorldState.DRAFT && role != Role.ADMINISTRATOR) throw WorldException.CityNotFound()
        val city = cityRepository.findById(cityId).orElse(null)?.takeIf { it.worldId == worldId } ?: throw WorldException.CityNotFound()
        if (city.ownerUserId != userId) throw WorldException.NotOwner()

        val res = cityResourcesRepository.findWithLockByCityId(cityId) ?: throw WorldException.CityNotFound()
        val state = CityState(
            city = city,
            resources = res,
            buildings = cityBuildingRepository.findAllByIdCityId(cityId).associateBy { it.id.building }.toMutableMap(),
            buildOrders = buildOrderRepository.findAllByCityIdOrderByCompletesAtAscIdAsc(cityId).toMutableList(),
            now = clock.instant(),
        )
        advance(state)
        return state
    }

    /** For the sweeper: the caller already holds the city row lock; loads the city and advances it. */
    fun advanceCity(cityId: Long) {
        val city = cityRepository.findById(cityId).orElse(null) ?: return
        val res = cityResourcesRepository.findById(cityId).orElse(null) ?: return
        val state = CityState(
            city = city,
            resources = res,
            buildings = cityBuildingRepository.findAllByIdCityId(cityId).associateBy { it.id.building }.toMutableMap(),
            buildOrders = buildOrderRepository.findAllByCityIdOrderByCompletesAtAscIdAsc(cityId).toMutableList(),
            now = clock.instant(),
        )
        advance(state)
    }

    /** Completes every due order in time order (settling before each), then settles to `now`. */
    fun advance(state: CityState) {
        val now = state.now
        while (true) {
            val order = state.buildOrders.firstOrNull()?.takeIf { !it.completesAt.isAfter(now) } ?: break
            state.settleTo(order.completesAt)
            state.completeLevel(order.building, order.targetLevel)
            cityBuildingRepository.save(state.buildings.getValue(order.building))
            state.buildOrders.removeAt(0)
            buildOrderRepository.delete(order)
        }
        state.settleTo(now)
    }

    fun coordinates(state: CityState): Pair<Int, Int> {
        val slot = citySlotRepository.findById(state.city.slotId).orElseThrow()
        return slot.x.toInt() to slot.y.toInt()
    }

    /** The founding rows of a new city: resources at the starting stock, six buildings at level 1. */
    fun found(cityId: Long, now: Instant) {
        cityResourcesRepository.save(
            CityResources(
                cityId = cityId,
                wood = ResourceConstants.STARTING_STOCK, stone = ResourceConstants.STARTING_STOCK, iron = ResourceConstants.STARTING_STOCK,
                woodSettledAt = now, stoneSettledAt = now, ironSettledAt = now,
                population = BuildingRules.farmPop(1).toInt(),
            ),
        )
        for (b in Building.FOUNDED) {
            cityBuildingRepository.save(com.caladon.worlds.buildings.CityBuilding(com.caladon.worlds.buildings.CityBuildingId(cityId, b), 1))
        }
    }

    companion object {
        /** Points every founded city starts with: the six level-1 buildings. */
        val FOUNDING_POINTS: Int = Building.FOUNDED.sumOf { BuildingRules.points(it, 1) }.toInt()
    }
}
