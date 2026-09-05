package com.caladon.worlds.repository

import com.caladon.worlds.domain.BarbarianVillage
import org.springframework.data.jpa.repository.JpaRepository

interface BarbarianVillageRepository : JpaRepository<BarbarianVillage, Long> {
    fun countByWorldId(worldId: Long): Long
}
