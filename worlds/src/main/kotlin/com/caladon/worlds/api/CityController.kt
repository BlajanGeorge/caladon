package com.caladon.worlds.api

import com.caladon.users.security.AuthenticatedUser
import com.caladon.worlds.army.ArmyService
import com.caladon.worlds.army.CitySupportRepository
import com.caladon.worlds.buildings.BuildingService
import com.caladon.worlds.domain.Resource
import com.caladon.worlds.resources.CityAccess
import com.caladon.worlds.resources.CityState
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.BuildingRules
import com.caladon.worlds.rules.Unit
import com.caladon.worlds.rules.UnitRules
import org.springframework.web.bind.annotation.RequestBody
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
    private val armyService: ArmyService,
    private val citySupportRepository: CitySupportRepository,
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
                description = v.building.description, effectLabel = v.building.effectLabel,
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

    @GetMapping("/army")
    fun army(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long): List<UnitViewResponse> {
        val (_, views) = armyService.list(worldId, cityId, user.id, user.role)
        return views.map { v ->
            val u = v.unit
            UnitViewResponse(
                type = u, name = u.displayName, role = u.role, count = v.count,
                cost = CostResponse(u.cost.wood, u.cost.stone, u.cost.iron), population = u.population,
                attack = u.attack, defence = u.defence, defenceCavalry = u.defenceCavalry, defenceArcher = u.defenceArcher,
                speed = u.speed, carry = u.carry, barracksLevel = u.barracksLevel, academyLevel = u.academyLevel,
                recruitSeconds = v.recruitSeconds, studied = v.studied, studyCompletesAt = v.studyCompletesAt,
                studyCost = u.studyCost?.let { CostResponse(it.wood, it.stone, it.iron) }, studySeconds = v.studySeconds,
                studyBlockedBy = v.studyBlockedBy.map { RequirementResponse(it.building, it.level) },
                blockedBy = v.blockedBy.map { RequirementResponse(it.building, it.level) },
                recruitable = v.blockedBy.isEmpty() && v.studied,
            )
        }
    }

    @PostMapping("/army/recruit")
    fun recruit(
        @AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long,
        @RequestBody request: RecruitRequest,
    ): CityDetailResponse = toDetail(armyService.recruit(worldId, cityId, user.id, user.role, request.unit, request.count))

    @PostMapping("/army/study")
    fun study(
        @AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long,
        @RequestBody request: StudyRequest,
    ): CityDetailResponse = toDetail(armyService.study(worldId, cityId, user.id, user.role, request.unit))

    @DeleteMapping("/study-orders/{unit}")
    fun cancelStudy(
        @AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long,
        @PathVariable unit: Unit,
    ): CityDetailResponse = toDetail(armyService.cancelStudy(worldId, cityId, user.id, user.role, unit))

    @DeleteMapping("/recruit-orders/{orderId}")
    fun cancelRecruit(
        @AuthenticationPrincipal user: AuthenticatedUser, @PathVariable worldId: Long, @PathVariable cityId: Long,
        @PathVariable orderId: Long,
    ): CityDetailResponse = toDetail(armyService.cancel(worldId, cityId, user.id, user.role, orderId))

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
                val cost = BuildingRules.cost(it.building, it.targetLevel)
                BuildOrderResponse(
                    requireNotNull(it.id), it.building, it.building.displayName, it.targetLevel, it.startedAt, it.completesAt,
                    CostResponse(cost.wood, cost.stone, cost.iron), BuildingRules.popCost(it.building, it.targetLevel),
                )
            },
            buildQueueSlots = BuildingRules.queueSlots(state.level(Building.TOWN_HALL)),
            units = unitCounts(state),
            recruitQueue = recruitQueue(state),
            recruitQueueSlots = BuildingRules.recruitSlots(state.level(Building.BARRACKS)),
            studied = Unit.entries.filter { it.needsStudy && state.isStudied(it) },
            studyQueue = state.studyQueue().mapIndexed { i, s -> StudyOrderResponse(s.id.unit, s.id.unit.displayName, i + 1, s.orderedAt, s.completesAt) },
            studyQueueSlots = BuildingRules.studySlots(state.level(Building.ACADEMY)),
        )
    }

    /** Estimated completion of every recruit order at the current Barracks level, chained from the head. */
    /** Own troops at home, foreign troops sheltering here, and this city's troops away as support. */
    private fun unitCounts(state: CityState): List<CityUnitResponse> {
        val hosted = citySupportRepository.findAllByIdHostCityId(state.cityId)
            .filter { it.id.ownerCityId != state.city.id }
            .groupBy { it.id.unit }.mapValues { (_, v) -> v.sumOf { it.count } }
        val away = citySupportRepository.findAllByIdOwnerCityId(state.cityId)
            .filter { it.id.hostCityId != state.city.id }
            .groupBy { it.id.unit }.mapValues { (_, v) -> v.sumOf { it.count } }
        return Unit.entries.map {
            val home = state.units[it]?.count ?: 0
            CityUnitResponse(it, it.displayName, home, home, hosted[it] ?: 0, away[it] ?: 0)
        }
    }

    private fun recruitQueue(state: CityState): List<RecruitOrderResponse> {
        val barracks = state.level(Building.BARRACKS)
        var t = state.now
        return state.recruitOrders.map { o ->
            val per = Math.round(UnitRules.recruitSeconds(o.unit, barracks) / com.caladon.worlds.resources.ResourceConstants.WORLD_SPEED)
            val first = o.nextCompletesAt ?: t.plusSeconds(per)
            val end = first.plusSeconds(per * (o.remaining - 1).coerceAtLeast(0))
            t = end
            RecruitOrderResponse(requireNotNull(o.id), o.unit, o.unit.displayName, o.count, o.remaining, o.nextCompletesAt, end)
        }
    }
}
