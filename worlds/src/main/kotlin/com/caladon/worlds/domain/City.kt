package com.caladon.worlds.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A founded city sitting on exactly one [CitySlot]. */
@Entity
@Table(name = "city")
class City(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "world_id", nullable = false)
    var worldId: Long,

    @Column(name = "slot_id", nullable = false, unique = true)
    var slotId: Long,

    @Column(name = "owner_user_id", nullable = false)
    var ownerUserId: Long,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var points: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,

    /**
     * Who is holding the city, and until when. Both are set together or not at all: a city under
     * occupation belongs to its owner still, but does nothing and is defended by the garrison standing
     * in it. When the moment passes it changes hands.
     */
    @Column(name = "occupied_by_user_id")
    var occupiedByUserId: Long? = null,

    @Column(name = "occupation_ends_at")
    var occupationEndsAt: Instant? = null,
) {
    val occupied: Boolean get() = occupiedByUserId != null
}
