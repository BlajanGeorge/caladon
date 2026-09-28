package com.caladon.worlds.army

import com.caladon.users.domain.Role
import com.caladon.worlds.map.MapQueryDao
import com.caladon.worlds.ranking.RankingService
import com.caladon.worlds.report.BattlePayload
import com.caladon.worlds.report.BattleRole
import com.caladon.worlds.report.BuildingLevelPayload
import com.caladon.worlds.report.CaughtPayload
import com.caladon.worlds.report.EspionagePayload
import com.caladon.worlds.report.ReportService
import com.caladon.worlds.report.ResourcesPayload
import com.caladon.worlds.report.SeenPayload
import com.caladon.worlds.report.SidePayload
import com.caladon.worlds.report.UnitCountPayload
import com.caladon.worlds.report.UnitTallyPayload
import com.caladon.worlds.report.WallPayload
import com.caladon.worlds.repository.CityRepository
import com.caladon.worlds.repository.CitySlotRepository
import com.caladon.worlds.resources.CityAccess
import com.caladon.worlds.resources.CityState
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.BuildingRules
import com.caladon.worlds.rules.Cost
import com.caladon.worlds.rules.MovementRules
import com.caladon.worlds.rules.Unit
import com.caladon.worlds.service.WorldException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Troop movements (ARCHITECTURE.md → Army → Movements): sending troops out, turning them around, and
 * everything that happens when a leg arrives. Arrivals are not scheduled: any touch of either city runs
 * the due ones (through [CityAccess.advance]) and the sweeper catches what nobody touches.
 */
@Service
class MovementService(
    private val cityAccess: CityAccess,
    private val cityRepository: CityRepository,
    private val citySlotRepository: CitySlotRepository,
    private val cityUnitRepository: CityUnitRepository,
    private val citySupportRepository: CitySupportRepository,
    private val movementRepository: CityMovementRepository,
    private val movementUnitRepository: CityMovementUnitRepository,
    private val mapQueryDao: MapQueryDao,
    private val rankingService: RankingService,
    private val reportService: ReportService,
    private val jdbc: NamedParameterJdbcTemplate,
) {
    data class MovementUnitView(val unit: Unit, val count: Int)

    /** One in-flight movement as the city sees it; [carrying] is null when the city may not know. */
    data class MovementView(
        val id: Long,
        val kind: MovementKind,
        val direction: MovementDirection,
        val otherCityName: String,
        /** Whose city it is: a player recognises a name, not a field number. */
        val otherPlayerName: String,
        val x: Int,
        val y: Int,
        val departsAt: Instant,
        val arrivesAt: Instant,
        val units: List<MovementUnitView>,
        val carrying: Triple<Long, Long, Long>?,
        val canRecall: Boolean,
    )

    data class Movements(val outgoing: List<MovementView>, val incoming: List<MovementView>)

    @Transactional
    fun list(worldId: Long, cityId: Long, userId: Long, role: Role): Pair<CityState, Movements> {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val outgoing = movementRepository.findAllByOriginCityIdAndAppliedFalseOrderByArrivesAtAscIdAsc(cityId)
        // Only what is actually heading here: a movement on its homeward leg is flying away from this city,
        // back to whoever sent it, and has no business in the panel that warns about arrivals. A spy
        // mission is never listed at all — a spy you can see coming is not a spy. The target learns of one
        // only from the report it gets when the attempt fails.
        val incoming = movementRepository.findAllByTargetCityIdAndAppliedFalseOrderByArrivesAtAscIdAsc(cityId)
            .filter {
                it.originCityId != cityId &&
                    it.direction == MovementDirection.OUTWARD &&
                    it.kind != MovementKind.ESPIONAGE
            }
        val units = unitsOf(outgoing + incoming)
        val refs = refs((outgoing.map { it.targetCityId } + incoming.map { it.originCityId }).toSet())
        return state to Movements(
            outgoing = outgoing.map { view(it, refs[it.targetCityId], units[it.id], own = true, now = state.now) },
            incoming = incoming.map { view(it, refs[it.originCityId], units[it.id], own = false, now = state.now) },
        )
    }

    /**
     * An incoming attack shows only its arrival until it is nearly here: close enough to see, the city
     * makes out what is coming. Support is a friend's, so it is shown the whole way.
     */
    private fun view(m: CityMovement, other: CityRef?, units: Map<Unit, Int>?, own: Boolean, now: Instant): MovementView {
        val hidden = !own && m.kind != MovementKind.SUPPORT && !nearlyHere(m, now)
        return MovementView(
            id = requireNotNull(m.id), kind = m.kind, direction = m.direction,
            otherCityName = other?.name ?: "", otherPlayerName = other?.player ?: "", x = other?.x ?: 0, y = other?.y ?: 0,
            departsAt = m.departsAt, arrivesAt = m.arrivesAt,
            units = if (hidden) emptyList() else Unit.entries.mapNotNull { u ->
                units?.get(u)?.takeIf { it > 0 }?.let { MovementUnitView(u, it) }
            },
            carrying = if (hidden) null else Triple(m.carriedWood, m.carriedStone, m.carriedSilver),
            canRecall = own && m.direction == MovementDirection.OUTWARD,
        )
    }

    // ---- sending ----

    /**
     * Sends [units] of the city to the city on `(targetX, targetY)`. The units leave `city_unit` at once;
     * their population stays spent by the origin, on the road as at home.
     */
    @Transactional
    fun send(
        worldId: Long, cityId: Long, userId: Long, role: Role,
        kind: MovementKind, targetX: Int, targetY: Int, units: Map<Unit, Int>,
    ): CityState {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val wanted = units.filterValues { it > 0 }
        if (wanted.isEmpty()) throw WorldException.NoUnits()
        val targetId = mapQueryDao.cityIdAt(worldId, targetX, targetY) ?: throw WorldException.CityNotFound()
        if (targetId == state.cityId) throw WorldException.SameCity()
        val short = wanted.mapNotNull { (u, n) ->
            val home = state.units[u]?.count ?: 0
            if (home < n) u.name to (n - home).toString() else null
        }
        if (short.isNotEmpty()) throw WorldException.NotEnoughUnits(short.toMap())

        for ((u, n) in wanted) {
            val row = requireNotNull(state.units[u])
            row.count -= n
            cityUnitRepository.save(row)
        }
        val (x, y) = cityAccess.coordinates(state)
        val target = requireNotNull(refs(setOf(targetId))[targetId])
        val seconds = MovementRules.travelSeconds(MovementRules.distance(x, y, target.x, target.y), MovementRules.slowestSpeed(wanted))
        val movement = movementRepository.save(
            CityMovement(
                worldId = worldId, originCityId = cityId, targetCityId = targetId, kind = kind,
                direction = MovementDirection.OUTWARD, departsAt = state.now, arrivesAt = state.now.plusSeconds(seconds),
            ),
        )
        writeUnits(requireNotNull(movement.id), wanted)
        return state
    }

    /**
     * Sends a spy mission at the city on `(targetX, targetY)`, paid for out of this city's Cave. The
     * silver goes at once and never comes back: it bought the attempt, not the outcome. While the
     * mission is anywhere on the road, out or back, the city may not send another at the same target.
     */
    @Transactional
    fun spy(worldId: Long, cityId: Long, userId: Long, role: Role, targetX: Int, targetY: Int, silver: Long): CityState {
        if (silver <= 0) throw WorldException.InvalidAmount()
        val state = cityAccess.open(worldId, cityId, userId, role)
        val targetId = mapQueryDao.cityIdAt(worldId, targetX, targetY) ?: throw WorldException.CityNotFound()
        if (targetId == state.cityId) throw WorldException.SameCity()
        if (state.level(Building.CAVE) < 1) throw WorldException.RequirementsNotMet(mapOf(Building.CAVE.name to "1"))
        if (state.resources.caveSilver < silver) {
            throw WorldException.NotEnoughSilver((silver - state.resources.caveSilver).toString())
        }
        if (movementRepository.existsByOriginCityIdAndTargetCityIdAndKindAndAppliedFalse(cityId, targetId, MovementKind.ESPIONAGE)) {
            throw WorldException.AlreadySpying()
        }

        state.resources.caveSilver -= silver
        val (x, y) = cityAccess.coordinates(state)
        val target = requireNotNull(refs(setOf(targetId))[targetId])
        val seconds = MovementRules.travelSeconds(MovementRules.distance(x, y, target.x, target.y), MovementRules.SPY_SPEED)
        movementRepository.save(
            CityMovement(
                worldId = worldId, originCityId = cityId, targetCityId = targetId, kind = MovementKind.ESPIONAGE,
                direction = MovementDirection.OUTWARD, departsAt = state.now, arrivesAt = state.now.plusSeconds(seconds),
                carriedSilver = silver,
            ),
        )
        return state
    }

    // ---- recall ----

    /**
     * Turns one of the city's outward movements around: the way back takes as long as it has already
     * flown. [id] may instead name the city a support stands in (or whose support stands here): the owner
     * may call its troops home and the host may send them away, and both send them home from the host.
     */
    @Transactional
    fun recall(worldId: Long, cityId: Long, userId: Long, role: Role, id: Long): CityState {
        val state = cityAccess.open(worldId, cityId, userId, role)
        val movement = movementRepository.findById(id).orElse(null)?.takeIf { it.originCityId == state.cityId }
        if (movement != null) {
            if (movement.applied || movement.direction == MovementDirection.HOMEWARD ||
                !movement.arrivesAt.isAfter(state.now)
            ) {
                throw WorldException.AlreadyArrived()
            }
            val flown = Duration.between(movement.departsAt, state.now).seconds.coerceAtLeast(1)
            movement.direction = MovementDirection.HOMEWARD
            movement.departsAt = state.now
            movement.arrivesAt = state.now.plusSeconds(flown)
            return state
        }
        return recallSupport(state, id)
    }

    private fun recallSupport(state: CityState, otherCityId: Long): CityState {
        val cityId = state.cityId
        val hosted = citySupportRepository.findAllByIdHostCityId(otherCityId).filter { it.id.ownerCityId == cityId }
        val sheltering = citySupportRepository.findAllByIdHostCityId(cityId).filter { it.id.ownerCityId == otherCityId }
        val rows = hosted.ifEmpty { sheltering }
        if (rows.isEmpty()) throw WorldException.MovementNotFound()
        val hostId = rows.first().id.hostCityId
        val ownerId = rows.first().id.ownerCityId
        val refs = refs(setOf(hostId, ownerId))
        val host = requireNotNull(refs[hostId])
        val owner = requireNotNull(refs[ownerId])
        val units = rows.associate { it.id.unit to it.count }
        val seconds = MovementRules.travelSeconds(
            MovementRules.distance(host.x, host.y, owner.x, owner.y), MovementRules.slowestSpeed(units),
        )
        val movement = movementRepository.save(
            CityMovement(
                worldId = state.city.worldId, originCityId = ownerId, targetCityId = hostId, kind = MovementKind.SUPPORT,
                direction = MovementDirection.HOMEWARD, departsAt = state.now, arrivesAt = state.now.plusSeconds(seconds),
            ),
        )
        writeUnits(requireNotNull(movement.id), units)
        citySupportRepository.deleteAll(rows)
        return state
    }

    // ---- arrival ----

    /**
     * Runs every movement of this city whose arrival has passed, newest state first. Loading the other end
     * advances it too, which would come straight back here: the flag keeps one pass at a time, and the
     * other city's own movements wait for its next touch or for the sweeper.
     */
    fun processArrivals(state: CityState) {
        if (processing.get()) return
        processing.set(true)
        try {
            var guard = 0
            while (guard++ < MAX_ARRIVALS) {
                val due = movementRepository.findDueFor(state.cityId, state.now).firstOrNull() ?: break
                apply(due, state)
            }
        } finally {
            processing.set(false)
        }
    }

    private fun apply(m: CityMovement, state: CityState) {
        val otherId = if (m.originCityId == state.cityId) m.targetCityId else m.originCityId
        lockInIdOrder(state.cityId, otherId)
        val other = cityAccess.advanceForMovement(otherId)
        val origin = if (m.originCityId == state.cityId) state else other
        val target = if (m.targetCityId == state.cityId) state else other
        if (origin == null || target == null) {
            m.applied = true
            return
        }
        when {
            m.direction == MovementDirection.HOMEWARD -> arriveHome(m, origin)
            m.kind == MovementKind.SUPPORT -> arriveSupport(m, target)
            m.kind == MovementKind.ESPIONAGE -> arriveEspionage(m, origin, target)
            else -> arriveAttack(m, origin, target)
        }
    }

    /** The troops are back in `city_unit` and the plunder in the stocks, capped by the Deposit. */
    private fun arriveHome(m: CityMovement, origin: CityState) {
        for ((u, n) in unitsOf(m)) addUnits(origin, u, n)
        origin.refund(Cost(m.carriedWood, m.carriedStone, m.carriedSilver), 0)
        m.applied = true
    }

    /** The troops stay: one `city_support` row per type, owned by the origin, hosted by the target. */
    private fun arriveSupport(m: CityMovement, target: CityState) {
        for ((u, n) in unitsOf(m)) {
            val id = CitySupportId(target.cityId, m.originCityId, u)
            val row = citySupportRepository.findById(id).orElse(null)
            citySupportRepository.save(if (row == null) CitySupport(id, n) else row.also { it.count += n })
        }
        m.applied = true
    }

    /**
     * Combat, then home with what could be carried. The dead free no population: troops on the road stay
     * paid for by their city, and support dying in a foreign city is not the host's to give back.
     */
    private fun arriveAttack(m: CityMovement, origin: CityState, target: CityState) {
        val attackers = unitsOf(m)
        val support = citySupportRepository.findAllByIdHostCityId(target.cityId).filter { it.id.ownerCityId != target.cityId }
        // Keyed by who holds the troops, the target itself included, so each holder loses its own share.
        val defenders = buildMap<Long, Map<Unit, Int>> {
            put(target.cityId, target.units.mapValues { it.value.count }.filterValues { it > 0 })
            for ((ownerId, rows) in support.groupBy { it.id.ownerCityId }) put(ownerId, rows.associate { it.id.unit to it.count })
        }
        val wallBefore = target.level(Building.WALL)
        val combat = MovementRules.resolve(attackers, defenders, wallBefore)

        // The dead free the population they were paid for, each to the city that raised them: the target
        // for its own, every supporter for the troops it lent, and the attacker for what it lost.
        var targetFreed = 0L
        for ((u, left) in combat.defenderLeft.getValue(target.cityId)) {
            val row = target.units[u] ?: continue
            targetFreed += u.population.toLong() * (row.count - left)
            row.count = left
            cityUnitRepository.save(row)
        }
        target.resources.population += targetFreed.toInt()

        val freedByOwner = mutableMapOf<Long, Long>()
        for (row in support) {
            val left = combat.defenderLeft[row.id.ownerCityId]?.get(row.id.unit) ?: 0
            freedByOwner.merge(row.id.ownerCityId, row.id.unit.population.toLong() * (row.count - left), Long::plus)
            if (left <= 0) citySupportRepository.delete(row) else citySupportRepository.save(row.also { it.count = left })
        }
        freePopulation(freedByOwner)

        val survivors = combat.attackerLeft.filterValues { it > 0 }
        val attackerFreed = attackers.entries.sumOf { (u, n) -> u.population.toLong() * (n - (survivors[u] ?: 0)) }
        origin.resources.population += attackerFreed.toInt()
        writeUnits(requireNotNull(m.id), survivors)

        // Whose troops each supporting city's were, so what the defence earned can be shared out.
        val supporters = cityRepository.findAllById(support.map { it.id.ownerCityId }.toSet())
            .associate { requireNotNull(it.id) to it.ownerUserId }
        awardBattlePoints(
            origin, target, supporters,
            killedDefending = targetFreed + freedByOwner.values.sum(),
            killedAttacking = attackerFreed,
            defenceByHolder = combat.defenceByHolder,
        )
        // Only a winner has survivors, so only a winner batters the Wall and only a winner plunders.
        var wallAfter = wallBefore
        var taken: Triple<Long, Long, Long>? = null
        if (combat.attackerWon && survivors.isNotEmpty()) {
            wallAfter = MovementRules.wallAfter(wallBefore, MovementRules.siegeStrength(survivors))
            damageWall(target, wallBefore, wallAfter)

            val hidden = BuildingRules.vault(target.level(Building.VAULT))
            val plundered = MovementRules.plunder(
                Triple(target.resources.wood - hidden, target.resources.stone - hidden, target.resources.silver - hidden),
                MovementRules.carry(survivors),
            )
            target.resources.wood -= plundered.first
            target.resources.stone -= plundered.second
            target.resources.silver -= plundered.third
            m.carriedWood = plundered.first
            m.carriedStone = plundered.second
            m.carriedSilver = plundered.third
            taken = plundered.takeIf { it.first + it.second + it.third > 0 }
        }

        writeBattleReports(
            origin = origin, target = target, sent = attackers, survivors = survivors,
            defenders = defenders, defenderLeft = combat.defenderLeft, attackerWon = combat.attackerWon,
            plunder = taken, wall = WallPayload(wallBefore, wallAfter), supporters = supporters,
        )
        if (survivors.isEmpty()) {
            m.applied = true
            return
        }
        turnAround(m, survivors, origin, target)
    }

    /**
     * Takes the levels the siege engines knocked off the Wall, and the points with them: a level that is
     * gone is a level the city is no longer worth.
     */
    private fun damageWall(target: CityState, before: Int, after: Int) {
        if (after >= before) return
        val row = target.buildings[Building.WALL] ?: return
        row.level = after
        target.city.points -= (BuildingRules.points(Building.WALL, before) - BuildingRules.points(Building.WALL, after)).toInt()
    }

    /**
     * One report per player who was in the fight. The defender and every supporter see it whole; a
     * beaten attacker sees only its own dead, since nobody survived to carry the news back.
     *
     * A player who supported from two cities gets **one** report, not two: a report is what a player is
     * left with, and the defending side is reported as one army in any case. A player who is already
     * being written to as the defender — or, oddly, as the attacker — is not written to twice.
     */
    /** The population a side killed: what it is paid in battle points. */
    private fun killed(before: Map<Unit, Int>, after: Map<Unit, Int>): Long =
        before.entries.sumOf { (u, n) -> u.population.toLong() * (n - (after[u] ?: 0)) }

    private fun writeBattleReports(
        origin: CityState,
        target: CityState,
        sent: Map<Unit, Int>,
        survivors: Map<Unit, Int>,
        defenders: Map<Long, Map<Unit, Int>>,
        defenderLeft: Map<Long, Map<Unit, Int>>,
        attackerWon: Boolean,
        plunder: Triple<Long, Long, Long>?,
        wall: WallPayload,
        supporters: Map<Long, Long>,
    ) {
        val worldId = target.city.worldId
        val names = refs(setOf(origin.cityId, target.cityId) + supporters.keys)
        // What each side earned is what it killed, the same figures the standings are given.
        val attackerSide = SidePayload(
            player = names[origin.cityId]?.player ?: "", city = origin.city.name,
            units = tallies(sent) { survivors[it] ?: 0 },
            points = killed(merge(defenders.values), merge(defenderLeft.values)),
        )
        // The whole defence as one army: the city's own troops and every supporter's, which is the army
        // the attack actually met.
        val defenderSent = merge(defenders.values)
        val defenderSide = SidePayload(
            player = names[target.cityId]?.player ?: "", city = target.city.name,
            units = tallies(defenderSent, merge(defenderLeft.values)::getValue),
            points = killed(sent, survivors),
        )
        val loot = plunder?.let { ResourcesPayload(it.first, it.second, it.third) }

        reportService.write(
            worldId = worldId, ownerUserId = origin.city.ownerUserId, kind = ReportService.Kind.BATTLE,
            createdAt = origin.now, subjectCity = origin.city.name, otherCity = target.city.name,
            otherPlayer = defenderSide.player, won = attackerWon, summary = "Attack on ${target.city.name}",
            payload = BattlePayload(
                role = BattleRole.ATTACKER, attacker = attackerSide,
                defender = defenderSide.takeIf { attackerWon }, plunder = loot.takeIf { attackerWon },
                wall = wall.takeIf { attackerWon },
            ),
        )
        reportService.write(
            worldId = worldId, ownerUserId = target.city.ownerUserId, kind = ReportService.Kind.BATTLE,
            createdAt = target.now, subjectCity = target.city.name, otherCity = origin.city.name,
            otherPlayer = attackerSide.player, won = !attackerWon, summary = "Attack from ${origin.city.name}",
            payload = BattlePayload(BattleRole.DEFENDER, attackerSide, defenderSide, loot, wall),
        )

        val written = setOf(target.city.ownerUserId, origin.city.ownerUserId)
        for ((userId, cityIds) in supporters.entries.groupBy({ it.value }, { it.key })) {
            if (userId in written) continue
            val own = cityIds.min()
            reportService.write(
                worldId = worldId, ownerUserId = userId, kind = ReportService.Kind.BATTLE,
                createdAt = target.now, subjectCity = names[own]?.name ?: "", otherCity = target.city.name,
                otherPlayer = defenderSide.player, won = !attackerWon, summary = "Battle at ${target.city.name}",
                payload = BattlePayload(BattleRole.SUPPORTER, attackerSide, defenderSide, loot, wall),
            )
        }
    }

    /** The three numbers a report gives per unit type: what set out, what died, and what is left. */
    private fun tallies(sent: Map<Unit, Int>, left: (Unit) -> Int): List<UnitTallyPayload> =
        Unit.entries.mapNotNull { u ->
            val out = sent[u] ?: 0
            if (out <= 0) null else UnitTallyPayload(u, u.displayName, out, out - left(u), left(u))
        }

    private fun merge(held: Collection<Map<Unit, Int>>): Map<Unit, Int> =
        Unit.entries.associateWith { u -> held.sumOf { it[u] ?: 0 } }

    /**
     * The silver committed meets the target's Cave: more than it holds and the spy sees everything and is
     * never noticed; not more and it learns only that it failed, while the target learns by whom. The
     * silver is spent either way, so the mission comes home empty.
     */
    private fun arriveEspionage(m: CityMovement, origin: CityState, target: CityState) {
        val silver = m.carriedSilver
        val success = silver > target.resources.caveSilver
        val names = refs(setOf(origin.cityId, target.cityId))
        val worldId = target.city.worldId

        reportService.write(
            worldId = worldId, ownerUserId = origin.city.ownerUserId, kind = ReportService.Kind.ESPIONAGE,
            createdAt = origin.now, subjectCity = origin.city.name, otherCity = target.city.name,
            otherPlayer = names[target.cityId]?.player, won = success,
            // How it went is the report's own verdict; the title only says what was attempted.
            summary = "Espionage of ${target.city.name}",
            payload = EspionagePayload(success, silver, seen = if (success) seen(target) else null),
        )
        if (!success) {
            reportService.write(
                worldId = worldId, ownerUserId = target.city.ownerUserId, kind = ReportService.Kind.ESPIONAGE_CAUGHT,
                createdAt = target.now, subjectCity = target.city.name, otherCity = origin.city.name,
                otherPlayer = names[origin.cityId]?.player, won = true,
                summary = "Caught a spy from ${origin.city.name}",
                payload = CaughtPayload(names[origin.cityId]?.player ?: "", origin.city.name, silver),
            )
        }
        // The silver bought the attempt: nothing comes back with the mission, and the way home is the
        // cooldown that holds the city to one mission per target.
        m.carriedSilver = 0
        turnAroundAt(m, MovementRules.SPY_SPEED, origin, target)
    }

    /** The city as the spy found it. Its Cave silver is not in here: hiding that is what the Cave is for. */
    private fun seen(target: CityState): SeenPayload {
        val standing = merge(
            listOf(target.units.mapValues { it.value.count }) +
                citySupportRepository.findAllByIdHostCityId(target.cityId)
                    .filter { it.id.ownerCityId != target.cityId }
                    .groupBy { it.id.ownerCityId }
                    .map { (_, rows) -> rows.associate { it.id.unit to it.count } },
        )
        return SeenPayload(
            resources = ResourcesPayload(target.resources.wood, target.resources.stone, target.resources.silver),
            buildings = Building.entries.mapNotNull { b ->
                target.level(b).takeIf { it > 0 }?.let { BuildingLevelPayload(b, it) }
            },
            units = standing.entries.filter { it.value > 0 }.map { UnitCountPayload(it.key, it.key.displayName, it.value) },
        )
    }

    /** The way home takes as long as the way out, at the speed of whoever is left. */
    private fun turnAround(m: CityMovement, units: Map<Unit, Int>, origin: CityState, target: CityState) =
        turnAroundAt(m, MovementRules.slowestSpeed(units), origin, target)

    private fun turnAroundAt(m: CityMovement, speed: Int, origin: CityState, target: CityState) {
        val (ox, oy) = cityAccess.coordinates(origin)
        val (tx, ty) = cityAccess.coordinates(target)
        val seconds = MovementRules.travelSeconds(MovementRules.distance(ox, oy, tx, ty), speed)
        m.direction = MovementDirection.HOMEWARD
        m.departsAt = m.arrivesAt
        m.arrivesAt = m.arrivesAt.plusSeconds(seconds)
    }

    /**
     * What the battle earned each side: the **population it killed**. The attacker takes all of it for
     * the defenders that died; the defence is split between the city and every supporter in proportion
     * to what each contributed, the remainder going to the city that was attacked.
     */
    private fun awardBattlePoints(
        origin: CityState,
        target: CityState,
        supporters: Map<Long, Long>,
        killedDefending: Long,
        killedAttacking: Long,
        defenceByHolder: Map<Long, Double>,
    ) {
        val worldId = target.city.worldId
        rankingService.award(worldId, origin.city.ownerUserId, attack = killedDefending)
        if (killedAttacking <= 0) return

        // Every holder's share of the whole defence, the city's own included; what is not given to a
        // supporter stays with the city that was attacked, so nothing is lost to rounding.
        val total = defenceByHolder.values.sum()
        var given = 0L
        if (total > 0) {
            for ((holder, power) in defenceByHolder) {
                if (holder == target.cityId) continue
                val userId = supporters[holder] ?: continue
                val share = (killedAttacking * (power / total)).toLong()
                rankingService.award(worldId, userId, defence = share)
                given += share
            }
        }
        rankingService.award(worldId, target.city.ownerUserId, defence = killedAttacking - given)
    }

    /**
     * Gives a third city back the population of the support it lost here. Its row is not one of the two
     * this arrival locks, so it is written straight, in id order, and never read back into a state.
     */
    private fun freePopulation(byCity: Map<Long, Long>) {
        for ((cityId, freed) in byCity.toSortedMap()) {
            if (freed <= 0) continue
            jdbc.update(
                "UPDATE city_resources SET population = population + :n WHERE city_id = :id",
                mapOf("n" to freed, "id" to cityId),
            )
        }
    }

    /** Both rows in one ordered statement, so two movements crossing between the same pair cannot deadlock. */
    private fun lockInIdOrder(a: Long, b: Long) {
        jdbc.queryForList(
            "SELECT city_id FROM city_resources WHERE city_id IN (:a, :b) ORDER BY city_id FOR UPDATE",
            mapOf("a" to a, "b" to b), Long::class.java,
        )
    }

    private fun addUnits(state: CityState, u: Unit, n: Int) {
        val row = state.units[u]
        if (row == null) {
            state.units[u] = cityUnitRepository.save(CityUnit(CityUnitId(state.cityId, u), n))
        } else {
            row.count += n
            cityUnitRepository.save(row)
        }
    }

    /** Replaces what the movement carries; a type that is gone loses its row. */
    private fun writeUnits(movementId: Long, units: Map<Unit, Int>) {
        val existing = movementUnitRepository.findAllByIdMovementId(movementId).associateBy { it.id.unit }
        for ((u, row) in existing) if ((units[u] ?: 0) <= 0) movementUnitRepository.delete(row)
        for ((u, n) in units.filterValues { it > 0 }) {
            val row = existing[u]
            movementUnitRepository.save(if (row == null) CityMovementUnit(CityMovementUnitId(movementId, u), n) else row.also { it.count = n })
        }
    }

    private fun unitsOf(m: CityMovement): Map<Unit, Int> =
        movementUnitRepository.findAllByIdMovementId(requireNotNull(m.id)).associate { it.id.unit to it.count }

    private fun unitsOf(movements: List<CityMovement>): Map<Long, Map<Unit, Int>> {
        if (movements.isEmpty()) return emptyMap()
        return movementUnitRepository.findAllByIdMovementIdIn(movements.map { requireNotNull(it.id) })
            .groupBy { it.id.movementId }
            .mapValues { (_, rows) -> rows.associate { it.id.unit to it.count } }
    }

    /**
     * True for the last [SIGHTED] of an incoming movement's flight: before that the city can only see
     * that something is on its way, not what.
     */
    private fun nearlyHere(m: CityMovement, now: Instant): Boolean {
        val whole = Duration.between(m.departsAt, m.arrivesAt)
        if (whole.isZero || whole.isNegative) return true
        val left = Duration.between(now, m.arrivesAt)
        return left <= Duration.ofMillis((whole.toMillis() * SIGHTED).toLong())
    }

    private data class CityRef(val name: String, val x: Int, val y: Int, val player: String)

    /** The other end of a movement, as a player reads it: whose city it is, not where it is. */
    private fun refs(cityIds: Set<Long>): Map<Long, CityRef> {
        if (cityIds.isEmpty()) return emptyMap()
        val cities = cityRepository.findAllById(cityIds)
        val slots = citySlotRepository.findAllById(cities.map { it.slotId }).associateBy { requireNotNull(it.id) }
        val owners = jdbc.queryForList(
            "SELECT c.id AS city_id, u.nickname FROM city c JOIN users u ON u.id = c.owner_user_id WHERE c.id IN (:ids)",
            mapOf("ids" to cityIds),
        ).associate { (it["city_id"] as Number).toLong() to it["nickname"] as String }
        return cities.mapNotNull { c ->
            val slot = slots[c.slotId] ?: return@mapNotNull null
            requireNotNull(c.id) to CityRef(c.name, slot.x.toInt(), slot.y.toInt(), owners[c.id] ?: "")
        }.toMap()
    }

    private companion object {
        /** The share of its flight an attack is in sight for: the last quarter. */
        const val SIGHTED = 0.25

        /** One pass processes a chain of legs (out, home, and a recall in between); a bound, never reached. */
        const val MAX_ARRIVALS = 64
        val processing: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    }
}
