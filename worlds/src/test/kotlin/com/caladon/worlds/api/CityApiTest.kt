package com.caladon.worlds.api

import com.caladon.worlds.buildings.CityBuilding
import com.caladon.worlds.buildings.CityBuildingId
import com.caladon.worlds.buildings.CityBuildingRepository
import com.caladon.worlds.resources.CitySweeper
import com.caladon.worlds.army.CityUnitRepository
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.Unit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.delete
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(MutableClockConfig::class)
class CityApiTest : ApiTestBase() {

    @Autowired lateinit var cityBuildingRepository: CityBuildingRepository
    @Autowired lateinit var sweeper: CitySweeper
    @Autowired lateinit var cityUnitRepository: CityUnitRepository

    private fun joinedCity(): Pair<Long, Long> {
        val world = createPlayableWorld()
        val cityId = json(post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        return world to cityId
    }

    private fun setLevel(cityId: Long, b: Building, level: Int) =
        cityBuildingRepository.save(CityBuilding(CityBuildingId(cityId, b), level))

    @Test
    fun `a founded city has the six founded buildings at level 1, 39 points and an empty queue`() {
        val (world, cityId) = joinedCity()
        assertThat(cityBuildingRepository.findAllByIdCityId(cityId).map { it.id.building to it.level })
            .containsExactlyInAnyOrderElementsOf(Building.FOUNDED.map { it to 1 })
        assertThat(cityRepository.findById(cityId).orElseThrow().points).isEqualTo(39)

        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.points") { value(39) }
            jsonPath("$.buildings.length()") { value(Building.entries.size) }
            jsonPath("$.buildings[?(@.type=='FARM')].level") { value(1) }
            jsonPath("$.buildings[?(@.type=='BARRACKS')].level") { value(0) }
            jsonPath("$.buildQueue.length()") { value(0) }
            jsonPath("$.buildQueueSlots") { value(2) }
        }
        get("/api/v1/worlds/$world/cities/$cityId/buildings", playerToken).andExpect {
            status { isOk() }
            jsonPath("$[?(@.type=='WOODCUTTER')].level") { value(1) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.level") { value(2) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.cost.wood") { value(63) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.cost.stone") { value(77) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.cost.iron") { value(50) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.popCost") { value(1) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.effect.value") { value(35) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.buildTimeSeconds") { value(95) }
            jsonPath("$[?(@.type=='WOODCUTTER')].next.blockedBy.length()") { value(0) }
            jsonPath("$[?(@.type=='BARRACKS')].next.blockedBy[0].building") { value("TOWN_HALL") }
            jsonPath("$[?(@.type=='BARRACKS')].next.blockedBy[0].level") { value(3) }
        }
    }

    @Test
    fun `upgrade pays at order time, queues, and completes after its build time with the effect from then on`() {
        val (world, cityId) = joinedCity()
        post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.resources.wood.stock") { value(437) }
            jsonPath("$.resources.stone.stock") { value(423) }
            jsonPath("$.resources.iron.stock") { value(450) }
            jsonPath("$.population") { value(239) }
            jsonPath("$.points") { value(39) }
            jsonPath("$.buildQueue.length()") { value(1) }
            jsonPath("$.buildQueue[0].building") { value("WOODCUTTER") }
            jsonPath("$.buildQueue[0].targetLevel") { value(2) }
            jsonPath("$.buildQueue[0].completesAt") { value(clock.instant().plusSeconds(95).toString()) }
            // The order carries what it was paid; cancelling gives back half of it.
            jsonPath("$.buildQueue[0].cost.wood") { value(63) }
            jsonPath("$.buildQueue[0].cost.stone") { value(77) }
            jsonPath("$.buildQueue[0].cost.iron") { value(50) }
            jsonPath("$.buildQueue[0].popCost") { value(1) }
            jsonPath("$.buildings[?(@.type=='WOODCUTTER')].level") { value(1) }
            jsonPath("$.resources.wood.ratePerHour") { value(30) }
        }

        clock.advance(Duration.ofSeconds(94))
        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            jsonPath("$.buildings[?(@.type=='WOODCUTTER')].level") { value(1) }
            jsonPath("$.buildQueue.length()") { value(1) }
        }
        clock.advance(Duration.ofSeconds(2))
        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            jsonPath("$.buildings[?(@.type=='WOODCUTTER')].level") { value(2) }
            jsonPath("$.buildings[?(@.type=='WOODCUTTER')].points") { value(7) }
            jsonPath("$.points") { value(40) }
            jsonPath("$.resources.wood.ratePerHour") { value(35) }
            jsonPath("$.buildQueue.length()") { value(0) }
        }
        assertThat(cityRepository.findById(cityId).orElseThrow().points).isEqualTo(40)
    }

    @Test
    fun `farm completion credits population and deposit completion raises capacity`() {
        val (world, cityId) = joinedCity()
        post("/api/v1/worlds/$world/cities/$cityId/buildings/FARM/upgrade", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.population") { value(240) } // the Farm's level 2 costs no population
        }
        post("/api/v1/worlds/$world/cities/$cityId/buildings/DEPOSIT/upgrade", playerToken).andExpect { status { isOk() } }
        clock.advance(Duration.ofMinutes(10))
        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            jsonPath("$.buildings[?(@.type=='FARM')].level") { value(2) }
            jsonPath("$.population") { value(281) }
            jsonPath("$.buildings[?(@.type=='DEPOSIT')].level") { value(2) }
            jsonPath("$.resources.capacity") { value(1229) }
            jsonPath("$.buildQueue.length()") { value(0) }
        }
    }

    @Test
    fun `orders are rejected by the step rule, cross-building conditions, the queue size and max level`() {
        val (world, cityId) = joinedCity()
        post("/api/v1/worlds/$world/cities/$cityId/buildings/BARRACKS/upgrade", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("REQUIREMENTS_NOT_MET") }
            jsonPath("$.details.TOWN_HALL") { value("3") }
        }
        setLevel(cityId, Building.FARM, 5)
        post("/api/v1/worlds/$world/cities/$cityId/buildings/FARM/upgrade", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("REQUIREMENTS_NOT_MET") }
            jsonPath("$.details.TOWN_HALL") { value("5") }
        }
        setLevel(cityId, Building.FARM, 30)
        post("/api/v1/worlds/$world/cities/$cityId/buildings/FARM/upgrade", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("MAX_LEVEL") }
        }
        post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect { status { isOk() } }
        post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.buildQueue[1].targetLevel") { value(3) }
        }
        post("/api/v1/worlds/$world/cities/$cityId/buildings/STONE_MINE/upgrade", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("QUEUE_FULL") }
        }
        post("/api/v1/worlds/$world/cities/$cityId/buildings/NOPE/upgrade", playerToken).andExpect { status { isNotFound() } }
    }

    @Test
    fun `orders are rejected when resources or population are short, with the shortfall`() {
        val (world, cityId) = joinedCity()
        val row = cityResourcesRepository.findById(cityId).orElseThrow()
        row.wood = 10; cityResourcesRepository.save(row)
        post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_ENOUGH_RESOURCES") }
            jsonPath("$.details.wood") { value("53") }
        }
        row.wood = 500; row.population = 0; cityResourcesRepository.save(row)
        post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_ENOUGH_POPULATION") }
            jsonPath("$.details.population") { value("1") }
        }
        assertThat(cityResourcesRepository.findById(cityId).orElseThrow().wood).isEqualTo(500)
    }

    @Test
    fun `only the last build order can be cancelled, giving back half the resources and all the population`() {
        val (world, cityId) = joinedCity()
        val first = json(post("/api/v1/worlds/$world/cities/$cityId/buildings/FARM/upgrade", playerToken).andExpect { status { isOk() } })
        val farmOrder = first["buildQueue"][0]["id"].asLong()
        val second = json(post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect { status { isOk() } })
        val woodOrder = second["buildQueue"][1]["id"].asLong()
        assertThat(second["buildQueue"][1]["startedAt"].asText()).isEqualTo(second["buildQueue"][0]["completesAt"].asText())
        assertThat(second["resources"]["wood"]["stock"].asLong()).isEqualTo(500 - 59 - 63)

        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/build-orders/$farmOrder") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_LAST_IN_QUEUE") }
        }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/build-orders/$woodOrder") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isOk() }
            // Half of the Woodcutter level's 63 wood and 77 stone, floored; its 1 population in full.
            jsonPath("$.resources.wood.stock") { value(500 - 59 - 63 + 31) }
            jsonPath("$.resources.stone.stock") { value(500 - 53 - 77 + 38) }
            jsonPath("$.population") { value(240) }
            jsonPath("$.buildQueue.length()") { value(1) }
            jsonPath("$.buildQueue[0].building") { value("FARM") }
        }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/build-orders/$farmOrder") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect { status { isOk() }; jsonPath("$.buildQueue.length()") { value(0) } }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/build-orders/$farmOrder") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("ORDER_NOT_FOUND") }
        }
    }

    @Test
    fun `the sweeper applies due orders without any read of the city`() {
        val (world, cityId) = joinedCity()
        post("/api/v1/worlds/$world/cities/$cityId/buildings/WOODCUTTER/upgrade", playerToken).andExpect { status { isOk() } }
        assertThat(sweeper.sweep()).isEqualTo(0)

        clock.advance(Duration.ofSeconds(100))
        assertThat(sweeper.sweep()).isEqualTo(1)
        assertThat(cityBuildingRepository.findById(CityBuildingId(cityId, Building.WOODCUTTER)).orElseThrow().level).isEqualTo(2)
        assertThat(cityRepository.findById(cityId).orElseThrow().points).isEqualTo(40)
        assertThat(sweeper.sweep()).isEqualTo(0)
    }

    // ---- army ----

    private fun stock(cityId: Long, amount: Long) {
        val row = cityResourcesRepository.findById(cityId).orElseThrow()
        row.wood = amount; row.stone = amount; row.iron = amount; cityResourcesRepository.save(row)
    }

    @Test
    fun `recruiting pays at order time and completes one unit at a time from the head order`() {
        val (world, cityId) = joinedCity()
        setLevel(cityId, Building.BARRACKS, 1)
        val t0 = clock.instant()
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SPEARMAN", "count" to 3)).andExpect {
            status { isOk() }
            jsonPath("$.resources.wood.stock") { value(350) }
            jsonPath("$.resources.stone.stock") { value(410) }
            jsonPath("$.resources.iron.stock") { value(470) }
            jsonPath("$.population") { value(237) }
            jsonPath("$.recruitQueue.length()") { value(1) }
            jsonPath("$.recruitQueue[0].unit") { value("SPEARMAN") }
            jsonPath("$.recruitQueue[0].remaining") { value(3) }
            jsonPath("$.recruitQueue[0].nextCompletesAt") { value(t0.plusSeconds(642).toString()) }
            jsonPath("$.recruitQueue[0].completesAt") { value(t0.plusSeconds(642 * 3).toString()) }
            jsonPath("$.units[?(@.type=='SPEARMAN')].count") { value(0) }
        }
        // A second order waits behind the first: no next-completion yet, but an estimate.
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SPEARMAN", "count" to 1)).andExpect {
            status { isOk() }
            jsonPath("$.recruitQueue[1].nextCompletesAt") { value(null) }
            jsonPath("$.recruitQueue[1].completesAt") { value(t0.plusSeconds(642 * 4).toString()) }
        }

        clock.advance(Duration.ofSeconds(643))
        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].count") { value(1) }
            jsonPath("$.recruitQueue[0].remaining") { value(2) }
            jsonPath("$.recruitQueue[0].nextCompletesAt") { value(t0.plusSeconds(642 * 2).toString()) }
        }
        clock.advance(Duration.ofSeconds(642 * 3))
        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='SPEARMAN')].count") { value(4) }
            jsonPath("$.recruitQueue.length()") { value(0) }
        }
        assertThat(cityUnitRepository.findAllByIdCityId(cityId).single().count).isEqualTo(4)
    }

    @Test
    fun `recruiting is gated by the barracks level and by study, study by the academy level`() {
        val (world, cityId) = joinedCity()
        setLevel(cityId, Building.BARRACKS, 1)
        stock(cityId, 3000)
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 1)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("REQUIREMENTS_NOT_MET") }
            jsonPath("$.details.BARRACKS") { value("3") }
        }
        setLevel(cityId, Building.BARRACKS, 3)
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 1)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_STUDIED") }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SWORDSMAN")).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("REQUIREMENTS_NOT_MET") }
            jsonPath("$.details.ACADEMY") { value("1") }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SPEARMAN")).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_STUDIED") }
        }
        setLevel(cityId, Building.ACADEMY, 1)
        val t0 = clock.instant()
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SWORDSMAN")).andExpect {
            status { isOk() }
            jsonPath("$.resources.wood.stock") { value(2600) }
            jsonPath("$.resources.stone.stock") { value(2500) }
            jsonPath("$.resources.iron.stock") { value(2700) }
            jsonPath("$.population") { value(240) }
            jsonPath("$.studyQueue[0].unit") { value("SWORDSMAN") }
            jsonPath("$.studyQueue[0].position") { value(1) }
            jsonPath("$.studyQueue[0].completesAt") { value(t0.plusSeconds(2727).toString()) }
            jsonPath("$.studied.length()") { value(0) }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SWORDSMAN")).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_STUDIED") }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 1)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_STUDIED") }
        }
        get("/api/v1/worlds/$world/cities/$cityId/army", playerToken).andExpect {
            status { isOk() }
            jsonPath("$[?(@.type=='SPEARMAN')].recruitable") { value(true) }
            jsonPath("$[?(@.type=='SWORDSMAN')].recruitable") { value(false) }
            jsonPath("$[?(@.type=='SWORDSMAN')].studyCompletesAt") { value(t0.plusSeconds(2727).toString()) }
            jsonPath("$[?(@.type=='AXEMAN')].blockedBy[0].building") { value("BARRACKS") }
            jsonPath("$[?(@.type=='AXEMAN')].studyBlockedBy[0].level") { value(3) }
            jsonPath("$[?(@.type=='SPEARMAN')].recruitSeconds") { value(571) } // Barracks 3
        }

        clock.advance(Duration.ofSeconds(2728))
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 2)).andExpect {
            status { isOk() }
            jsonPath("$.studied[0]") { value("SWORDSMAN") }
            jsonPath("$.studyQueue.length()") { value(0) }
            jsonPath("$.recruitQueue[0].unit") { value("SWORDSMAN") }
            jsonPath("$.population") { value(238) }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 0)).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `cancelling a recruit order gives back half the untrained resources and all their population, only from the tail`() {
        val (world, cityId) = joinedCity()
        setLevel(cityId, Building.BARRACKS, 1)
        val orderId = json(post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SPEARMAN", "count" to 3))
            .andExpect { status { isOk() } })["recruitQueue"][0]["id"].asLong()
        val second = json(post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SPEARMAN", "count" to 1))
            .andExpect { status { isOk() } })["recruitQueue"][1]["id"].asLong()

        clock.advance(Duration.ofSeconds(643))
        assertThat(sweeper.sweep()).isEqualTo(1)                    // first unit completed without a read
        assertThat(cityUnitRepository.findAllByIdCityId(cityId).single().count).isEqualTo(1)

        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/recruit-orders/$orderId") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_LAST_IN_QUEUE") }
        }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/recruit-orders/$second") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect { status { isOk() }; jsonPath("$.recruitQueue.length()") { value(1) } }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/recruit-orders/$orderId") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.units[?(@.type=='SPEARMAN')].count") { value(1) }
            jsonPath("$.recruitQueue.length()") { value(0) }
            // 4 paid, 3 untrained refunded at half, +5 produced in 643 s
            jsonPath("$.resources.wood.stock") { value(500 - 200 + 75 + 5) }
            jsonPath("$.resources.iron.stock") { value(500 - 40 + 15 + 5) }
            jsonPath("$.population") { value(239) }
        }
    }

    @Test
    fun `studies queue one after another, only the tail can be cancelled, and completion unlocks recruitment`() {
        val (world, cityId) = joinedCity()
        setLevel(cityId, Building.BARRACKS, 5)
        setLevel(cityId, Building.ACADEMY, 10)   // 2 study slots, so two studies may wait
        setLevel(cityId, Building.DEPOSIT, 10)   // cap 6420, so a 5000 stock survives settlement
        stock(cityId, 5000)
        val t0 = clock.instant()
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SWORDSMAN")).andExpect { status { isOk() } }
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SCOUT")).andExpect {
            status { isOk() }
            jsonPath("$.studyQueue.length()") { value(2) }
            jsonPath("$.studyQueue[0].unit") { value("SWORDSMAN") }
            jsonPath("$.studyQueue[0].completesAt") { value(t0.plusSeconds(1157).toString()) }   // 2 × 1500 × 1.1^-10
            jsonPath("$.studyQueue[1].unit") { value("SCOUT") }
            jsonPath("$.studyQueue[1].position") { value(2) }
            jsonPath("$.studyQueue[1].completesAt") { value(t0.plusSeconds(1157 + 694).toString()) } // starts after the Swordsman
            jsonPath("$.resources.wood.stock") { value(5000 - 400 - 560) }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SCOUT")).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_STUDIED") }
        }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/study-orders/SWORDSMAN") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_LAST_IN_QUEUE") }
        }
        mockMvc.delete("/api/v1/worlds/$world/cities/$cityId/study-orders/SCOUT") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.studyQueue.length()") { value(1) }
            jsonPath("$.resources.wood.stock") { value(5000 - 400 - 560 + 280) }   // half the Scout's cost back
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "AXEMAN")).andExpect {
            status { isOk() }
            jsonPath("$.studyQueue[1].completesAt") { value(t0.plusSeconds(1157 + 1018).toString()) }   // 2 × 1320 × 1.1^-10
        }

        clock.advance(Duration.ofSeconds(1158))
        get("/api/v1/worlds/$world/cities/$cityId", playerToken).andExpect {
            jsonPath("$.studied[0]") { value("SWORDSMAN") }
            jsonPath("$.studyQueue.length()") { value(1) }
            jsonPath("$.studyQueue[0].unit") { value("AXEMAN") }
        }
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 1)).andExpect { status { isOk() } }
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "AXEMAN", "count" to 1)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_STUDIED") }
        }
    }

    @Test
    fun `the sweeper applies a due study`() {
        val (world, cityId) = joinedCity()
        setLevel(cityId, Building.BARRACKS, 3)
        setLevel(cityId, Building.ACADEMY, 1)
        setLevel(cityId, Building.DEPOSIT, 10)
        stock(cityId, 3000)
        post("/api/v1/worlds/$world/cities/$cityId/army/study", playerToken, mapOf("unit" to "SWORDSMAN")).andExpect { status { isOk() } }
        assertThat(sweeper.sweep()).isEqualTo(0)
        clock.advance(Duration.ofSeconds(2728))
        assertThat(sweeper.sweep()).isEqualTo(1)
        assertThat(sweeper.sweep()).isEqualTo(0)   // applied: not selected again
        post("/api/v1/worlds/$world/cities/$cityId/army/recruit", playerToken, mapOf("unit" to "SWORDSMAN", "count" to 1)).andExpect { status { isOk() } }
    }
}
