package com.caladon.worlds.buildings

import com.caladon.worlds.rules.Building
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Embeddable
data class CityBuildingId(
    @Column(name = "city_id", nullable = false)
    var cityId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var building: Building,
) : Serializable

/** A building a city has, at its completed level. Player-built buildings have no row until level 1 completes. */
@Entity
@Table(name = "city_building")
class CityBuilding(
    @EmbeddedId
    var id: CityBuildingId,

    @Column(nullable = false)
    var level: Int,
)

/** One entry of a city's build queue: sequential, paid at placement, timed at placement. */
@Entity
@Table(name = "city_build_order")
class CityBuildOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "city_id", nullable = false)
    var cityId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var building: Building,

    @Column(name = "target_level", nullable = false)
    var targetLevel: Int,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: Instant,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant,

    @Column(name = "completes_at", nullable = false)
    var completesAt: Instant,

    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long,
)
