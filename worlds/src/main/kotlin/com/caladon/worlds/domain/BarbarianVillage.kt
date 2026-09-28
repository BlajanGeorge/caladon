package com.caladon.worlds.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Raidable, never owned or conquered (ARCHITECTURE.md → Raiding barbarians): a level that says how many
 * militia stand there, a store that refills to the level's ceiling, and the two clocks the store and the
 * hardening are measured from.
 */
@Entity
@Table(name = "barbarian_village")
class BarbarianVillage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "world_id", nullable = false)
    var worldId: Long,

    @Column(nullable = false)
    var x: Short,

    @Column(nullable = false)
    var y: Short,

    @Column(nullable = false)
    var level: Int = 1,

    @Column(nullable = false)
    var wood: Long = 1000,

    @Column(nullable = false)
    var stone: Long = 1000,

    @Column(nullable = false)
    var silver: Long = 1000,

    /** When the store was last brought up to date. */
    @Column(name = "settled_at", nullable = false)
    var settledAt: Instant = Instant.EPOCH,

    /** When the village was last taken; null while nobody has taken it. */
    @Column(name = "raided_at")
    var raidedAt: Instant? = null,
)
