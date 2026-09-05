package com.caladon.worlds.map

import com.caladon.worlds.generation.MapConstants
import com.caladon.worlds.generation.tileIndex

/** An inclusive rectangle of tiles. */
data class Viewport(val startX: Int, val startY: Int, val endX: Int, val endY: Int) {
    val width: Int get() = endX - startX + 1
    val height: Int get() = endY - startY + 1

    /** Field -> message for every violated constraint; empty when valid. */
    fun violations(): Map<String, String> {
        val v = LinkedHashMap<String, String>()
        val max = MapConstants.SIZE - 1
        fun range(name: String, value: Int) { if (value !in 0..max) v[name] = "must be between 0 and $max" }
        range("startX", startX); range("startY", startY); range("endX", endX); range("endY", endY)
        if (endX < startX) v["endX"] = "must be >= startX"
        if (endY < startY) v["endY"] = "must be >= startY"
        if (v.isEmpty()) {
            if (width > MapConstants.MAX_VIEWPORT_SIDE) v["endX"] = "rectangle wider than ${MapConstants.MAX_VIEWPORT_SIDE} tiles"
            if (height > MapConstants.MAX_VIEWPORT_SIDE) v["endY"] = "rectangle taller than ${MapConstants.MAX_VIEWPORT_SIDE} tiles"
        }
        return v
    }

    /** Terrain codes of this rectangle, flat row-major from (startX, startY). */
    fun slice(terrain: ByteArray): IntArray {
        val out = IntArray(width * height)
        var i = 0
        for (y in startY..endY) {
            val row = tileIndex(startX, y)
            for (dx in 0 until width) out[i++] = terrain[row + dx].toInt()
        }
        return out
    }
}
