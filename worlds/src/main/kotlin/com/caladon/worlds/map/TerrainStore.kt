package com.caladon.worlds.map

import com.caladon.worlds.generation.MapConstants
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/** Reads/writes the per-world terrain blob (kept out of the JPA entity because of its size). */
@Repository
class TerrainStore(private val jdbc: JdbcTemplate) {

    fun write(worldId: Long, terrain: ByteArray) {
        require(terrain.size == MapConstants.TILES)
        jdbc.update("UPDATE worlds SET terrain = ? WHERE id = ?", terrain, worldId)
    }

    fun read(worldId: Long): ByteArray? =
        jdbc.query("SELECT terrain FROM worlds WHERE id = ?", { rs, _ -> rs.getBytes("terrain") }, worldId)
            .firstOrNull()
}
