package com.caladon.worlds.repository

import com.caladon.worlds.domain.CitySlot
import org.springframework.data.jpa.repository.JpaRepository

interface CitySlotRepository : JpaRepository<CitySlot, Long> {
    fun countByWorldId(worldId: Long): Long
}
