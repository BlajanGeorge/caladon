package com.caladon.worlds.api

import com.caladon.worlds.army.CityMovement
import com.caladon.worlds.army.CityMovementRepository
import com.caladon.worlds.army.MovementDirection
import com.caladon.worlds.army.MovementKind
import com.caladon.worlds.army.CitySupportRepository
import com.caladon.worlds.army.CityUnit
import com.caladon.worlds.army.CityUnitId
import com.caladon.worlds.army.CityUnitRepository
import com.caladon.worlds.buildings.CityBuilding
import com.caladon.worlds.buildings.CityBuildingId
import com.caladon.worlds.buildings.CityBuildingRepository
import com.caladon.worlds.resources.CitySweeper
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.Unit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(MutableClockConfig::class)
class MovementApiTest : ApiTestBase() {

    @Autowired lateinit var cityUnitRepository: CityUnitRepository
    @Autowired lateinit var citySupportRepository: CitySupportRepository
    @Autowired lateinit var cityBuildingRepository: CityBuildingRepository
    @Autowired lateinit var movementRepository: CityMovementRepository
    @Autowired lateinit var sweeper: CitySweeper

    /**
     * Two cities in one world, the second moved to exactly 3 fields east and 4 north of the first, so
     * every travel time in this test is five fields at the unit's own speed.
     */
    private data class Two(val world: Long, val mine: Long, val theirs: Long, val x: Int, val y: Int)

    private fun twoCities(): Two {
        val world = createPlayableWorld()
        val mine = json(post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val theirs = json(post("/api/v1/worlds/$world/join", otherPlayerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val (x, y) = coordinates(mine)
        place(theirs, x + 3, y + 4)
        return Two(world, mine, theirs, x + 3, y + 4)
    }

    /**
     * Moves the clock on and re-issues the access tokens: they are cut on the same clock the arrivals run
     * on, and the hours a movement spends on the road are longer than a token lives.
     */
    private fun jump(seconds: Long) {
        clock.advance(Duration.ofSeconds(seconds))
        adminToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("admin@caladon.test")))
        playerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("george@caladon.test")))
        otherPlayerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("ana@caladon.test")))
    }

    private fun coordinates(cityId: Long): Pair<Int, Int> {
        val slot = citySlotRepository.findById(cityRepository.findById(cityId).orElseThrow().slotId).orElseThrow()
        return slot.x.toInt() to slot.y.toInt()
    }

    /**
     * Moves a city onto an exact field. Whatever slot is already there is shifted aside rather than
     * deleted: the generated map may have put another player's city on it, and deleting that slot breaks
     * the city pointing at it.
     */
    private fun place(cityId: Long, x: Int, y: Int) {
        val city = cityRepository.findById(cityId).orElseThrow()
        citySlotRepository.findByWorldIdAndXAndY(city.worldId, x.toShort(), y.toShort())
            ?.takeIf { it.id != city.slotId }
            ?.let { blocking ->
                var free = 499
                while (citySlotRepository.findByWorldIdAndXAndY(city.worldId, free.toShort(), free.toShort()) != null) free--
                blocking.x = free.toShort()
                blocking.y = free.toShort()
                citySlotRepository.saveAndFlush(blocking)
            }
        val slot = citySlotRepository.findById(city.slotId).orElseThrow()
        slot.x = x.toShort()
        slot.y = y.toShort()
        citySlotRepository.saveAndFlush(slot)
    }

    private fun give(cityId: Long, unit: Unit, count: Int) =
        cityUnitRepository.save(CityUnit(CityUnitId(cityId, unit), count))

    private fun setLevel(cityId: Long, b: Building, level: Int) =
        cityBuildingRepository.save(CityBuilding(CityBuildingId(cityId, b), level))

    private fun stock(cityId: Long, amount: Long) {
        val row = cityResourcesRepository.findById(cityId).orElseThrow()
        row.wood = amount; row.stone = amount; row.silver = amount
        cityResourcesRepository.save(row)
    }

    private fun send(two: Two, kind: String, units: Map<String, Int>, token: String = playerToken, from: Long = two.mine, x: Int = two.x, y: Int = two.y) =
        post(
            "/api/v1/worlds/${two.world}/cities/$from/movements", token,
            mapOf("kind" to kind, "targetX" to x, "targetY" to y, "units" to units),
        )

    private fun movements(two: Two, cityId: Long, token: String) =
        json(get("/api/v1/worlds/${two.world}/cities/$cityId/movements", token).andExpect { status { isOk() } })

    private fun recall(two: Two, cityId: Long, id: Long, token: String = playerToken): ResultActionsDsl =
        mockMvc.delete("/api/v1/worlds/${two.world}/cities/$cityId/movements/$id") { header("Authorization", "Bearer $token") }

    @Test
    fun `sending takes the units out of the city and puts the movement in outgoing`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 12)
        val t0 = clock.instant()
        send(two, "ATTACK", mapOf("SPEARMAN" to 10)).andExpect {
            status { isOk() }
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(2) }
            jsonPath("$.population") { value(240) }     // the road costs the origin no extra population
        }
        assertThat(cityUnitRepository.findAllByIdCityId(two.mine).single().count).isEqualTo(2)

        val mine = movements(two, two.mine, playerToken)
        assertThat(mine["serverTime"].asText()).isEqualTo(t0.toString())
        assertThat(mine["incoming"]).isEmpty()
        val out = mine["outgoing"].single()
        assertThat(out["kind"].asText()).isEqualTo("ATTACK")
        assertThat(out["direction"].asText()).isEqualTo("OUTWARD")
        assertThat(out["otherCityName"].asText()).isEqualTo("ana's city")
        assertThat(out["otherPlayerName"].asText()).isEqualTo("ana")
        assertThat(out["x"].asInt()).isEqualTo(two.x)
        assertThat(out["y"].asInt()).isEqualTo(two.y)
        assertThat(out["departsAt"].asText()).isEqualTo(t0.toString())
        assertThat(out["arrivesAt"].asText()).isEqualTo(t0.plusSeconds(5400).toString())   // 5 fields × 18 min
        assertThat(out["units"].single()["type"].asText()).isEqualTo("SPEARMAN")
        assertThat(out["units"].single()["name"].asText()).isEqualTo("Spearman")
        assertThat(out["units"].single()["count"].asInt()).isEqualTo(10)
        assertThat(out["carrying"]["wood"].asLong()).isZero()
        assertThat(out["canRecall"].asBoolean()).isTrue()
    }

    @Test
    fun `an incoming attack shows its arrival and nothing else`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 10)
        send(two, "ATTACK", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }

        val theirs = movements(two, two.theirs, otherPlayerToken)
        assertThat(theirs["outgoing"]).isEmpty()
        val incoming = theirs["incoming"].single()
        assertThat(incoming["kind"].asText()).isEqualTo("ATTACK")
        assertThat(incoming["otherCityName"].asText()).isEqualTo("george's city")
        assertThat(incoming["units"]).isEmpty()
        assertThat(incoming["carrying"].isNull).isTrue()
        assertThat(incoming["canRecall"].asBoolean()).isFalse()
    }

    @Test
    fun `a spy mission heading at a city is not listed there at all`() {
        val two = twoCities()
        // Written straight in: nothing can send one yet, and the point is what the target is shown.
        val spy = movementRepository.save(
            CityMovement(
                worldId = two.world, originCityId = two.mine, targetCityId = two.theirs,
                kind = MovementKind.ESPIONAGE, direction = MovementDirection.OUTWARD,
                departsAt = clock.instant(), arrivesAt = clock.instant().plusSeconds(1800), carriedSilver = 1200,
            ),
        )

        // The city it is aimed at sees nothing: a spy you can see coming is not a spy.
        assertThat(movements(two, two.theirs, otherPlayerToken)["incoming"]).isEmpty()

        // The city that sent it sees it, with the silver it carries.
        val out = movements(two, two.mine, playerToken)["outgoing"].single()
        assertThat(out["id"].asLong()).isEqualTo(requireNotNull(spy.id))
        assertThat(out["kind"].asText()).isEqualTo("ESPIONAGE")
        assertThat(out["units"]).isEmpty()
        assertThat(out["carrying"]["silver"].asLong()).isEqualTo(1200)
    }

    @Test
    fun `an incoming attack is only made out in the last quarter of its flight`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 10)
        send(two, "ATTACK", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }   // 5400 s away

        // Three quarters of the way: still nothing but an arrival time.
        jump(4049)
        val far = movements(two, two.theirs, otherPlayerToken)["incoming"].single()
        assertThat(far["units"]).isEmpty()
        assertThat(far["carrying"].isNull).isTrue()

        // Past that line the city makes out what is coming.
        jump(2)
        val near = movements(two, two.theirs, otherPlayerToken)["incoming"].single()
        assertThat(near["units"].single()["type"].asText()).isEqualTo("SPEARMAN")
        assertThat(near["units"].single()["count"].asInt()).isEqualTo(10)
        assertThat(near["carrying"].isNull).isFalse()
    }

    @Test
    fun `support arrives, stands in the host city and is listed by both`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 10)
        send(two, "SUPPORT", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }
        // Support is a friend's: the host sees what is coming.
        assertThat(movements(two, two.theirs, otherPlayerToken)["incoming"].single()["units"].single()["count"].asInt()).isEqualTo(10)

        jump(5401)
        get("/api/v1/worlds/${two.world}/cities/${two.theirs}", otherPlayerToken).andExpect {
            status { isOk() }
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(0) }
            jsonPath("$.units[?(@.type=='SPEARMAN')].supporting") { value(10) }
        }
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs).single().count).isEqualTo(10)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].sentAway") { value(10) }
        }
        assertThat(movements(two, two.mine, playerToken)["outgoing"]).isEmpty()
        assertThat(movements(two, two.theirs, otherPlayerToken)["incoming"]).isEmpty()
    }

    @Test
    fun `the sweeper lands an arrival nobody was watching`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 10)
        send(two, "SUPPORT", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }
        assertThat(sweeper.sweep()).isZero()

        jump(5401)
        assertThat(sweeper.sweep()).isEqualTo(2)   // both ends of the movement are due
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs).single().count).isEqualTo(10)
        assertThat(sweeper.sweep()).isZero()
    }

    /** Hands a city to another player, to make two of a player's own cities out of two players'. */
    private fun handOver(cityId: Long, email: String) {
        val city = cityRepository.findById(cityId).orElseThrow()
        city.ownerUserId = requireNotNull(requireNotNull(userRepository.findByEmailIgnoreCase(email)).id)
        cityRepository.saveAndFlush(city)
    }

    @Test
    fun `a player supports his own city but may not attack or spy on it`() {
        val two = twoCities()
        handOver(two.theirs, "george@caladon.test")
        give(two.mine, Unit.SPEARMAN, 6)
        setLevel(two.mine, Building.CAVE, 1)
        cityResourcesRepository.findById(two.mine).orElseThrow().also { it.caveSilver = 500 }
            .let(cityResourcesRepository::save)

        send(two, "ATTACK", mapOf("SPEARMAN" to 3)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("OWN_CITY") }
        }
        post(
            "/api/v1/worlds/${two.world}/cities/${two.mine}/spy", playerToken,
            mapOf("targetX" to two.x, "targetY" to two.y, "silver" to 100),
        ).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("OWN_CITY") }
        }
        // Neither refusal took anything: the troops are still at home and the silver is still in the Cave.
        assertThat(cityUnitRepository.findById(CityUnitId(two.mine, Unit.SPEARMAN)).orElseThrow().count).isEqualTo(6)
        assertThat(cityResourcesRepository.findById(two.mine).orElseThrow().caveSilver).isEqualTo(500)

        // Helping one of his own cities is the whole reason to hold more than one.
        send(two, "SUPPORT", mapOf("SPEARMAN" to 3)).andExpect { status { isOk() } }
    }

    @Test
    fun `an attack on an empty city turns straight around and brings the troops home`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 5)
        val t0 = clock.instant()
        send(two, "ATTACK", mapOf("SPEARMAN" to 5)).andExpect { status { isOk() } }

        jump(5401)
        val turned = movements(two, two.mine, playerToken)["outgoing"].single()
        assertThat(turned["direction"].asText()).isEqualTo("HOMEWARD")
        assertThat(turned["canRecall"].asBoolean()).isFalse()
        assertThat(turned["arrivesAt"].asText()).isEqualTo(t0.plusSeconds(10800).toString())   // the way back is the way out
        assertThat(cityUnitRepository.findById(CityUnitId(two.mine, Unit.SPEARMAN)).orElseThrow().count).isZero()

        // Flying home is flying away from the city it hit: it must not stand in that city's incoming.
        assertThat(movements(two, two.theirs, otherPlayerToken)["incoming"]).isEmpty()

        jump(5400)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(5) }
        }
        assertThat(movements(two, two.mine, playerToken)["outgoing"]).isEmpty()
    }

    @Test
    fun `a recall turns the movement around with the time it has already flown`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 10)
        val id = json(send(two, "ATTACK", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } })
            .let { movements(two, two.mine, playerToken)["outgoing"].single()["id"].asLong() }

        jump(600)
        val at = clock.instant()
        recall(two, two.mine, id).andExpect { status { isOk() } }
        val back = movements(two, two.mine, playerToken)["outgoing"].single()
        assertThat(back["direction"].asText()).isEqualTo("HOMEWARD")
        assertThat(back["departsAt"].asText()).isEqualTo(at.toString())
        assertThat(back["arrivesAt"].asText()).isEqualTo(at.plusSeconds(600).toString())

        jump(601)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(10) }
        }
        recall(two, two.mine, id).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_ARRIVED") }
        }
    }

    @Test
    fun `support standing in another city is recalled by its owner and sent away by its host`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 20)
        send(two, "SUPPORT", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }
        jump(5401)

        // The owner calls its troops home: the host city's id names the support.
        recall(two, two.mine, two.theirs).andExpect { status { isOk() } }
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs)).isEmpty()
        assertThat(movements(two, two.mine, playerToken)["outgoing"].single()["direction"].asText()).isEqualTo("HOMEWARD")
        jump(5401)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(20) }
        }

        // The host sends it away again, naming the owner's city.
        send(two, "SUPPORT", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs)).hasSize(1)
        recall(two, two.theirs, two.mine, otherPlayerToken).andExpect { status { isOk() } }
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs)).isEmpty()
        jump(5401)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(20) }
        }
    }

    @Test
    fun `an attack kills the defenders outright and costs the winner the computed fraction`() {
        val two = twoCities()
        give(two.mine, Unit.AXEMAN, 100)
        give(two.theirs, Unit.SPEARMAN, 50)
        send(two, "ATTACK", mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }

        jump(5401)
        get("/api/v1/worlds/${two.world}/cities/${two.theirs}", otherPlayerToken).andExpect {
            status { isOk() }
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(0) }
        }
        // 4000 against 750: the attacker loses (750/4000)^1.5 = 8% of its axemen.
        val home = movements(two, two.mine, playerToken)["outgoing"].single()
        assertThat(home["direction"].asText()).isEqualTo("HOMEWARD")
        assertThat(home["units"].single()["count"].asInt()).isEqualTo(92)

        jump(5401)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='AXEMAN')].home") { value(92) }
        }
    }

    @Test
    fun `an attack that loses dies to the last man and the defenders keep the field`() {
        val two = twoCities()
        give(two.mine, Unit.AXEMAN, 10)
        give(two.theirs, Unit.SPEARMAN, 50)
        send(two, "ATTACK", mapOf("AXEMAN" to 10)).andExpect { status { isOk() } }

        jump(5401)
        // 400 against 750: the defender loses (400/750)^1.5 = 38% of its spearmen, the attacker everything.
        get("/api/v1/worlds/${two.world}/cities/${two.theirs}", otherPlayerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].home") { value(31) }
        }
        assertThat(movements(two, two.mine, playerToken)["outgoing"]).isEmpty()
        assertThat(cityUnitRepository.findById(CityUnitId(two.mine, Unit.AXEMAN)).orElseThrow().count).isZero()
    }

    @Test
    fun `the wall stiffens the defence and support dies with the city it defends`() {
        val two = twoCities()
        val third = json(post("/api/v1/worlds/${two.world}/join", adminToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        place(third, two.x + 3, two.y + 4)  // five fields beyond the defender
        give(third, Unit.SPEARMAN, 10)
        post(
            "/api/v1/worlds/${two.world}/cities/$third/movements", adminToken,
            mapOf("kind" to "SUPPORT", "targetX" to two.x, "targetY" to two.y, "units" to mapOf("SPEARMAN" to 10)),
        ).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs)).hasSize(1)

        give(two.mine, Unit.AXEMAN, 100)
        give(two.theirs, Unit.SPEARMAN, 40)
        setLevel(two.theirs, Building.WALL, 10)
        send(two, "ATTACK", mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()

        // 50 spearmen behind a level-10 Wall defend with 1125: the attacker still wins, losing 14%.
        assertThat(citySupportRepository.findAllByIdHostCityId(two.theirs)).isEmpty()
        assertThat(cityUnitRepository.findById(CityUnitId(two.theirs, Unit.SPEARMAN)).orElseThrow().count).isZero()
        assertThat(movements(two, two.mine, playerToken)["outgoing"].single()["units"].single()["count"].asInt()).isEqualTo(86)
    }

    @Test
    fun `the dead give their population back to the city that raised them`() {
        val two = twoCities()
        // A third city lends ten spearmen, so all three sides have something to lose.
        val third = json(post("/api/v1/worlds/${two.world}/join", adminToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        place(third, two.x + 3, two.y + 4)
        give(third, Unit.SPEARMAN, 10)
        post(
            "/api/v1/worlds/${two.world}/cities/$third/movements", adminToken,
            mapOf("kind" to "SUPPORT", "targetX" to two.x, "targetY" to two.y, "units" to mapOf("SPEARMAN" to 10)),
        ).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()

        give(two.mine, Unit.AXEMAN, 100)     // 4000 attack
        give(two.theirs, Unit.SPEARMAN, 40)  // with the ten lent, 750 defence
        val before = mapOf(
            two.mine to population(two, two.mine, playerToken),
            two.theirs to population(two, two.theirs, otherPlayerToken),
            third to population(two, third, adminToken),
        )
        send(two, "ATTACK", mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()

        // The attacker wins and loses 8 of 100 axemen, 1 population each; both defending sides lose
        // everything, 40 spearmen for the city and the 10 it was lent.
        assertThat(population(two, two.mine, playerToken) - before.getValue(two.mine)).isEqualTo(8)
        assertThat(population(two, two.theirs, otherPlayerToken) - before.getValue(two.theirs)).isEqualTo(40)
        assertThat(population(two, third, adminToken) - before.getValue(third)).isEqualTo(10)
    }

    private fun population(two: Two, cityId: Long, token: String): Int =
        json(get("/api/v1/worlds/${two.world}/cities/$cityId", token).andExpect { status { isOk() } })["population"].asInt()

    @Test
    fun `plunder leaves what the vault hides and is capped again by the deposit at home`() {
        val two = twoCities()
        give(two.mine, Unit.AXEMAN, 100)
        setLevel(two.theirs, Building.VAULT, 7)   // hides 843 of each resource
        stock(two.theirs, 1000)                   // at capacity, so nothing is produced on top
        send(two, "ATTACK", mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }

        jump(5401)
        // An empty city costs the attacker nothing: 100 axemen carry 1000, but only 157 per resource is loose.
        val home = movements(two, two.mine, playerToken)["outgoing"].single()
        assertThat(home["units"].single()["count"].asInt()).isEqualTo(100)
        assertThat(home["carrying"]["wood"].asLong()).isEqualTo(157)
        assertThat(home["carrying"]["stone"].asLong()).isEqualTo(157)
        assertThat(home["carrying"]["silver"].asLong()).isEqualTo(157)
        assertThat(cityResourcesRepository.findById(two.theirs).orElseThrow().wood).isEqualTo(843)

        stock(two.mine, 1000)   // the Deposit is full: the plunder arrives and is lost
        jump(5401)
        get("/api/v1/worlds/${two.world}/cities/${two.mine}", playerToken).andExpect {
            jsonPath("$.resources.wood.stock") { value(1000) }
            jsonPath("$.units[?(@.type=='AXEMAN')].home") { value(100) }
        }
    }

    @Test
    fun `sending is refused without units, without enough of them, on itself and off the map`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 5)
        send(two, "ATTACK", mapOf("SPEARMAN" to 0)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NO_UNITS") }
        }
        send(two, "ATTACK", emptyMap()).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NO_UNITS") }
        }
        send(two, "ATTACK", mapOf("SPEARMAN" to 6)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_ENOUGH_UNITS") }
            jsonPath("$.details.SPEARMAN") { value("1") }
        }
        val (x, y) = coordinates(two.mine)
        send(two, "ATTACK", mapOf("SPEARMAN" to 5), x = x, y = y).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("SAME_CITY") }
        }
        clearField(two.world, x + 1, y + 40)
        send(two, "ATTACK", mapOf("SPEARMAN" to 5), x = x + 1, y = y + 40).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("CITY_NOT_FOUND") }
        }
        assertThat(cityUnitRepository.findById(CityUnitId(two.mine, Unit.SPEARMAN)).orElseThrow().count).isEqualTo(5)
    }

    @Test
    fun `a recall needs a movement of this city that is still on its way out`() {
        val two = twoCities()
        give(two.mine, Unit.SPEARMAN, 10)
        recall(two, two.mine, 999_999).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("MOVEMENT_NOT_FOUND") }
        }
        send(two, "ATTACK", mapOf("SPEARMAN" to 10)).andExpect { status { isOk() } }
        val id = movementRepository.findAllByOriginCityIdAndAppliedFalseOrderByArrivesAtAscIdAsc(two.mine).single().id!!
        // The target may not touch someone else's movement.
        recall(two, two.theirs, id, otherPlayerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("MOVEMENT_NOT_FOUND") }
        }
        recall(two, two.mine, id).andExpect { status { isOk() } }
        recall(two, two.mine, id).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_ARRIVED") }
        }
    }
}
