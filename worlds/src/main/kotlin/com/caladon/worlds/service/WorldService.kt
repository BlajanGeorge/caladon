package com.caladon.worlds.service

import com.caladon.users.domain.Role
import com.caladon.users.repository.UserRepository
import com.caladon.worlds.domain.City
import com.caladon.worlds.domain.CityResources
import com.caladon.worlds.domain.Resource
import com.caladon.worlds.domain.World
import com.caladon.worlds.domain.WorldMembership
import com.caladon.worlds.domain.WorldMembershipId
import com.caladon.worlds.domain.WorldState
import com.caladon.worlds.generation.PlacementGenerator
import com.caladon.worlds.generation.TerrainGenerator
import com.caladon.worlds.map.MapQueryDao
import com.caladon.worlds.map.TerrainCache
import com.caladon.worlds.map.TerrainStore
import com.caladon.worlds.map.Viewport
import com.caladon.worlds.repository.CityRepository
import com.caladon.worlds.repository.CityResourcesRepository
import com.caladon.worlds.repository.CitySlotRepository
import com.caladon.worlds.repository.WorldMembershipRepository
import com.caladon.worlds.repository.WorldRepository
import org.slf4j.LoggerFactory
import com.caladon.worlds.resources.ResourceConstants
import com.caladon.worlds.resources.Settlement
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock

@Service
class WorldService(
    private val worldRepository: WorldRepository,
    private val cityRepository: CityRepository,
    private val cityResourcesRepository: CityResourcesRepository,
    private val citySlotRepository: CitySlotRepository,
    private val membershipRepository: WorldMembershipRepository,
    private val userRepository: UserRepository,
    private val terrainGenerator: TerrainGenerator,
    private val placementGenerator: PlacementGenerator,
    private val terrainStore: TerrainStore,
    private val terrainCache: TerrainCache,
    private val mapQueryDao: MapQueryDao,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val seeds = SecureRandom()

    data class WorldSummary(val world: World, val players: Long)
    data class StartCity(val id: Long, val x: Int, val y: Int, val name: String, val points: Int)
    data class JoinResult(val worldId: Long, val startCity: StartCity)
    data class CityDetail(
        val id: Long, val name: String, val x: Int, val y: Int, val points: Int,
        val stocks: Map<Resource, Long>, val ratePerMinute: Double, val capacity: Int,
        val settledAt: java.time.Instant, val population: Int,
    ) {
        fun stock(r: Resource): Long = stocks.getValue(r)
    }
    data class MapView(
        val viewport: Viewport,
        val terrain: IntArray,
        val slots: List<com.caladon.worlds.generation.Tile>,
        val cities: List<MapQueryDao.CityOnMap>,
        val barbarians: List<com.caladon.worlds.generation.Tile>,
    )

    // ---- administrator ----

    /** Creates and fully seeds a world (synchronously); it starts in [WorldState.DRAFT]. */
    @Transactional
    fun create(name: String, adminUserId: Long): World {
        val trimmed = name.trim()
        if (worldRepository.existsByNameIgnoreCase(trimmed)) throw WorldException.NameTaken()

        // One-off randomness for the generators; not persisted, worlds are not regenerated.
        val seed = seeds.nextLong()
        val terrain = terrainGenerator.generate(seed)
        val placement = placementGenerator.generate(terrain, seed)

        val world = try {
            // The terrain blob is not mapped on the entity; it is written through TerrainStore right after.
            worldRepository.saveAndFlush(
                World(name = trimmed, state = WorldState.DRAFT, createdBy = adminUserId, createdAt = clock.instant()),
            )
        } catch (e: DataIntegrityViolationException) {
            if (worldRepository.existsByNameIgnoreCase(trimmed)) throw WorldException.NameTaken()
            throw e
        }
        val worldId = requireNotNull(world.id)
        terrainStore.write(worldId, terrain)
        mapQueryDao.insertSlots(worldId, placement.slots)
        mapQueryDao.insertBarbarians(worldId, placement.barbarians)
        terrainCache.put(worldId, terrain)

        log.info("Created world {} '{}' slots={} barbarians={}", worldId, trimmed, placement.slots.size, placement.barbarians.size)
        return world
    }

    @Transactional(readOnly = true)
    fun listAll(): List<WorldSummary> {
        val counts = membershipRepository.countPerWorld().associate { (it[0] as Long) to (it[1] as Long) }
        return worldRepository.findAllByOrderByIdAsc().map { WorldSummary(it, counts[it.id] ?: 0L) }
    }

    @Transactional
    fun approve(worldId: Long): World {
        val world = worldRepository.findById(worldId).orElse(null) ?: throw WorldException.NotFound()
        if (world.state != WorldState.DRAFT) throw WorldException.NotDraft()
        world.state = WorldState.PLAYABLE
        return world
    }

    // ---- player ----

    @Transactional(readOnly = true)
    fun listPlayable(userId: Long): List<Pair<World, Boolean>> {
        val joined = membershipRepository.findWorldIdsByUserId(userId).toSet()
        return worldRepository.findAllByStateOrderByIdAsc(WorldState.PLAYABLE).map { it to (it.id in joined) }
    }

    @Transactional(readOnly = true)
    fun listMine(userId: Long): List<World> {
        val ids = membershipRepository.findWorldIdsByUserId(userId)
        return worldRepository.findAllById(ids).sortedBy { it.id }
    }

    /** Enrols the player and founds their first city at the frontier. Joins are serialized per world. */
    @Transactional
    fun join(worldId: Long, userId: Long): JoinResult {
        val world = worldRepository.findWithLockById(worldId) ?: throw WorldException.NotFound()
        if (world.state != WorldState.PLAYABLE) throw WorldException.NotPlayable()
        val membershipId = WorldMembershipId(worldId, userId)
        if (membershipRepository.existsById(membershipId)) throw WorldException.AlreadyJoined()

        val user = userRepository.findById(userId).orElseThrow()
        val slot = mapQueryDao.findStartSlot(worldId) ?: throw WorldException.Full()
        val now = clock.instant()
        membershipRepository.save(WorldMembership(membershipId, joinedAt = now))
        val city = cityRepository.save(
            City(worldId = worldId, slotId = slot.id, ownerUserId = userId, name = "${user.nickname}'s city", createdAt = now),
        )
        cityResourcesRepository.save(
            CityResources(
                cityId = requireNotNull(city.id),
                wood = ResourceConstants.STARTING_STOCK,
                stone = ResourceConstants.STARTING_STOCK,
                iron = ResourceConstants.STARTING_STOCK,
                settledAt = now,
                population = ResourceConstants.STARTING_POPULATION,
            ),
        )
        return JoinResult(worldId, StartCity(requireNotNull(city.id), slot.x, slot.y, city.name, city.points))
    }

    /** The player's cities in a world. Requires membership; DRAFT worlds are invisible to players. */
    @Transactional(readOnly = true)
    fun myCities(worldId: Long, userId: Long): List<MapQueryDao.OwnedCity> {
        val world = worldRepository.findById(worldId).orElse(null) ?: throw WorldException.NotFound()
        if (world.state == WorldState.DRAFT) throw WorldException.NotFound()
        if (!membershipRepository.existsById(WorldMembershipId(worldId, userId))) throw WorldException.NotJoined()
        return mapQueryDao.citiesOwnedBy(worldId, userId)
    }

    /**
     * One of the caller's cities with its resources settled to now. The settlement is persisted under a
     * row lock so concurrent reads/spends never double-count production.
     */
    @Transactional
    fun cityDetail(worldId: Long, cityId: Long, userId: Long, requesterRole: Role): CityDetail {
        val world = worldRepository.findById(worldId).orElse(null) ?: throw WorldException.CityNotFound()
        if (world.state == WorldState.DRAFT && requesterRole != Role.ADMINISTRATOR) throw WorldException.CityNotFound()
        val city = cityRepository.findById(cityId).orElse(null)?.takeIf { it.worldId == worldId } ?: throw WorldException.CityNotFound()
        if (city.ownerUserId != userId) throw WorldException.NotOwner()
        val slot = citySlotRepository.findById(city.slotId).orElseThrow()

        val res = cityResourcesRepository.findWithLockByCityId(cityId) ?: throw WorldException.CityNotFound()
        val now = clock.instant()
        val rate = ResourceConstants.PRODUCTION_PER_MINUTE * ResourceConstants.WORLD_SPEED
        val capacity = ResourceConstants.CAPACITY
        val elapsed = Settlement.elapsedMinutes(res.settledAt, now)
        for (r in Resource.entries) res.setStock(r, Settlement.settle(res.stock(r), rate, capacity, elapsed))
        res.settledAt = if (now.isAfter(res.settledAt)) now else res.settledAt

        return CityDetail(
            id = cityId, name = city.name, x = slot.x.toInt(), y = slot.y.toInt(), points = city.points,
            stocks = Resource.entries.associateWith { res.stock(it).toLong() },
            ratePerMinute = rate, capacity = capacity, settledAt = res.settledAt, population = res.population,
        )
    }

    /** Everything inside the rectangle. DRAFT worlds are visible to administrators only. */
    @Transactional(readOnly = true)
    fun map(worldId: Long, viewport: Viewport, requesterRole: Role): MapView {
        val violations = viewport.violations()
        if (violations.isNotEmpty()) throw WorldException.InvalidViewport(violations)

        val world = worldRepository.findById(worldId).orElse(null) ?: throw WorldException.NotFound()
        if (world.state != WorldState.PLAYABLE && requesterRole != Role.ADMINISTRATOR) throw WorldException.NotFound()
        val terrain = terrainCache.get(worldId) ?: throw WorldException.NotFound()

        return MapView(
            viewport = viewport,
            terrain = viewport.slice(terrain),
            slots = mapQueryDao.freeSlotsIn(worldId, viewport),
            cities = mapQueryDao.citiesIn(worldId, viewport),
            barbarians = mapQueryDao.barbariansIn(worldId, viewport),
        )
    }
}
