package com.caladon.worlds.generation

import org.springframework.stereotype.Component
import java.util.Random

/**
 * Places city slots and barbarian villages on GRASS tiles using jittered grids:
 * the map is split into `cell x cell` squares and each square yields at most one candidate
 * (its origin plus a random in-cell offset), which guarantees at most one entity per cell.
 */
@Component
class PlacementGenerator {

    data class Placement(val slots: List<Tile>, val barbarians: List<Tile>)

    fun generate(terrain: ByteArray, seed: Long): Placement {
        require(terrain.size == MapConstants.TILES) { "terrain must have ${MapConstants.TILES} tiles" }
        val occupancy = ByteArray(MapConstants.TILES) // 0 free, 1 slot, 2 barbarian

        val slots = jitteredGrid(SLOT_CELL, Random(seed)) { x, y ->
            terrain[tileIndex(x, y)] == Terrain.GRASS.code
        }
        for (t in slots) occupancy[tileIndex(t.x, t.y)] = 1

        // Candidates only need to be GRASS here; distance rejection happens below, in order, so that
        // each accepted village also keeps earlier villages at bay.
        val candidates = jitteredGrid(BARBARIAN_CELL, Random(seed xor BARBARIAN_SEED_SALT)) { x, y ->
            terrain[tileIndex(x, y)] == Terrain.GRASS.code
        }
        val accepted = ArrayList<Tile>(candidates.size)
        for (t in candidates) {
            if (hasNeighbourWithin(occupancy, t.x, t.y, BARBARIAN_MIN_DISTANCE)) continue
            occupancy[tileIndex(t.x, t.y)] = 2
            accepted += t
        }
        return Placement(slots, accepted)
    }

    private inline fun jitteredGrid(cell: Int, random: Random, accept: (Int, Int) -> Boolean): List<Tile> {
        val result = ArrayList<Tile>()
        var cy = 0
        while (cy < MapConstants.SIZE) {
            var cx = 0
            while (cx < MapConstants.SIZE) {
                val x = cx + random.nextInt(cell)
                val y = cy + random.nextInt(cell)
                if (x < MapConstants.SIZE && y < MapConstants.SIZE && accept(x, y)) result += Tile(x, y)
                cx += cell
            }
            cy += cell
        }
        return result
    }

    /** True if any occupied tile lies strictly closer than [minDistance] (Euclidean) to (x, y). */
    private fun hasNeighbourWithin(occupancy: ByteArray, x: Int, y: Int, minDistance: Int): Boolean {
        val limitSq = minDistance * minDistance
        for (dy in -minDistance..minDistance) {
            val ny = y + dy
            if (ny !in 0 until MapConstants.SIZE) continue
            for (dx in -minDistance..minDistance) {
                val nx = x + dx
                if (nx !in 0 until MapConstants.SIZE) continue
                if (dx * dx + dy * dy < limitSq && occupancy[tileIndex(nx, ny)] != 0.toByte()) return true
            }
        }
        return false
    }

    companion object {
        /** Slot grid cell side = minimum slot spacing ("C" in the architecture doc). */
        const val SLOT_CELL = 3
        /** Barbarian grid cell side; coarser than slots so farms interleave with cities. */
        const val BARBARIAN_CELL = 4
        /** No barbarian village closer than this to a slot or another village ("D"). */
        const val BARBARIAN_MIN_DISTANCE = 2
        private const val BARBARIAN_SEED_SALT = 0x2545F4914F6CDD1DL
    }
}
