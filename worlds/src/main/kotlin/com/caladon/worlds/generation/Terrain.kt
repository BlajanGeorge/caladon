package com.caladon.worlds.generation

/** Terrain tile codes as stored in the per-world blob and returned by `/map`. */
enum class Terrain(val code: Byte) {
    GRASS(0),
    FOREST(1),
    MOUNTAIN(3),
    ;

    companion object {
        fun fromCode(code: Byte): Terrain = entries.first { it.code == code }
    }
}

/** A tile position on the map. */
data class Tile(val x: Int, val y: Int)

/** Row-major index of a tile in the terrain blob. */
fun tileIndex(x: Int, y: Int): Int = y * MapConstants.SIZE + x
