package com.caladon.worlds.generation

import org.springframework.stereotype.Component
import java.util.Random

/**
 * Places city slots and barbarian villages on GRASS tiles using jittered grids: the map is split
 * into `cell x cell` squares and each square yields at most one candidate (its origin plus a random
 * in-cell offset). Candidates are then filtered for a grass buffer and a true minimum distance.
 */
@Component
class PlacementGenerator {

    data class Placement(val slots: List<Tile>, val barbarians: List<Tile>)

    fun generate(terrain: ByteArray, seed: Long): Placement {
        require(terrain.size == MapConstants.TILES) { "terrain must have ${MapConstants.TILES} tiles" }
        val occupancy = ByteArray(MapConstants.TILES) // 0 free, 1 slot, 2 barbarian

        // Candidates come from the jittered grid; a candidate is accepted only if it sits on open
        // grass (a GRASS buffer around it, so no terrain object overhangs it) and keeps the minimum
        // distance from everything accepted before it.
        val slots = ArrayList<Tile>()
        for (t in jitteredGrid(SLOT_CELL, Random(seed)) { x, y -> isOpenGrass(terrain, x, y) }) {
            if (hasNeighbourWithin(occupancy, t.x, t.y, SLOT_MIN_DISTANCE)) continue
            occupancy[tileIndex(t.x, t.y)] = 1
            slots += t
        }

        val barbarians = ArrayList<Tile>()
        for (t in jitteredGrid(BARBARIAN_CELL, Random(seed xor BARBARIAN_SEED_SALT)) { x, y -> isOpenGrass(terrain, x, y) }) {
            if (hasNeighbourWithin(occupancy, t.x, t.y, BARBARIAN_MIN_DISTANCE)) continue
            occupancy[tileIndex(t.x, t.y)] = 2
            barbarians += t
        }
        return Placement(slots, barbarians)
    }

    /** GRASS at (x, y) and on every tile within [TERRAIN_BUFFER] (Chebyshev), inside the map. */
    private fun isOpenGrass(terrain: ByteArray, x: Int, y: Int): Boolean {
        for (dy in -TERRAIN_BUFFER..TERRAIN_BUFFER) {
            for (dx in -TERRAIN_BUFFER..TERRAIN_BUFFER) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until MapConstants.SIZE || ny !in 0 until MapConstants.SIZE) return false
                if (terrain[tileIndex(nx, ny)] != Terrain.GRASS.code) return false
            }
        }
        return true
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
        /** Slot grid cell side ("C" in the architecture doc): one candidate per cell. */
        const val SLOT_CELL = 5
        /** No slot closer than this (Euclidean) to another slot. */
        const val SLOT_MIN_DISTANCE = 4
        /** Slots and villages need GRASS on every tile within this Chebyshev distance. */
        const val TERRAIN_BUFFER = 2
        /** Barbarian grid cell side; coarser than slots so farms interleave with cities. */
        const val BARBARIAN_CELL = 9
        /** No barbarian village closer than this to a slot or another village ("D"). */
        const val BARBARIAN_MIN_DISTANCE = 3
        private const val BARBARIAN_SEED_SALT = 0x2545F4914F6CDD1DL
    }
}
