package com.caladon.worlds.api

import com.caladon.worlds.domain.WorldState
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateWorldRequest(
    @field:NotBlank @field:Size(min = 2, max = 64)
    val name: String,
)

data class WorldResponse(val id: Long, val name: String, val state: WorldState)

data class AdminWorldResponse(val id: Long, val name: String, val state: WorldState, val players: Long, val createdAt: Instant)

data class PlayableWorldResponse(val id: Long, val name: String, val joined: Boolean)

data class MyWorldResponse(val id: Long, val name: String)

data class OwnedCityResponse(val id: Long, val x: Int, val y: Int, val name: String, val points: Int)

data class StartCityResponse(val id: Long, val x: Int, val y: Int, val name: String, val points: Int)

data class JoinResponse(val worldId: Long, val startCity: StartCityResponse)

data class TileResponse(val x: Int, val y: Int)

data class CityResponse(val id: Long, val x: Int, val y: Int, val name: String, val points: Int, val owner: String)

data class MapResponse(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    /** One terrain code per tile, flat row-major from (startX, startY): 0=GRASS 1=FOREST 3=MOUNTAIN. */
    val terrain: IntArray,
    /** Free (unoccupied) city slots only. */
    val slots: List<TileResponse>,
    val cities: List<CityResponse>,
    val barbarians: List<TileResponse>,
)

data class ResourceStockResponse(val stock: Long, val ratePerMinute: Double)

data class CityResourcesResponse(
    val wood: ResourceStockResponse,
    val stone: ResourceStockResponse,
    val iron: ResourceStockResponse,
    /** Max stock of each resource. */
    val capacity: Int,
    /** The instant the stocks are settled to; the client extrapolates from here. */
    val serverTime: Instant,
)

data class CityDetailResponse(
    val id: Long,
    val name: String,
    val x: Int,
    val y: Int,
    val points: Int,
    val resources: CityResourcesResponse,
    /** Remaining free population. */
    val population: Int,
)
