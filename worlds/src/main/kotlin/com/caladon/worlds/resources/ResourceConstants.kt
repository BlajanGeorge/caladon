package com.caladon.worlds.resources

/** Economy values identical across all worlds (the per-level tables live in `rules`). */
object ResourceConstants {
    /** Stock of each resource a city is founded with. */
    const val STARTING_STOCK = 500L

    /** Multiplies every production rate (and, later, divides every build/recruit/study time). */
    const val WORLD_SPEED = 1.0
}
