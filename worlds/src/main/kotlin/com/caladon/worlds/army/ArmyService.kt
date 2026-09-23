package com.caladon.worlds.army

import com.caladon.users.domain.Role
import com.caladon.worlds.resources.CityAccess
import com.caladon.worlds.resources.CityState
import com.caladon.worlds.resources.ResourceConstants
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.Requirement
import com.caladon.worlds.rules.Unit
import com.caladon.worlds.rules.UnitRules
import com.caladon.worlds.service.WorldException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Recruitment (Barracks) and study (Academy): what each unit needs, ordering, cancelling. */
@Service
class ArmyService(
    private val cityAccess: CityAccess,
    private val recruitOrderRepository: CityRecruitOrderRepository,
    private val studyRepository: CityStudyRepository,
) {
    data class UnitView(
        val unit: Unit, val count: Int, val studied: Boolean, val studyCompletesAt: Instant?,
        /** Requirements a recruit order would fail on right now (Barracks level, study). */
        val blockedBy: List<Requirement>, val needsStudy: Boolean,
        val recruitSeconds: Long, val studySeconds: Long?, val studyBlockedBy: List<Requirement>,
    )

    fun views(state: CityState): List<UnitView> {
        val barracks = state.level(Building.BARRACKS)
        val academy = state.level(Building.ACADEMY)
        return Unit.entries.map { u ->
            val study = state.studies[u]
            val studied = study != null && !study.completesAt.isAfter(state.now)
            val blocked = buildList {
                if (barracks < u.barracksLevel) add(Requirement(Building.BARRACKS, u.barracksLevel))
            }
            val studyBlocked = buildList {
                if (u.academyLevel != null && academy < u.academyLevel) add(Requirement(Building.ACADEMY, u.academyLevel))
            }
            UnitView(
                unit = u, count = state.units[u]?.count ?: 0, studied = studied || !u.needsStudy,
                studyCompletesAt = study?.completesAt?.takeIf { it.isAfter(state.now) },
                blockedBy = blocked, needsStudy = u.needsStudy && !studied,
                recruitSeconds = recruitSeconds(u, barracks),
                studySeconds = u.academyLevel?.let { Math.round(UnitRules.studySeconds(u, academy) / ResourceConstants.WORLD_SPEED) },
                studyBlockedBy = studyBlocked,
            )
        }
    }

    @Transactional
    fun list(worldId: Long, cityId: Long, userId: Long, role: Role): Pair<CityState, List<UnitView>> {
        val state = cityAccess.open(worldId, cityId, userId, role)
        return state to views(state)
    }

    @Transactional
    fun recruit(worldId: Long, cityId: Long, userId: Long, role: Role, unit: Unit, count: Int): CityState {
        if (count < 1 || count > MAX_ORDER) throw WorldException.InvalidCount()
        val state = cityAccess.open(worldId, cityId, userId, role)
        val barracks = state.level(Building.BARRACKS)
        if (barracks < unit.barracksLevel) throw WorldException.RequirementsNotMet(mapOf(Building.BARRACKS.name to unit.barracksLevel.toString()))
        if (unit.needsStudy) {
            val study = state.studies[unit]
            if (study == null || study.completesAt.isAfter(state.now)) throw WorldException.NotStudied()
        }
        val cost = unit.cost * count.toLong()
        val pop = unit.population.toLong() * count
        val short = state.shortfall(cost)
        if (short.isNotEmpty()) throw WorldException.NotEnoughResources(short.entries.associate { it.key.name.lowercase() to it.value.toString() })
        if (state.resources.population < pop) throw WorldException.NotEnoughPopulation((pop - state.resources.population).toString())
        state.pay(cost, pop)

        val head = state.recruitOrders.isEmpty()
        val order = recruitOrderRepository.save(
            CityRecruitOrder(
                cityId = cityId, unit = unit, count = count, remaining = count, orderedAt = state.now,
                nextCompletesAt = if (head) state.now.plusSeconds(recruitSeconds(unit, barracks)) else null,
            ),
        )
        state.recruitOrders += order
        return state
    }

    @Transactional
    fun study(worldId: Long, cityId: Long, userId: Long, role: Role, unit: Unit): CityState {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val needed = unit.academyLevel ?: throw WorldException.AlreadyStudied()
        if (state.studies.containsKey(unit)) throw WorldException.AlreadyStudied()
        val academy = state.level(Building.ACADEMY)
        if (academy < needed) throw WorldException.RequirementsNotMet(mapOf(Building.ACADEMY.name to needed.toString()))
        val cost = requireNotNull(unit.studyCost)
        val short = state.shortfall(cost)
        if (short.isNotEmpty()) throw WorldException.NotEnoughResources(short.entries.associate { it.key.name.lowercase() to it.value.toString() })
        state.pay(cost, 0)
        val seconds = Math.round(UnitRules.studySeconds(unit, academy) / ResourceConstants.WORLD_SPEED)
        val study = studyRepository.save(CityStudy(CityUnitId(cityId, unit), state.now.plusSeconds(seconds)))
        state.studies[unit] = study
        return state
    }

    /** Cancels a recruit order and refunds its unproduced remainder; the next order starts now if this was the head. */
    @Transactional
    fun cancel(worldId: Long, cityId: Long, userId: Long, role: Role, orderId: Long): CityState {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val order = state.recruitOrders.firstOrNull { it.id == orderId } ?: throw WorldException.OrderNotFound()
        val wasHead = state.recruitOrders.first() == order
        state.refund(order.unit.cost * order.remaining.toLong(), order.unit.population.toLong() * order.remaining)
        state.recruitOrders.remove(order)
        recruitOrderRepository.delete(order)
        if (wasHead) state.recruitOrders.firstOrNull()?.let {
            it.nextCompletesAt = state.now.plusSeconds(recruitSeconds(it.unit, state.level(Building.BARRACKS)))
        }
        return state
    }

    private fun recruitSeconds(u: Unit, barracks: Int): Long = Math.round(UnitRules.recruitSeconds(u, barracks) / ResourceConstants.WORLD_SPEED)

    companion object { const val MAX_ORDER = 10_000 }
}
