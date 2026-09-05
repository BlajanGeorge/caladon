package com.caladon.worlds.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Embeddable
data class WorldMembershipId(
    @Column(name = "world_id", nullable = false)
    var worldId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,
) : Serializable

/** A player's enrolment in a world, independent of whether they currently own cities there. */
@Entity
@Table(name = "world_membership")
class WorldMembership(
    @EmbeddedId
    var id: WorldMembershipId,

    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant,
)
