package com.caladon.worlds.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A city's whole-unit resource stocks, each with its own production clock (the instant it was last
 * settled to), plus its remaining free population. Rates and capacity are derived from building levels.
 */
@Entity
@Table(name = "city_resources")
class CityResources(
    @Id
    @Column(name = "city_id", nullable = false)
    var cityId: Long,

    @Column(nullable = false) var wood: Long,
    @Column(nullable = false) var stone: Long,
    @Column(nullable = false) var iron: Long,

    @Column(name = "wood_settled_at", nullable = false) var woodSettledAt: Instant,
    @Column(name = "stone_settled_at", nullable = false) var stoneSettledAt: Instant,
    @Column(name = "iron_settled_at", nullable = false) var ironSettledAt: Instant,

    @Column(nullable = false)
    var population: Int,
) {
    fun stock(resource: Resource): Long = when (resource) {
        Resource.WOOD -> wood
        Resource.STONE -> stone
        Resource.IRON -> iron
    }

    fun setStock(resource: Resource, value: Long) = when (resource) {
        Resource.WOOD -> wood = value
        Resource.STONE -> stone = value
        Resource.IRON -> iron = value
    }

    fun settledAt(resource: Resource): Instant = when (resource) {
        Resource.WOOD -> woodSettledAt
        Resource.STONE -> stoneSettledAt
        Resource.IRON -> ironSettledAt
    }

    fun setSettledAt(resource: Resource, value: Instant) = when (resource) {
        Resource.WOOD -> woodSettledAt = value
        Resource.STONE -> stoneSettledAt = value
        Resource.IRON -> ironSettledAt = value
    }
}
