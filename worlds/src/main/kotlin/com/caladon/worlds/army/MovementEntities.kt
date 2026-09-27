package com.caladon.worlds.army

import com.caladon.worlds.rules.Unit
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
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.io.Serializable
import java.time.Instant

enum class MovementKind { ATTACK, SUPPORT }

/** Which way the troops are flying: out to the target, or back to the city that sent them. */
enum class MovementDirection { OUTWARD, HOMEWARD }

/**
 * One leg of a troop movement on the road. [originCityId] is always the home city and [targetCityId] the
 * other end, whichever way it is flying, so turning around keeps the row and its id.
 */
@Entity
@Table(name = "city_movement")
class CityMovement(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "world_id", nullable = false) var worldId: Long,
    @Column(name = "origin_city_id", nullable = false) var originCityId: Long,
    @Column(name = "target_city_id", nullable = false) var targetCityId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var kind: MovementKind,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var direction: MovementDirection,
    @Column(name = "departs_at", nullable = false) var departsAt: Instant,
    @Column(name = "arrives_at", nullable = false) var arrivesAt: Instant,
    @Column(name = "carried_wood", nullable = false) var carriedWood: Long = 0,
    @Column(name = "carried_stone", nullable = false) var carriedStone: Long = 0,
    @Column(name = "carried_silver", nullable = false) var carriedSilver: Long = 0,
    /** Set once the last leg has been processed; an applied movement is history and no longer shown. */
    @Column(nullable = false) var applied: Boolean = false,
)

@Embeddable
data class CityMovementUnitId(
    @Column(name = "movement_id") var movementId: Long = 0,
    @Enumerated(EnumType.STRING) @Column(length = 16) var unit: Unit = Unit.SPEARMAN,
) : Serializable

/** How many of a type a movement carries; only living units are listed. */
@Entity
@Table(name = "city_movement_unit")
class CityMovementUnit(@EmbeddedId var id: CityMovementUnitId, @Column(nullable = false) var count: Int)

interface CityMovementRepository : JpaRepository<CityMovement, Long> {
    /** In flight and starting from this city, either way. */
    fun findAllByOriginCityIdAndAppliedFalseOrderByArrivesAtAscIdAsc(originCityId: Long): List<CityMovement>

    /** In flight and heading for this city, either way. */
    fun findAllByTargetCityIdAndAppliedFalseOrderByArrivesAtAscIdAsc(targetCityId: Long): List<CityMovement>

    @Query(
        """
        SELECT m FROM CityMovement m
        WHERE m.applied = false AND m.arrivesAt <= :now AND (m.originCityId = :cityId OR m.targetCityId = :cityId)
        ORDER BY m.arrivesAt ASC, m.id ASC
        """,
    )
    fun findDueFor(@Param("cityId") cityId: Long, @Param("now") now: Instant): List<CityMovement>
}

interface CityMovementUnitRepository : JpaRepository<CityMovementUnit, CityMovementUnitId> {
    fun findAllByIdMovementId(movementId: Long): List<CityMovementUnit>

    fun findAllByIdMovementIdIn(movementIds: Collection<Long>): List<CityMovementUnit>
}
