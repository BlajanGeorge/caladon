package com.caladon.worlds.buildings

import com.caladon.worlds.resources.CityAccess
import com.caladon.worlds.resources.CityState
import com.caladon.worlds.rules.Building
import com.caladon.worlds.service.WorldException
import com.caladon.users.domain.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The Cave's silver: moved in from the city's stock and never moved back out (ARCHITECTURE.md → Army →
 * Espionage and the Cave). It is one number rather than a queue, so there is nothing here but the two
 * limits: what the city has, and what the Cave can hold.
 */
@Service
class CaveService(private val cityAccess: CityAccess) {

    @Transactional
    fun store(worldId: Long, cityId: Long, userId: Long, role: Role, amount: Long): CityState {
        if (amount <= 0) throw WorldException.InvalidAmount()
        val state = cityAccess.openForOrder(worldId, cityId, userId, role)
        if (state.level(Building.CAVE) < 1) {
            throw WorldException.RequirementsNotMet(mapOf(Building.CAVE.name to "1"))
        }
        if (state.resources.silver < amount) {
            throw WorldException.NotEnoughResources(mapOf("silver" to (amount - state.resources.silver).toString()))
        }
        val room = state.caveCapacity() - state.resources.caveSilver
        if (room < amount) throw WorldException.CaveFull((amount - room).toString())

        state.resources.silver -= amount
        state.resources.caveSilver += amount
        return state
    }
}
