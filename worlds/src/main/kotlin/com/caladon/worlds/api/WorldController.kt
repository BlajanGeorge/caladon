package com.caladon.worlds.api

import com.caladon.users.security.AuthenticatedUser
import com.caladon.worlds.map.Viewport
import com.caladon.worlds.ranking.RankingService
import com.caladon.worlds.service.WorldException
import com.caladon.worlds.service.WorldService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/worlds")
class WorldController(
    private val worldService: WorldService,
    private val rankingService: RankingService,
) {

    /**
     * One page of the world's standings, best first. Ordered by city points unless another `board` is
     * asked for; `after` continues a scroll exactly, `page` jumps to a page, `q` searches by name without
     * changing anyone's rank. Any player who has joined the world may read it.
     */
    @GetMapping("/{id}/ranking")
    fun ranking(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "points") board: String,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) q: String?,
    ): RankingResponse {
        worldService.requirePlayable(id, user.id)
        val which = RankingService.Board.entries.firstOrNull { it.name.equals(board, ignoreCase = true) }
            ?: throw WorldException.InvalidBoard(board)
        val result = rankingService.standings(id, which, limit, after, page, q, meUserId = user.id)
        return RankingResponse(
            board = result.board.name.lowercase(),
            total = result.total,
            limit = result.limit,
            next = result.next,
            me = result.me?.let(::toStanding),
            rows = result.rows.map(::toStanding),
        )
    }

    private fun toStanding(s: RankingService.Standing) = StandingResponse(
        rank = s.rank, playerId = s.playerId, player = s.player, cities = s.cities, points = s.points,
        attackPoints = s.attackPoints, defencePoints = s.defencePoints, battlePoints = s.battlePoints,
    )

    /** PLAYABLE worlds only; `joined` tells the UI whether to show "Play" or "Join". */
    @GetMapping
    fun list(@AuthenticationPrincipal user: AuthenticatedUser): List<PlayableWorldResponse> =
        worldService.listPlayable(user.id).map { (w, joined) -> PlayableWorldResponse(requireNotNull(w.id), w.name, joined) }

    @GetMapping("/mine")
    fun mine(@AuthenticationPrincipal user: AuthenticatedUser): List<MyWorldResponse> =
        worldService.listMine(user.id).map { MyWorldResponse(requireNotNull(it.id), it.name) }

    @PostMapping("/{id}/join")
    fun join(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: Long): JoinResponse {
        val result = worldService.join(id, user.id)
        val c = result.startCity
        return JoinResponse(result.worldId, StartCityResponse(c.id, c.x, c.y, c.name, c.points))
    }

    /** The caller's cities in the world (today: the start city). */
    @GetMapping("/{id}/cities/mine")
    fun myCities(@AuthenticationPrincipal user: AuthenticatedUser, @PathVariable id: Long): List<OwnedCityResponse> =
        worldService.myCities(id, user.id).map { OwnedCityResponse(it.id, it.x, it.y, it.name, it.points) }

    @GetMapping("/{id}/map")
    fun map(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable id: Long,
        @RequestParam startX: Int,
        @RequestParam startY: Int,
        @RequestParam endX: Int,
        @RequestParam endY: Int,
    ): MapResponse {
        val view = worldService.map(id, Viewport(startX, startY, endX, endY), user.role)
        return MapResponse(
            startX = view.viewport.startX,
            startY = view.viewport.startY,
            endX = view.viewport.endX,
            endY = view.viewport.endY,
            terrain = view.terrain,
            slots = view.slots.map { TileResponse(it.x, it.y) },
            cities = view.cities.map { CityResponse(it.id, it.x, it.y, it.name, it.points, it.owner) },
            barbarians = view.barbarians.map { TileResponse(it.x, it.y) },
        )
    }
}
