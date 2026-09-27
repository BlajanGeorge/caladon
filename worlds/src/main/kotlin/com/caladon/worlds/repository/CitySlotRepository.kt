package com.caladon.worlds.repository

import com.caladon.worlds.domain.CitySlot
import org.springframework.data.jpa.repository.JpaRepository

interface CitySlotRepository : JpaRepository<CitySlot, Long> {
    fun countByWorldId(worldId: Long): Long

    /** The slot on a field, free or built on; one at most, the position being unique per world. */
    fun findByWorldIdAndXAndY(worldId: Long, x: Short, y: Short): CitySlot?
}
