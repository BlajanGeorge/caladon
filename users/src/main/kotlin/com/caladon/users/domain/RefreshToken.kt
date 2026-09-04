package com.caladon.users.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A refresh token that is currently valid. Deleting the row revokes the token. */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    /** Equals the `jti` claim of the refresh JWT. */
    @Id
    var id: UUID,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
)
