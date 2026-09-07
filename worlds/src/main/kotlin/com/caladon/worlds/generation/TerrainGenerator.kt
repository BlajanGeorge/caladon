package com.caladon.worlds.generation

import org.springframework.stereotype.Component

/**
 * Builds the terrain grid from two smooth noise fields, `elevation` and `moisture`.
 *
 * Each field is rank-normalised to `0..1` over the whole map, so the thresholds below are exact
 * map fractions (e.g. `MOUNTAIN_ABOVE = 0.90` means the highest 10% of tiles become mountains).
 * This keeps the ~60–70% GRASS target stable regardless of the noise distribution.
 */
@Component
class TerrainGenerator {

    fun generate(seed: Long): ByteArray {
        val elevation = rankNormalise(field(PerlinNoise(seed), ELEVATION_SCALE))
        val moisture = rankNormalise(field(PerlinNoise(seed xor MOISTURE_SEED_SALT), MOISTURE_SCALE))

        val terrain = ByteArray(MapConstants.TILES)
        for (i in terrain.indices) {
            val e = elevation[i]
            terrain[i] = when {
                e > MOUNTAIN_ABOVE -> Terrain.MOUNTAIN
                e < FOREST_BELOW -> Terrain.FOREST
                moisture[i] > FOREST_ABOVE -> Terrain.FOREST
                else -> Terrain.GRASS
            }.code
        }
        return terrain
    }

    private fun field(noise: PerlinNoise, scale: Double): DoubleArray {
        val values = DoubleArray(MapConstants.TILES)
        for (y in 0 until MapConstants.SIZE) {
            for (x in 0 until MapConstants.SIZE) {
                values[tileIndex(x, y)] = noise.fbm(x / scale, y / scale, octaves = 3)
            }
        }
        return values
    }

    /** Maps each value to its rank / N, i.e. the fraction of tiles with a smaller value. */
    private fun rankNormalise(values: DoubleArray): FloatArray {
        val order = values.indices.sortedBy { values[it] }
        val out = FloatArray(values.size)
        for ((rank, index) in order.withIndex()) out[index] = rank.toFloat() / values.size
        return out
    }

    companion object {
        /** Tile span of one noise cell: larger = broader features. ~20 gives patches of ~10–25 tiles
         *  scattered through the map rather than a few continent-sized blobs. */
        const val ELEVATION_SCALE = 20.0
        const val MOISTURE_SCALE = 16.0
        private const val MOISTURE_SEED_SALT = 0x5DEECE66DL

        // Fractions of the map (see class doc): 12% mountain, the lowest 10% of tiles forest, plus
        // 15% of the rest forest by moisture, so GRASS ≈ 66% and FOREST ≈ 22%.
        const val MOUNTAIN_ABOVE = 0.88f
        const val FOREST_BELOW = 0.10f
        const val FOREST_ABOVE = 0.85f
    }
}
