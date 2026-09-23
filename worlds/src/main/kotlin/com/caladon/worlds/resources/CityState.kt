package com.caladon.worlds.resources

import com.caladon.worlds.buildings.CityBuildOrder
import com.caladon.worlds.buildings.CityBuilding
import com.caladon.worlds.buildings.CityBuildingId
import com.caladon.worlds.domain.City
import com.caladon.worlds.domain.CityResources
import com.caladon.worlds.domain.Resource
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.BuildingRules
import com.caladon.worlds.rules.Cost
import com.caladon.worlds.rules.Production
import java.time.Instant

/**
 * Everything about one city that gameplay touches, loaded under the city's row lock and advanced to
 * `now` (see [CityAccess]). Mutations happen on these objects; JPA flushes them at commit.
 */
class CityState(
    val city: City,
    val resources: CityResources,
    val buildings: MutableMap<Building, CityBuilding>,
    val buildOrders: MutableList<CityBuildOrder>,
    var now: Instant,
) {
    val cityId: Long get() = resources.cityId

    fun level(b: Building): Int = buildings[b]?.level ?: 0

    fun levels(): Map<Building, Int> = Building.entries.associateWith { level(it) }

    fun rate(r: Resource): Double = BuildingRules.production(level(producer(r))) * ResourceConstants.WORLD_SPEED

    fun capacity(): Long = BuildingRules.capacity(level(Building.DEPOSIT))

    /** Whole-unit settlement of the three stocks up to [instant] with the current levels. */
    fun settleTo(instant: Instant) {
        val cap = capacity()
        for (r in Resource.entries) {
            val s = Production.settle(resources.stock(r), rate(r), cap, resources.settledAt(r), instant)
            resources.setStock(r, s.stock)
            resources.setSettledAt(r, s.settledAt)
        }
    }

    /** Applies a completed level: the row, the points counter, and the Farm's population credit. */
    fun completeLevel(b: Building, level: Int) {
        val row = buildings[b]
        if (row == null) buildings[b] = CityBuilding(CityBuildingId(cityId, b), level) else row.level = level
        city.points += (BuildingRules.points(b, level) - BuildingRules.points(b, level - 1)).toInt()
        if (b == Building.FARM) resources.population += BuildingRules.farmGain(level).toInt()
    }

    fun shortfall(cost: Cost): Map<Resource, Long> = buildMap {
        if (resources.wood < cost.wood) put(Resource.WOOD, cost.wood - resources.wood)
        if (resources.stone < cost.stone) put(Resource.STONE, cost.stone - resources.stone)
        if (resources.iron < cost.iron) put(Resource.IRON, cost.iron - resources.iron)
    }

    fun pay(cost: Cost, population: Long) {
        resources.wood -= cost.wood; resources.stone -= cost.stone; resources.iron -= cost.iron
        resources.population -= population.toInt()
    }

    /** A refund is an addition through the settle path: capped at capacity. */
    fun refund(cost: Cost, population: Long) {
        val cap = capacity()
        resources.wood = minOf(cap, resources.wood + cost.wood)
        resources.stone = minOf(cap, resources.stone + cost.stone)
        resources.iron = minOf(cap, resources.iron + cost.iron)
        resources.population += population.toInt()
    }

    companion object {
        fun producer(r: Resource): Building = when (r) {
            Resource.WOOD -> Building.WOODCUTTER
            Resource.STONE -> Building.STONE_MINE
            Resource.IRON -> Building.IRON_MINE
        }
    }
}
