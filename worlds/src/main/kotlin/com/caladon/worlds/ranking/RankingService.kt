package com.caladon.worlds.ranking

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

/**
 * The world's standings (ARCHITECTURE.md → Ranking). A player's **points** are the points of the cities
 * they hold, read live from the cities themselves; their **battle points** are the population they have
 * killed, attacking and defending, kept in `player_score`.
 */
@Service
class RankingService(private val jdbc: NamedParameterJdbcTemplate) {

    /** Which figure the board is ordered by. Every row carries all of them whichever is chosen. */
    enum class Board(val column: String) {
        POINTS("points"),
        BATTLE("battle_points"),
        ATTACK("attack_points"),
        DEFENCE("defence_points"),
    }

    data class Standing(
        val rank: Long,
        val playerId: Long,
        val player: String,
        val cities: Int,
        val points: Long,
        val attackPoints: Long,
        val defencePoints: Long,
        val battlePoints: Long,
    )

    data class Page(
        val board: Board,
        val total: Long,
        val limit: Int,
        /** Where the next scroll continues from; null at the end of the board. */
        val next: String?,
        /** The caller's own standing, wherever it falls, so they never have to page to find it. */
        val me: Standing?,
        val rows: List<Standing>,
    )

    /**
     * One page of a board.
     *
     * Two ways through it, because they are wanted for different things. **`after`** is a cursor from a
     * previous page and continues exactly where that one stopped: the board is ordered by the figure and
     * then by player id, so the cursor names a point in a total order and scrolling can neither skip a
     * player nor show one twice. **`page`** is an ordinary page number for a pager, which can drop or
     * repeat a player when the board moves under it — acceptable for a page you jump to deliberately.
     *
     * [query] filters by player name but never changes a rank: a searched player keeps the rank they
     * hold in the whole world, which is the number the searcher wants.
     */
    @Transactional(readOnly = true)
    fun standings(
        worldId: Long,
        board: Board,
        limit: Int,
        after: String? = null,
        page: Int? = null,
        query: String? = null,
        meUserId: Long? = null,
    ): Page {
        val size = limit.coerceIn(1, 100)
        val cursor = after?.let(::decode)
        val offset = if (cursor != null) 0L else ((page ?: 1).coerceAtLeast(1) - 1).toLong() * size
        val like = query?.trim()?.takeIf { it.isNotEmpty() }?.let { "%" + it.replace("%", "\\%").replace("_", "\\_") + "%" }

        val term = query?.trim()?.takeIf { it.isNotEmpty() }
        val params = mutableMapOf<String, Any?>(
            "worldId" to worldId, "limit" to size, "offset" to offset, "like" to like, "term" to term,
            "cursorValue" to cursor?.value, "cursorPlayer" to cursor?.playerId,
        )
        val rows = jdbc.queryForList(
            """
            ${ranked(board)}
            SELECT * FROM ranked
            WHERE $MATCHES
              AND (
                    CAST(:cursorValue AS bigint) IS NULL
                 OR sort_value < CAST(:cursorValue AS bigint)
                 OR (sort_value = CAST(:cursorValue AS bigint) AND player_id > CAST(:cursorPlayer AS bigint))
              )
            ORDER BY rank
            LIMIT :limit OFFSET :offset
            """.trimIndent(),
            params,
        ).map(::toStanding)

        val total = jdbc.queryForObject(
            """
            ${ranked(board)}
            SELECT COUNT(*) FROM ranked WHERE $MATCHES
            """.trimIndent(),
            mapOf("worldId" to worldId, "like" to like, "term" to term),
            Long::class.java,
        ) ?: 0

        val me = meUserId?.let { id ->
            jdbc.queryForList(
                """
                ${ranked(board)}
                SELECT * FROM ranked WHERE player_id = :playerId
                """.trimIndent(),
                mapOf("worldId" to worldId, "playerId" to id),
            ).map(::toStanding).firstOrNull()
        }

        val last = rows.lastOrNull()
        val next = if (rows.size < size || last == null) null else encode(Cursor(sortValue(board, last), last.playerId))
        return Page(board = board, total = total, limit = size, next = next, me = me, rows = rows)
    }

    /** Adds what a battle earned a player: the population they killed, attacking or defending. */
    fun award(worldId: Long, userId: Long, attack: Long = 0, defence: Long = 0) {
        if (attack <= 0 && defence <= 0) return
        jdbc.update(
            """
            INSERT INTO player_score (world_id, user_id, attack_points, defence_points)
            VALUES (:worldId, :userId, :attack, :defence)
            ON CONFLICT (world_id, user_id)
            DO UPDATE SET attack_points = player_score.attack_points + EXCLUDED.attack_points,
                          defence_points = player_score.defence_points + EXCLUDED.defence_points
            """.trimIndent(),
            mapOf(
                "worldId" to worldId, "userId" to userId,
                "attack" to maxOf(0, attack), "defence" to maxOf(0, defence),
            ),
        )
    }

    /**
     * Everyone with a city in the world, or with battle points left after losing their last one, ranked
     * over the whole board. The tie-break is the player id, so the order is total: no two rows compare
     * equal, which is what lets the cursor above be exact.
     */
    private fun ranked(board: Board) = """
        WITH board AS (
            SELECT u.id AS player_id, u.nickname AS player,
                   COALESCE(c.cities, 0) AS cities, COALESCE(c.points, 0) AS points,
                   COALESCE(s.attack_points, 0) AS attack_points,
                   COALESCE(s.defence_points, 0) AS defence_points,
                   COALESCE(s.attack_points, 0) + COALESCE(s.defence_points, 0) AS battle_points
            FROM users u
            LEFT JOIN (
                SELECT owner_user_id, COUNT(*) AS cities, SUM(points) AS points
                FROM city WHERE world_id = :worldId GROUP BY owner_user_id
            ) c ON c.owner_user_id = u.id
            LEFT JOIN player_score s ON s.user_id = u.id AND s.world_id = :worldId
            WHERE c.owner_user_id IS NOT NULL OR s.user_id IS NOT NULL
        ), ranked AS (
            SELECT board.*, ${board.column} AS sort_value,
                   ROW_NUMBER() OVER (ORDER BY ${board.column} DESC, player_id ASC) AS rank
            FROM board
        )
    """.trimIndent()

    private fun toStanding(r: Map<String, Any?>) = Standing(
        rank = (r["rank"] as Number).toLong(),
        playerId = (r["player_id"] as Number).toLong(),
        player = r["player"] as String,
        cities = (r["cities"] as Number).toInt(),
        points = (r["points"] as Number).toLong(),
        attackPoints = (r["attack_points"] as Number).toLong(),
        defencePoints = (r["defence_points"] as Number).toLong(),
        battlePoints = (r["battle_points"] as Number).toLong(),
    )

    private fun sortValue(board: Board, s: Standing): Long = when (board) {
        Board.POINTS -> s.points
        Board.BATTLE -> s.battlePoints
        Board.ATTACK -> s.attackPoints
        Board.DEFENCE -> s.defencePoints
    }

    private companion object {
        /**
         * How a search matches: the name contains what was typed, or is close enough to it by trigram
         * similarity, so "ann" still finds "ana". `SIMILARITY` is pg_trgm's own default threshold.
         */
        const val SIMILARITY = 0.3
        const val MATCHES =
            "(CAST(:term AS text) IS NULL OR player ILIKE CAST(:like AS text) " +
                "OR similarity(player, CAST(:term AS text)) >= $SIMILARITY)"
    }

    private data class Cursor(val value: Long, val playerId: Long)

    private fun encode(c: Cursor): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${c.value}:${c.playerId}".toByteArray())

    /** A cursor the client hands back. Anything unreadable simply starts the board again. */
    private fun decode(raw: String): Cursor? = runCatching {
        val (value, playerId) = String(Base64.getUrlDecoder().decode(raw)).split(":")
        Cursor(value.toLong(), playerId.toLong())
    }.getOrNull()
}
