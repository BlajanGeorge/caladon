package com.caladon.worlds.api

import com.caladon.worlds.army.CityUnit
import com.caladon.worlds.army.CityUnitId
import com.caladon.worlds.army.CityUnitRepository
import com.caladon.worlds.resources.CitySweeper
import com.caladon.worlds.rules.Unit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(MutableClockConfig::class)
class RankingApiTest : ApiTestBase() {

    @Autowired lateinit var cityUnitRepository: CityUnitRepository
    @Autowired lateinit var sweeper: CitySweeper

    private fun place(cityId: Long, x: Int, y: Int) {
        val city = cityRepository.findById(cityId).orElseThrow()
        citySlotRepository.findByWorldIdAndXAndY(city.worldId, x.toShort(), y.toShort())
            ?.takeIf { it.id != city.slotId }
            ?.let { citySlotRepository.delete(it) }
        val slot = citySlotRepository.findById(city.slotId).orElseThrow()
        slot.x = x.toShort()
        slot.y = y.toShort()
        citySlotRepository.saveAndFlush(slot)
    }

    private fun coordinates(cityId: Long): Pair<Int, Int> {
        val slot = citySlotRepository.findById(cityRepository.findById(cityId).orElseThrow().slotId).orElseThrow()
        return slot.x.toInt() to slot.y.toInt()
    }

    private fun jump(seconds: Long) {
        clock.advance(Duration.ofSeconds(seconds))
        adminToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("admin@caladon.test")))
        playerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("george@caladon.test")))
        otherPlayerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("ana@caladon.test")))
    }

    @Test
    fun `a new world ranks every player by the points of the cities they hold`() {
        val world = createPlayableWorld()
        post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } }
        post("/api/v1/worlds/$world/join", otherPlayerToken).andExpect { status { isOk() } }

        get("/api/v1/worlds/$world/ranking", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            // Both founded a city worth the same 39 points, so the tie breaks on the name.
            jsonPath("$[0].rank") { value(1) }
            jsonPath("$[0].player") { value("ana") }
            jsonPath("$[0].cities") { value(1) }
            jsonPath("$[0].points") { value(39) }
            jsonPath("$[0].attackPoints") { value(0) }
            jsonPath("$[0].defencePoints") { value(0) }
            jsonPath("$[0].battlePoints") { value(0) }
            jsonPath("$[1].player") { value("george") }
        }
    }

    @Test
    fun `battle points are the population each side killed, the defence shared with its supporters`() {
        val world = createPlayableWorld()
        val mine = json(post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val theirs = json(post("/api/v1/worlds/$world/join", otherPlayerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val ally = json(post("/api/v1/worlds/$world/join", adminToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val (x, y) = coordinates(mine)
        place(theirs, x + 3, y + 4)
        place(ally, x + 6, y + 8)

        // The ally lends ten spearmen to the city that is about to be attacked.
        cityUnitRepository.save(CityUnit(CityUnitId(ally, Unit.SPEARMAN), 10))
        post(
            "/api/v1/worlds/$world/cities/$ally/movements", adminToken,
            mapOf("kind" to "SUPPORT", "targetX" to x + 3, "targetY" to y + 4, "units" to mapOf("SPEARMAN" to 10)),
        ).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()

        cityUnitRepository.save(CityUnit(CityUnitId(mine, Unit.AXEMAN), 100))      // 4000 attack
        cityUnitRepository.save(CityUnit(CityUnitId(theirs, Unit.SPEARMAN), 40))   // 750 defence with the support
        post(
            "/api/v1/worlds/$world/cities/$mine/movements", playerToken,
            mapOf("kind" to "ATTACK", "targetX" to x + 3, "targetY" to y + 4, "units" to mapOf("AXEMAN" to 100)),
        ).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()

        val standings = json(get("/api/v1/worlds/$world/ranking", playerToken).andExpect { status { isOk() } })
        val by = standings.associateBy { it["player"].asText() }

        // The attacker killed 50 spearmen, one population each, and lost 8 axemen.
        assertThat(by.getValue("george")["attackPoints"].asLong()).isEqualTo(50)
        assertThat(by.getValue("george")["defencePoints"].asLong()).isZero()
        // The defence is shared by what each held: 40 of the 50 spearmen were the city's, 10 the ally's.
        assertThat(by.getValue("ana")["defencePoints"].asLong()).isEqualTo(7)
        assertThat(by.getValue("admin")["defencePoints"].asLong()).isEqualTo(1)
        assertThat(by.values.sumOf { it["defencePoints"].asLong() }).isEqualTo(8)
        assertThat(by.getValue("george")["battlePoints"].asLong()).isEqualTo(50)
    }

    @Test
    fun `the ranking of a world the player has not joined is refused`() {
        val world = createPlayableWorld()
        get("/api/v1/worlds/$world/ranking", playerToken).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("NOT_JOINED") }
        }
    }
}
