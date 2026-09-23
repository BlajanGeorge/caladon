package com.caladon.worlds.api

import com.caladon.users.security.AuthenticatedUser
import com.caladon.worlds.buildings.BuildingService
import com.caladon.worlds.domain.Resource
import com.caladon.worlds.resources.CityAccess
import com.caladon.worlds.resources.CityState
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.BuildingRules
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.server.ResponseStatusException

/** Owner-only city endpoints: detail, buildings and the build queue. */
@RestController
@RequestMapping("/api/v1/worlds/{worldId}/cities/{cityId}")
class CityController(
    private val cityAccess: CityAccess,
    private val buildingService: BuildingService,
) {
    @GetMapping
    @Transactional
    fun detail(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long): CityDetailResponse =
        toDetail(cityAccess.open(worldId, cityId, user.id, user.role))

    @GetMapping("/buildings")
    fun buildings(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long): List<BuildingViewResponse> {
        val (_, views) = buildingService.list(worldId, cityId, user.id, user.role)
        return views.map { v ->
            BuildingViewResponse(
                type = v.building, name = v.building.displayName, level = v.level, maxLevel = v.building.maxLevel,
                founded = v.building.founded, points = v.points, effect = EffectResponse(v.effect.value, v.effect.unit),
                queued = v.queued,
                next = v.next?.let { n ->
                    NextLevelResponse(
                        level = n.level, cost = CostResponse(n.cost.wood, n.cost.stone, n.cost.iron), popCost = n.popCost,
                        points = n.points, effect = EffectResponse(n.effect.value, n.effect.unit),
                        buildTimeSeconds = n.buildTimeSeconds, blockedBy = n.blockedBy.map { RequirementResponse(it.building, it.level) },
                    )
                },
            )
        }
    }

    @PostMapping("/buildings/{building}/upgrade")
    fun upgrade(
        @AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long,
        @PathVariable building: String,
    ): CityDetailResponse {
        val b = Building.entries.firstOrNull { it.name.equals(building, ignoreCase = true) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "BUILDING_NOT_FOUND")
        return toDetail(buildingService.upgrade(worldId, cityId, user.id, user.role, b))
    }

    @DeleteMapping("/build-orders/{orderId}")
    fun cancelBuild(
        @AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long,
        @PathVariable orderId: Long,
    ): CityDetailResponse = toDetail(buildingService.cancel(worldId, cityId, user.id, user.role, orderId))

    private fun toDetail(state: CityState): CityDetailResponse {
        val (x, y) = cityAccess.coordinates(state)
        fun stock(r: Resource) = ResourceStockResponse(state.resources.stock(r), Math.round(state.rate(r)))
        return CityDetailResponse(
            id = state.cityId, name = state.city.name, x = x, y = y, points = state.city.points,
            resources = CityResourcesResponse(
                wood = stock(Resource.WOOD), stone = stock(Resource.STONE), iron = stock(Resource.IRON),
                capacity = state.capacity(), serverTime = state.now,
            ),
            population = state.resources.population,
            buildings = Building.entries.map { b ->
                val level = state.level(b)
                CityBuildingResponse(b, b.displayName, level, BuildingRules.points(b, level))
            },
            buildQueue = state.buildOrders.map {
                BuildOrderResponse(requireNotNull(it.id), it.building, it.building.displayName, it.targetLevel, it.startedAt, it.completesAt)
            },
            buildQueueSlots = BuildingRules.queueSlots(state.level(Building.TOWN_HALL)),
        )
    }
}
