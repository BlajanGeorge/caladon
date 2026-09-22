package com.caladon.worlds.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A city's resource stocks, settled lazily to [settledAt], plus its remaining free population.
 * Rates and capacity are not stored; they are derived (today: the level-1 constants).
 */
@Entity
@Table(name = "city_resources")
class CityResources(
    @Id
    @Column(name = "city_id", nullable = false)
    var cityId: Long,

    @Column(nullable = false, precision = 14, scale = 3)
    var wood: BigDecimal,

    @Column(nullable = false, precision = 14, scale = 3)
    var stone: BigDecimal,

    @Column(nullable = false, precision = 14, scale = 3)
    var iron: BigDecimal,

    @Column(name = "settled_at", nullable = false)
    var settledAt: Instant,

    @Column(nullable = false)
    var population: Int,
) {
    fun stock(resource: Resource): BigDecimal = when (resource) {
        Resource.WOOD -> wood
        Resource.STONE -> stone
        Resource.IRON -> iron
    }

    fun setStock(resource: Resource, value: BigDecimal) = when (resource) {
        Resource.WOOD -> wood = value
        Resource.STONE -> stone = value
        Resource.IRON -> iron = value
    }
}
