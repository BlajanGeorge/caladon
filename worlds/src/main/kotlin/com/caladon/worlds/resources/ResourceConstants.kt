package com.caladon.worlds.resources

import java.math.BigDecimal

/**
 * Economy values identical across all worlds. Until buildings exist these are the level-1 values
 * of the Woodcutter / Stone Mine / Iron Mine (production), the Deposit (capacity) and the Farm
 * (population) that every city is founded with.
 */
object ResourceConstants {
    /** Stock of each resource a city is founded with. */
    val STARTING_STOCK: BigDecimal = BigDecimal("500")

    /** Units of each resource produced per minute at building level 1. */
    const val PRODUCTION_PER_MINUTE = 30.0

    /** Maximum stock of each resource at Deposit level 1. Production above it is lost. */
    const val CAPACITY = 2000

    /** Free population a city is founded with (Farm level 1). */
    const val STARTING_POPULATION = 100

    /** Multiplies every production rate. */
    const val WORLD_SPEED = 1.0
}
