package com.caladon.worlds.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Farmable, never owned or conquered. Loot/level fields come with the farming feature. */
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
)
