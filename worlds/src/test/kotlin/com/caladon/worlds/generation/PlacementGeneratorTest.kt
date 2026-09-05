package com.caladon.worlds.generation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.hypot

class PlacementGeneratorTest {
    private val terrain = TerrainGenerator().generate(1234)
    private val placement = PlacementGenerator().generate(terrain, 1234)

    @Test
    fun `is deterministic`() {
        assertThat(PlacementGenerator().generate(terrain, 1234)).isEqualTo(placement)
        assertThat(PlacementGenerator().generate(terrain, 4321)).isNotEqualTo(placement)
    }

    @Test
    fun `places roughly eighteen thousand slots, all on grass, at most one per cell`() {
        assertThat(placement.slots.size).isBetween(17_000, 22_000)
        assertThat(placement.slots).allSatisfy { assertThat(terrain[tileIndex(it.x, it.y)]).isEqualTo(Terrain.GRASS.code) }

        val cells = placement.slots.map { (it.x / PlacementGenerator.SLOT_CELL) to (it.y / PlacementGenerator.SLOT_CELL) }
        assertThat(cells).doesNotHaveDuplicates()
    }

    @Test
    fun `barbarian villages sit on grass and keep their distance from slots and each other`() {
        assertThat(placement.barbarians.size).isBetween(2_000, 6_000)
        assertThat(placement.barbarians).allSatisfy { assertThat(terrain[tileIndex(it.x, it.y)]).isEqualTo(Terrain.GRASS.code) }

        val minDistance = PlacementGenerator.BARBARIAN_MIN_DISTANCE.toDouble()
        val occupied = HashSet<Tile>(placement.slots)
        for (b in placement.barbarians) {
            // Only tiles within the distance window need checking: scan the neighbourhood.
            for (dy in -2..2) for (dx in -2..2) {
                if (dx == 0 && dy == 0) continue
                val n = Tile(b.x + dx, b.y + dy)
                if (hypot(dx.toDouble(), dy.toDouble()) < minDistance) {
                    assertThat(occupied).doesNotContain(n)
                }
            }
            occupied += b
        }
        assertThat(placement.barbarians.toSet()).doesNotContainAnyElementsOf(placement.slots)
    }
}
