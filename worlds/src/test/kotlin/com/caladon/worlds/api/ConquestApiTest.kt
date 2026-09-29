package com.caladon.worlds.api

import com.caladon.worlds.army.CityMovementRepository
import com.caladon.worlds.army.CitySupportRepository
import com.caladon.worlds.army.CityUnit
import com.caladon.worlds.army.CityUnitId
import com.caladon.worlds.army.CityUnitRepository
import com.caladon.worlds.buildings.CityBuildOrderRepository
import com.caladon.worlds.resources.CitySweeper
import com.caladon.worlds.rules.ConquestRules
import com.caladon.worlds.rules.Unit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.ResultActionsDsl
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

/**
 * Taking a city (ARCHITECTURE.md → Conquest). Three cities: the attacker's, the defender's, and a third
 * the defender owns too, so a counter-attack on his own held city has somewhere to come from.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(MutableClockConfig::class)
class ConquestApiTest : ApiTestBase() {

    @Autowired lateinit var cityUnitRepository: CityUnitRepository
    @Autowired lateinit var citySupportRepository: CitySupportRepository
    @Autowired lateinit var movementRepository: CityMovementRepository
    @Autowired lateinit var buildOrderRepository: CityBuildOrderRepository
    @Autowired lateinit var sweeper: CitySweeper

    private data class Field(val world: Long, val mine: Long, val theirs: Long, val x: Int, val y: Int)

    /** The defender's city sits three east and four north of the attacker's: five fields, always. */
    private fun field(): Field {
        val world = createPlayableWorld()
        val mine = json(post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val theirs = json(post("/api/v1/worlds/$world/join", otherPlayerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val slot = citySlotRepository.findById(cityRepository.findById(mine).orElseThrow().slotId).orElseThrow()
        place(theirs, slot.x + 3, slot.y + 4)
        return Field(world, mine, theirs, slot.x + 3, slot.y + 4)
    }

    private fun place(cityId: Long, x: Int, y: Int) {
        val city = cityRepository.findById(cityId).orElseThrow()
        citySlotRepository.findByWorldIdAndXAndY(city.worldId, x.toShort(), y.toShort())
            ?.takeIf { it.id != city.slotId }
            ?.let { blocking ->
                var free = 499
                while (citySlotRepository.findByWorldIdAndXAndY(city.worldId, free.toShort(), free.toShort()) != null) free--
                blocking.x = free.toShort(); blocking.y = free.toShort()
                citySlotRepository.saveAndFlush(blocking)
            }
        val slot = citySlotRepository.findById(city.slotId).orElseThrow()
        slot.x = x.toShort(); slot.y = y.toShort()
        citySlotRepository.saveAndFlush(slot)
    }

    private fun give(cityId: Long, unit: Unit, count: Int) =
        cityUnitRepository.save(CityUnit(CityUnitId(cityId, unit), count))

    private fun jump(seconds: Long) {
        clock.advance(Duration.ofSeconds(seconds))
        playerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("george@caladon.test")))
        otherPlayerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("ana@caladon.test")))
    }

    private fun attack(f: Field, units: Map<String, Int>, from: Long = f.mine, token: String = playerToken, x: Int = f.x, y: Int = f.y): ResultActionsDsl =
        post(
            "/api/v1/worlds/${f.world}/cities/$from/movements", token,
            mapOf("kind" to "ATTACK", "targetX" to x, "targetY" to y, "units" to units),
        )

    private fun detail(f: Field, cityId: Long, token: String) =
        json(get("/api/v1/worlds/${f.world}/cities/$cityId", token).andExpect { status { isOk() } })

    /** The attack that wins and stays. Five fields at the Nobleman's 35 minutes a field is 10500s. */
    private fun takeAndHold(f: Field): Field {
        give(f.mine, Unit.AXEMAN, 50)
        give(f.mine, Unit.NOBLEMAN, 1)
        attack(f, mapOf("AXEMAN" to 50, "NOBLEMAN" to 1)).andExpect { status { isOk() } }
        jump(10_500)
        sweeper.sweep()
        return f
    }

    @Test
    fun `a nobleman that survives the battle holds the city instead of coming home`() {
        val f = takeAndHold(field())

        val held = cityRepository.findById(f.theirs).orElseThrow()
        assertThat(held.occupied).isTrue()
        assertThat(held.occupationEndsAt).isNotNull()
        // The city is still the defender's: it changes hands only when the hold runs out.
        assertThat(held.ownerUserId).isNotEqualTo(cityRepository.findById(f.mine).orElseThrow().ownerUserId)

        // The attack did not turn around: its troops are standing in the city as support.
        assertThat(movementRepository.findAllByOriginCityIdAndAppliedFalseOrderByArrivesAtAscIdAsc(f.mine)).isEmpty()
        val garrison = citySupportRepository.findAllByIdHostCityId(f.theirs).associate { it.id.unit to it.count }
        assertThat(garrison[Unit.NOBLEMAN]).isEqualTo(1)
        assertThat(garrison[Unit.AXEMAN]).isGreaterThan(0)
    }

    @Test
    fun `a held city does nothing and produces nothing`() {
        val f = takeAndHold(field())
        val before = detail(f, f.theirs, otherPlayerToken)
        assertThat(before["occupation"]["player"].asText()).isEqualTo("george")

        post(
            "/api/v1/worlds/${f.world}/cities/${f.theirs}/buildings/FARM/upgrade", otherPlayerToken, null,
        ).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("OCCUPIED") }
        }
        post(
            "/api/v1/worlds/${f.world}/cities/${f.theirs}/army/recruit", otherPlayerToken,
            mapOf("unit" to "SPEARMAN", "count" to 1),
        ).andExpect { jsonPath("$.error") { value("OCCUPIED") } }

        // An hour of a stopped city adds nothing to its stocks, and its rate reads as nothing too.
        assertThat(before["resources"]["wood"]["ratePerHour"].asLong()).isZero()
        val woodBefore = before["resources"]["wood"]["stock"].asLong()
        jump(3600)
        sweeper.sweep()
        assertThat(detail(f, f.theirs, otherPlayerToken)["resources"]["wood"]["stock"].asLong()).isEqualTo(woodBefore)
    }

    @Test
    fun `the owner may attack his own held city, and breaking the garrison ends the hold`() {
        val f = takeAndHold(field())
        // A city of his own to counter-attack from, at the same five fields from the held one: a third
        // player joins for the slot, and the city is handed to the man whose city is being held.
        val bystander = tokenFor(createUser("relief@caladon.test", "relief", com.caladon.users.domain.Role.PLAYER))
        val relief = json(post("/api/v1/worlds/${f.world}/join", bystander).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        cityRepository.findById(relief).orElseThrow().also {
            it.ownerUserId = requireNotNull(requireNotNull(userRepository.findByEmailIgnoreCase("ana@caladon.test")).id)
        }.let(cityRepository::saveAndFlush)
        place(relief, f.x + 3, f.y + 4)
        give(relief, Unit.AXEMAN, 4000)

        attack(f, mapOf("AXEMAN" to 4000), from = relief, token = otherPlayerToken, x = f.x, y = f.y)
            .andExpect { status { isOk() } }
        jump(5400)
        sweeper.sweep()

        val freed = cityRepository.findById(f.theirs).orElseThrow()
        assertThat(freed.occupied).isFalse()
        assertThat(freed.occupationEndsAt).isNull()
        assertThat(citySupportRepository.findAllByIdHostCityId(f.theirs)).isEmpty()
    }

    @Test
    fun `the city changes hands when the hold runs out, garrison and all`() {
        val f = takeAndHold(field())
        val conqueror = cityRepository.findById(f.mine).orElseThrow().ownerUserId

        jump(ConquestRules.occupationSeconds() + 1)
        sweeper.sweep()

        val taken = cityRepository.findById(f.theirs).orElseThrow()
        assertThat(taken.ownerUserId).isEqualTo(conqueror)
        assertThat(taken.occupied).isFalse()
        // The men who took it are still in it, now the new owner's support in his own new city.
        assertThat(citySupportRepository.findAllByIdHostCityId(f.theirs)).isNotEmpty()
        // The standings follow the cities, so the conqueror now counts two.
        val board = json(get("/api/v1/worlds/${f.world}/ranking", playerToken).andExpect { status { isOk() } })
        assertThat(board["rows"].first { it["player"].asText() == "george" }["cities"].asInt()).isEqualTo(2)
    }

    @Test
    fun `the queues of a held city are thrown away`() {
        val f = field()
        post(
            "/api/v1/worlds/${f.world}/cities/${f.theirs}/buildings/FARM/upgrade", otherPlayerToken, null,
        ).andExpect { status { isOk() } }
        assertThat(buildOrderRepository.findAllByCityIdOrderByCompletesAtAscIdAsc(f.theirs)).isNotEmpty()

        takeAndHold(f)
        assertThat(buildOrderRepository.findAllByCityIdOrderByCompletesAtAscIdAsc(f.theirs)).isEmpty()
    }
}
