package com.caladon.worlds.repository

import com.caladon.worlds.domain.BarbarianVillage
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface BarbarianVillageRepository : JpaRepository<BarbarianVillage, Long> {
    fun countByWorldId(worldId: Long): Long

    /** Row-locks the village for the arrival that is resolving on it, as a city's resources are locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): BarbarianVillage?
}
