package com.caladon.worlds.repository

import com.caladon.worlds.domain.World
import com.caladon.worlds.domain.WorldState
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface WorldRepository : JpaRepository<World, Long> {
    fun existsByNameIgnoreCase(name: String): Boolean
    fun findAllByStateOrderByIdAsc(state: WorldState): List<World>
    fun findAllByOrderByIdAsc(): List<World>

    /** Row-locks the world for the current transaction; used to serialize joins per world. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): World?
}
