package com.caladon.worlds.rules

import com.caladon.worlds.resources.ResourceConstants

/**
 * Taking a city (ARCHITECTURE.md → Conquest). Pure arithmetic: how long a city must be held, and what
 * counts as holding it. Nothing here touches a row.
 *
 * There is no loyalty and no dice. An attack that wins with a Nobleman still standing stays where it is
 * and holds the city for [OCCUPATION_HOURS]. Anyone may come and break the hold in that time — the owner
 * from another of his cities, an ally, or a third party who wants the city for himself. Lose the
 * Nobleman, or every man standing with him, and the whole attempt is over.
 */
object ConquestRules {
    /** Hours a city is held before it changes hands, at world speed 1. */
    const val OCCUPATION_HOURS = 12.0

    fun occupationSeconds(): Long = Math.round(OCCUPATION_HOURS * 3600 / ResourceConstants.WORLD_SPEED)

    /**
     * Whether a force can hold a city: a Nobleman, and someone to stand with him. A Nobleman alone is a
     * man in a hostile city, not a garrison, and the hold breaks the moment he is the last one left.
     */
    fun canHold(garrison: Map<Unit, Int>): Boolean =
        (garrison[Unit.NOBLEMAN] ?: 0) > 0 && garrison.any { (u, n) -> u != Unit.NOBLEMAN && n > 0 }
}
