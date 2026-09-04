package com.caladon.users.repository

import com.caladon.users.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun deleteByIdAndUserId(id: UUID, userId: Long): Long

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    fun deleteAllExpiredBefore(now: Instant): Int
}
