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
    @Column(nullable = false) var silver: Long,

    @Column(name = "wood_settled_at", nullable = false) var woodSettledAt: Instant,
    @Column(name = "stone_settled_at", nullable = false) var stoneSettledAt: Instant,
    @Column(name = "silver_settled_at", nullable = false) var silverSettledAt: Instant,

    @Column(nullable = false)
    var population: Int,

    /** The Cave's own silver: moved in from the stock, and never out except by being spent on spying. */
    @Column(name = "cave_silver", nullable = false) var caveSilver: Long = 0,
) {
    fun stock(resource: Resource): Long = when (resource) {
        Resource.WOOD -> wood
        Resource.STONE -> stone
        Resource.SILVER -> silver
    }

    fun setStock(resource: Resource, value: Long) = when (resource) {
        Resource.WOOD -> wood = value
        Resource.STONE -> stone = value
        Resource.SILVER -> silver = value
    }

    fun settledAt(resource: Resource): Instant = when (resource) {
        Resource.WOOD -> woodSettledAt
        Resource.STONE -> stoneSettledAt
        Resource.SILVER -> silverSettledAt
    }

    fun setSettledAt(resource: Resource, value: Instant) = when (resource) {
        Resource.WOOD -> woodSettledAt = value
        Resource.STONE -> stoneSettledAt = value
        Resource.SILVER -> silverSettledAt = value
    }
}
