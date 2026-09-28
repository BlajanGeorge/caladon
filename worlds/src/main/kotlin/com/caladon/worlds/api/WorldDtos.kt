package com.caladon.worlds.api

import com.caladon.worlds.domain.WorldState
import com.caladon.worlds.report.ReportService
import com.fasterxml.jackson.databind.JsonNode
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


/**
 * A player's standing in a world: the points of the cities they hold, and the battle points they have
 * earned — the population they have killed attacking and defending.
 */
data class StandingResponse(
    val rank: Long,
    val playerId: Long,
    val player: String,
    val cities: Int,
    val points: Long,
    val attackPoints: Long,
    val defencePoints: Long,
    val battlePoints: Long,
)

/**
 * One page of a board. [next] continues an endless scroll exactly where this page stopped and is null at
 * the end; [me] is the caller's own standing wherever it falls.
 */
data class RankingResponse(
    val board: String,
    val total: Long,
    val limit: Int,
    val next: String?,
    val me: StandingResponse?,
    val rows: List<StandingResponse>,
)

/**
 * One line of the report list: enough to show it without opening it. [won] is null where winning means
 * nothing, and [otherCity] and [otherPlayer] name the other end of what happened.
 */
data class ReportRowResponse(
    val id: Long,
    val kind: ReportService.Kind,
    val createdAt: Instant,
    val read: Boolean,
    val subjectCity: String,
    val otherCity: String?,
    val otherPlayer: String?,
    val won: Boolean?,
    val summary: String,
)

/** One page of a player's reports, newest first; [unread] counts the whole world, for the top bar. */
data class ReportsResponse(
    val unread: Long,
    val total: Long,
    val limit: Int,
    val page: Int,
    val rows: List<ReportRowResponse>,
)

/** One report with its snapshot; the shape of [payload] follows the kind (ARCHITECTURE.md → Reports). */
data class ReportResponse(
    val id: Long,
    val kind: ReportService.Kind,
    val createdAt: Instant,
    val read: Boolean,
    val subjectCity: String,
    val otherCity: String?,
    val otherPlayer: String?,
    val won: Boolean?,
    val summary: String,
    val payload: JsonNode?,
)
