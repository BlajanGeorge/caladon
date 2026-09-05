package com.caladon.worlds.domain

enum class WorldState {
    /** Seeded, admin-only, invisible to players. */
    DRAFT,
    /** Live and visible; players may join. */
    PLAYABLE,
    /** Reserved for a later "ended/archived" feature; no transition into it yet. */
    ENDED,
}
