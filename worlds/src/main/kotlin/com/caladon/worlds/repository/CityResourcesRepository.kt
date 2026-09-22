package com.caladon.worlds.repository

import com.caladon.worlds.domain.CityResources
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface CityResourcesRepository : JpaRepository<CityResources, Long> {
    /** Row-locks the city's resources for the current transaction: every settle-and-spend goes through this. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByCityId(cityId: Long): CityResources?
}
