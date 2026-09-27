package com.caladon.worlds.ranking

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The world's standings (ARCHITECTURE.md → Ranking). A player's **points** are simply the points of the
 * cities they hold; their **battle points** are the population they have killed — attacking, defending,
 * and the two added together.
 */
@Service
class RankingService(private val jdbc: NamedParameterJdbcTemplate) {

    data class Standing(
        val rank: Int,
        val userId: Long,
        val player: String,
        val cities: Int,
        val points: Long,
        val attackPoints: Long,
        val defencePoints: Long,
    ) {
        val battlePoints: Long get() = attackPoints + defencePoints
    }

    /**
     * Everyone with a city in the world, or with battle points left in it after losing their last one,
     * best first: by city points, then by what they have killed, then by name so the order never wobbles.
     */
    @Transactional(readOnly = true)
    fun standings(worldId: Long, limit: Int): List<Standing> {
        val rows = jdbc.queryForList(
            """
            SELECT u.id AS user_id, u.nickname,
                   COALESCE(c.cities, 0) AS cities, COALESCE(c.points, 0) AS points,
                   COALESCE(s.attack_points, 0) AS attack_points, COALESCE(s.defence_points, 0) AS defence_points
            FROM users u
            LEFT JOIN (
                SELECT owner_user_id, COUNT(*) AS cities, SUM(points) AS points
                FROM city WHERE world_id = :worldId GROUP BY owner_user_id
            ) c ON c.owner_user_id = u.id
            LEFT JOIN player_score s ON s.user_id = u.id AND s.world_id = :worldId
            WHERE c.owner_user_id IS NOT NULL OR s.user_id IS NOT NULL
            ORDER BY points DESC,
                     (COALESCE(s.attack_points, 0) + COALESCE(s.defence_points, 0)) DESC,
                     u.nickname
            LIMIT :limit
            """.trimIndent(),
            mapOf("worldId" to worldId, "limit" to limit),
        )
        return rows.mapIndexed { i, r ->
            Standing(
                rank = i + 1,
                userId = (r["user_id"] as Number).toLong(),
                player = r["nickname"] as String,
                cities = (r["cities"] as Number).toInt(),
                points = (r["points"] as Number).toLong(),
                attackPoints = (r["attack_points"] as Number).toLong(),
                defencePoints = (r["defence_points"] as Number).toLong(),
            )
        }
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
}
