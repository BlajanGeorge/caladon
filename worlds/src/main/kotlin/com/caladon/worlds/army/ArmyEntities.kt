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
import java.io.Serializable
import java.time.Instant

@Embeddable
data class CityUnitId(
    @Column(name = "city_id", nullable = false) var cityId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var unit: Unit,
) : Serializable

/** How many of a unit type a city holds (at home). */
@Entity
@Table(name = "city_unit")
class CityUnit(@EmbeddedId var id: CityUnitId, @Column(nullable = false) var count: Int)

/** An entry of the Academy's study queue; studied once `completesAt` has passed. */
@Entity
@Table(name = "city_study")
class CityStudy(
    @EmbeddedId var id: CityUnitId,
    @Column(name = "ordered_at", nullable = false) var orderedAt: Instant,
    @Column(name = "completes_at", nullable = false) var completesAt: Instant,
    /** Set once advance() has processed the completion (lets the sweeper skip it). */
    @Column(nullable = false) var applied: Boolean = false,
)

/** One entry of a city's recruitment queue; units complete one at a time from the head order. */
@Entity
@Table(name = "city_recruit_order")
class CityRecruitOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "city_id", nullable = false) var cityId: Long,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var unit: Unit,
    @Column(nullable = false) var count: Int,
    @Column(nullable = false) var remaining: Int,
    @Column(name = "ordered_at", nullable = false) var orderedAt: Instant,
    /** When the next unit of this order completes; null while another order is ahead. */
    @Column(name = "next_completes_at") var nextCompletesAt: Instant?,
)

interface CityUnitRepository : JpaRepository<CityUnit, CityUnitId> {
    fun findAllByIdCityId(cityId: Long): List<CityUnit>
}

interface CityStudyRepository : JpaRepository<CityStudy, CityUnitId> {
    fun findAllByIdCityIdOrderByCompletesAtAsc(cityId: Long): List<CityStudy>
}

interface CityRecruitOrderRepository : JpaRepository<CityRecruitOrder, Long> {
    fun findAllByCityIdOrderByIdAsc(cityId: Long): List<CityRecruitOrder>
}
