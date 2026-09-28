package com.caladon.worlds.map

import com.caladon.worlds.generation.MapConstants
import com.caladon.worlds.generation.Tile
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

/** Range queries on `(world_id, x, y)` plus the bulk inserts used at world creation. */
@Repository
class MapQueryDao(private val jdbc: NamedParameterJdbcTemplate) {

    data class CityOnMap(val id: Long, val x: Int, val y: Int, val name: String, val points: Int, val owner: String)

    /** A village as the map draws it: the level says how big it is and what raiding it will meet. */
    data class BarbarianOnMap(val id: Long, val x: Int, val y: Int, val level: Int)
    data class SlotRef(val id: Long, val x: Int, val y: Int)
    data class OwnedCity(val id: Long, val x: Int, val y: Int, val name: String, val points: Int)

    fun insertSlots(worldId: Long, tiles: List<Tile>) = bulkInsert("city_slot", worldId, tiles)

    /** Villages start at level 1 with their store full, settled from [now]. */
    fun insertBarbarians(worldId: Long, tiles: List<Tile>, now: Instant) {
        val sql = "INSERT INTO barbarian_village (world_id, x, y, settled_at) VALUES (:worldId, :x, :y, :now)"
        val settledAt = Timestamp.from(now)
        val batch = tiles.map { MapSqlParameterSource(mapOf("worldId" to worldId, "x" to it.x, "y" to it.y, "now" to settledAt)) }
        batch.chunked(BATCH_SIZE).forEach { jdbc.batchUpdate(sql, it.toTypedArray()) }
    }

    private fun bulkInsert(table: String, worldId: Long, tiles: List<Tile>) {
        val sql = "INSERT INTO $table (world_id, x, y) VALUES (:worldId, :x, :y)"
        val batch = tiles.map { MapSqlParameterSource(mapOf("worldId" to worldId, "x" to it.x, "y" to it.y)) }
        batch.chunked(BATCH_SIZE).forEach { jdbc.batchUpdate(sql, it.toTypedArray()) }
    }

    fun freeSlotsIn(worldId: Long, v: Viewport): List<Tile> = jdbc.query(
        """
        SELECT s.x, s.y FROM city_slot s
        WHERE s.world_id = :worldId AND s.x BETWEEN :ax AND :bx AND s.y BETWEEN :ay AND :by
          AND NOT EXISTS (SELECT 1 FROM city c WHERE c.slot_id = s.id)
        ORDER BY s.y, s.x
        """,
        params(worldId, v),
    ) { rs, _ -> Tile(rs.getInt("x"), rs.getInt("y")) }

    fun citiesIn(worldId: Long, v: Viewport): List<CityOnMap> = jdbc.query(
        """
        SELECT c.id, s.x, s.y, c.name, c.points, u.nickname
        FROM city c
        JOIN city_slot s ON s.id = c.slot_id
        JOIN users u ON u.id = c.owner_user_id
        WHERE c.world_id = :worldId AND s.x BETWEEN :ax AND :bx AND s.y BETWEEN :ay AND :by
        ORDER BY s.y, s.x
        """,
        params(worldId, v),
    ) { rs, _ ->
        CityOnMap(rs.getLong("id"), rs.getInt("x"), rs.getInt("y"), rs.getString("name"), rs.getInt("points"), rs.getString("nickname"))
    }

    fun barbariansIn(worldId: Long, v: Viewport): List<BarbarianOnMap> = jdbc.query(
        """
        SELECT b.id, b.x, b.y, b.level FROM barbarian_village b
        WHERE b.world_id = :worldId AND b.x BETWEEN :ax AND :bx AND b.y BETWEEN :ay AND :by
        ORDER BY b.y, b.x
        """,
        params(worldId, v),
    ) { rs, _ -> BarbarianOnMap(rs.getLong("id"), rs.getInt("x"), rs.getInt("y"), rs.getInt("level")) }

    /** The village standing on a field, if any: the other thing a movement may be sent to. */
    fun barbarianIdAt(worldId: Long, x: Int, y: Int): Long? = jdbc.query(
        "SELECT b.id FROM barbarian_village b WHERE b.world_id = :worldId AND b.x = :x AND b.y = :y",
        mapOf("worldId" to worldId, "x" to x, "y" to y),
    ) { rs, _ -> rs.getLong("id") }.firstOrNull()

    /** The city standing on a field, if any: what a movement is sent to. */
    fun cityIdAt(worldId: Long, x: Int, y: Int): Long? = jdbc.query(
        """
        SELECT c.id FROM city c JOIN city_slot s ON s.id = c.slot_id
        WHERE c.world_id = :worldId AND s.x = :x AND s.y = :y
        """,
        mapOf("worldId" to worldId, "x" to x, "y" to y),
    ) { rs, _ -> rs.getLong("id") }.firstOrNull()

    fun citiesOwnedBy(worldId: Long, userId: Long): List<OwnedCity> = jdbc.query(
        """
        SELECT c.id, s.x, s.y, c.name, c.points
        FROM city c JOIN city_slot s ON s.id = c.slot_id
        WHERE c.world_id = :worldId AND c.owner_user_id = :userId
        ORDER BY c.id
        """,
        mapOf("worldId" to worldId, "userId" to userId),
    ) { rs, _ -> OwnedCity(rs.getLong("id"), rs.getInt("x"), rs.getInt("y"), rs.getString("name"), rs.getInt("points")) }

    /**
     * The "frontier" slot for a new player: the free slot nearest the centre of mass of the world's
     * cities (the map centre when the world is empty), at least [MapConstants.START_CITY_MIN_DISTANCE]
     * away from every existing city. Null when no such slot is left.
     */
    fun findStartSlot(worldId: Long): SlotRef? {
        val centre = (MapConstants.SIZE - 1) / 2.0
        val minDistSq = MapConstants.START_CITY_MIN_DISTANCE * MapConstants.START_CITY_MIN_DISTANCE
        return jdbc.query(
            """
            WITH cities AS (
                SELECT s.x, s.y FROM city c JOIN city_slot s ON s.id = c.slot_id WHERE c.world_id = :worldId
            ), centre AS (
                SELECT COALESCE(AVG(x), :centre) AS cx, COALESCE(AVG(y), :centre) AS cy FROM cities
            )
            SELECT s.id, s.x, s.y
            FROM city_slot s, centre
            WHERE s.world_id = :worldId
              AND NOT EXISTS (SELECT 1 FROM city c WHERE c.slot_id = s.id)
              AND NOT EXISTS (
                  SELECT 1 FROM cities k
                  WHERE (k.x::int - s.x) * (k.x::int - s.x) + (k.y::int - s.y) * (k.y::int - s.y) < :minDistSq
              )
            ORDER BY (s.x - centre.cx) * (s.x - centre.cx) + (s.y - centre.cy) * (s.y - centre.cy), s.id -- cx/cy are numeric: no smallint overflow
            LIMIT 1
            """,
            mapOf("worldId" to worldId, "centre" to centre, "minDistSq" to minDistSq),
        ) { rs, _ -> SlotRef(rs.getLong("id"), rs.getInt("x"), rs.getInt("y")) }.firstOrNull()
    }

    private fun params(worldId: Long, v: Viewport) =
        mapOf("worldId" to worldId, "ax" to v.startX, "bx" to v.endX, "ay" to v.startY, "by" to v.endY)

    private companion object {
        const val BATCH_SIZE = 2000
    }
}
