package com.caladon.worlds.generation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class TerrainGeneratorTest {
    private val generator = TerrainGenerator()

    @Test
    fun `is deterministic for a seed and differs between seeds`() {
        val a = generator.generate(42)
        assertThat(a).hasSize(MapConstants.TILES).isEqualTo(generator.generate(42))
        assertThat(generator.generate(43)).isNotEqualTo(a)
    }

    @Test
    fun `about two thirds of the map is grass`() {
        val terrain = generator.generate(7)
        val share = Terrain.entries.associateWith { t -> terrain.count { it == t.code }.toDouble() / MapConstants.TILES }

        assertThat(share[Terrain.GRASS]).isBetween(0.62, 0.70)
        assertThat(share[Terrain.MOUNTAIN]).isCloseTo(0.12, within(0.01))
        assertThat(share[Terrain.LAKE]).isCloseTo(0.10, within(0.01))
        assertThat(share[Terrain.FOREST]).isCloseTo(0.12, within(0.01))
        assertThat(share.values.sum()).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `features form contiguous patches rather than noise`() {
        val terrain = generator.generate(99)
        // Fraction of horizontally adjacent tile pairs that share a terrain code; random noise would give ~0.5.
        var same = 0
        var pairs = 0
        for (y in 0 until MapConstants.SIZE) for (x in 0 until MapConstants.SIZE - 1) {
            pairs++
            if (terrain[tileIndex(x, y)] == terrain[tileIndex(x + 1, y)]) same++
        }
        assertThat(same.toDouble() / pairs).isGreaterThan(0.95)
    }
}
