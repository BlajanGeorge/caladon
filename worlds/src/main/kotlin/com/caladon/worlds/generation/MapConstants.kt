package com.caladon.worlds.generation

/** Values identical across all worlds live here, not in the database. */
object MapConstants {
    /** The map is SIZE x SIZE unit tiles. */
    const val SIZE = 500
    const val TILES = SIZE * SIZE

    /** Largest rectangle `/map` will serve, per side (inclusive coordinates). */
    const val MAX_VIEWPORT_SIDE = 100

    /** Minimum Euclidean distance between a new player's start city and any existing city. */
    const val START_CITY_MIN_DISTANCE = 2.0
}
