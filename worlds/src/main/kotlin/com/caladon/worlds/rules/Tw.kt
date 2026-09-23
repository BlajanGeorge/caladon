package com.caladon.worlds.rules

import kotlin.math.floor
import kotlin.math.pow

/** Tribal Wars arithmetic: round-half-up and `base × factor^(level−1)`. */
object Tw {
    fun round(v: Double): Long = floor(v + 0.5).toLong()

    fun curve(base: Double, factor: Double, level: Int): Long = round(base * factor.pow(level - 1))
}
