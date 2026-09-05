package com.caladon.worlds.repository

import com.caladon.worlds.domain.WorldMembership
import com.caladon.worlds.domain.WorldMembershipId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface WorldMembershipRepository : JpaRepository<WorldMembership, WorldMembershipId> {
    fun countByIdWorldId(worldId: Long): Long

    @Query("select m.id.worldId from WorldMembership m where m.id.userId = :userId")
    fun findWorldIdsByUserId(userId: Long): List<Long>

    @Query("select m.id.worldId, count(m) from WorldMembership m group by m.id.worldId")
    fun countPerWorld(): List<Array<Any>>
}
