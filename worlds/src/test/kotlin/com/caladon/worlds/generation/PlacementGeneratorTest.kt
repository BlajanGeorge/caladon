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
    fun `places a few thousand slots on open grass, at most one per cell, spaced apart`() {
        assertThat(placement.slots.size).isBetween(1_800, 4_000)
        assertThat(placement.slots).allSatisfy { assertOpenGrass(it) }

        val cells = placement.slots.map { (it.x / PlacementGenerator.SLOT_CELL) to (it.y / PlacementGenerator.SLOT_CELL) }
        assertThat(cells).doesNotHaveDuplicates()
        assertMinDistance(placement.slots, PlacementGenerator.SLOT_MIN_DISTANCE.toDouble())
    }

    /** Every tile within the buffer must be grass, so no mountain/forest/lake sprite overhangs the entity. */
    private fun assertOpenGrass(t: Tile) {
        val b = PlacementGenerator.TERRAIN_BUFFER
        for (dy in -b..b) for (dx in -b..b) {
            assertThat(terrain[tileIndex(t.x + dx, t.y + dy)]).`as`("tile (%d,%d) near %s", t.x + dx, t.y + dy, t).isEqualTo(Terrain.GRASS.code)
        }
    }

    private fun assertMinDistance(tiles: List<Tile>, minDistance: Double) {
        val byCell = tiles.groupBy { (it.x / 8) to (it.y / 8) }
        for (t in tiles) {
            for (cx in -1..1) for (cy in -1..1) {
                for (o in byCell[(t.x / 8 + cx) to (t.y / 8 + cy)] ?: emptyList()) {
                    if (o !== t) assertThat(hypot((o.x - t.x).toDouble(), (o.y - t.y).toDouble())).`as`("%s vs %s", t, o).isGreaterThanOrEqualTo(minDistance)
                }
            }
        }
    }

    @Test
    fun `barbarian villages sit on open grass and keep their distance from slots and each other`() {
        assertThat(placement.barbarians.size).isBetween(300, 3_000)
        assertThat(placement.barbarians).allSatisfy { assertOpenGrass(it) }
        assertMinDistance(placement.barbarians + placement.slots, PlacementGenerator.BARBARIAN_MIN_DISTANCE.toDouble())
    }
}
