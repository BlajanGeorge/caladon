package com.caladon.worlds.api

import com.caladon.worlds.rules.Building
import java.time.Instant

data class ResourceStockResponse(val stock: Long, val ratePerHour: Long)

data class CityResourcesResponse(
    val wood: ResourceStockResponse,
    val stone: ResourceStockResponse,
    val iron: ResourceStockResponse,
    /** Max stock of each resource (Deposit level). */
    val capacity: Long,
    /** The instant the stocks are settled to. */
    val serverTime: Instant,
)

data class CityBuildingResponse(val type: Building, val name: String, val level: Int, val points: Long)

data class BuildOrderResponse(
    val id: Long, val building: Building, val name: String, val targetLevel: Int,
    val startedAt: Instant, val completesAt: Instant,
)

/** The owner's view of a city, settled and advanced to `serverTime`. Every mutation returns it. */
data class CityDetailResponse(
    val id: Long,
    val name: String,
    val x: Int,
    val y: Int,
    val points: Int,
    val resources: CityResourcesResponse,
    /** Remaining free population. */
    val population: Int,
    /** Every building type, level 0 when not built. */
    val buildings: List<CityBuildingResponse>,
    /** The build queue, first entry in progress. */
    val buildQueue: List<BuildOrderResponse>,
    /** Build-queue slots at the current Town Hall level. */
    val buildQueueSlots: Int,
)

data class CostResponse(val wood: Long, val stone: Long, val iron: Long)

data class EffectResponse(val value: Long, val unit: String)

data class RequirementResponse(val building: Building, val level: Int)

data class NextLevelResponse(
    val level: Int,
    val cost: CostResponse,
    val popCost: Long,
    val points: Long,
    val effect: EffectResponse,
    val buildTimeSeconds: Long,
    /** Requirements not yet met (empty when orderable). */
    val blockedBy: List<RequirementResponse>,
)

data class BuildingViewResponse(
    val type: Building,
    val name: String,
    val level: Int,
    val maxLevel: Int,
    val founded: Boolean,
    val points: Long,
    val effect: EffectResponse,
    /** Levels of this building already in the queue. */
    val queued: Int,
    /** The next orderable level (current + queued + 1); absent at max level. */
    val next: NextLevelResponse?,
)
