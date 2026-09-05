package com.caladon.worlds.map

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** Terrain is static per world: load it once, keep it in memory, slice per viewport. */
@Component
class TerrainCache(private val store: TerrainStore) {
    private val cache = ConcurrentHashMap<Long, ByteArray>()

    fun get(worldId: Long): ByteArray? =
        cache[worldId] ?: store.read(worldId)?.also { cache[worldId] = it }

    fun put(worldId: Long, terrain: ByteArray) {
        cache[worldId] = terrain
    }
}
