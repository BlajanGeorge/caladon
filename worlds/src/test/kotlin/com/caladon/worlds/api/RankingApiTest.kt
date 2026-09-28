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
    @Autowired lateinit var rankingService: com.caladon.worlds.ranking.RankingService

    /**
     * Moves a city onto an exact field. Whatever slot is already there is shifted aside rather than
     * deleted: the generated map may have put another player's city on it, and deleting that slot breaks
     * the city pointing at it — which it did, now and then, depending on where the world put people.
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
            jsonPath("$.total") { value(2) }
            jsonPath("$.rows.length()") { value(2) }
            // Both founded a city worth the same 39 points, so the tie breaks on the player id.
            jsonPath("$.rows[0].rank") { value(1) }
            jsonPath("$.rows[0].player") { value("george") }
            jsonPath("$.rows[0].cities") { value(1) }
            jsonPath("$.rows[0].points") { value(39) }
            jsonPath("$.rows[0].attackPoints") { value(0) }
            jsonPath("$.rows[0].defencePoints") { value(0) }
            jsonPath("$.rows[0].battlePoints") { value(0) }
            jsonPath("$.rows[1].player") { value("ana") }
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

        val standings = json(get("/api/v1/worlds/$world/ranking", playerToken).andExpect { status { isOk() } })["rows"]
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
    fun `boards, paging and search`() {
        val world = createPlayableWorld()
        val mine = json(post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        post("/api/v1/worlds/$world/join", otherPlayerToken).andExpect { status { isOk() } }
        post("/api/v1/worlds/$world/join", adminToken).andExpect { status { isOk() } }

        // Points come from the cities, so raising one moves the board at once.
        val city = cityRepository.findById(mine).orElseThrow()
        city.points = 500
        cityRepository.saveAndFlush(city)
        // Battle points are their own board, and george has none.
        rankingService.award(world, userId("ana@caladon.test"), attack = 120)
        rankingService.award(world, userId("admin@caladon.test"), defence = 300)

        get("/api/v1/worlds/$world/ranking", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.board") { value("points") }
            jsonPath("$.total") { value(3) }
            jsonPath("$.limit") { value(100) }
            jsonPath("$.next") { doesNotExist() }
            jsonPath("$.rows[0].player") { value("george") }     // 500 points
            jsonPath("$.rows[0].rank") { value(1) }
            jsonPath("$.me.player") { value("george") }
            jsonPath("$.me.rank") { value(1) }
        }
        get("/api/v1/worlds/$world/ranking?board=battle", playerToken).andExpect {
            jsonPath("$.board") { value("battle") }
            jsonPath("$.rows[0].player") { value("admin") }      // 300 defending
            jsonPath("$.rows[1].player") { value("ana") }        // 120 attacking
            jsonPath("$.rows[2].player") { value("george") }
            jsonPath("$.me.rank") { value(3) }
        }
        get("/api/v1/worlds/$world/ranking?board=attack", playerToken).andExpect {
            jsonPath("$.rows[0].player") { value("ana") }
        }
        get("/api/v1/worlds/$world/ranking?board=defence", playerToken).andExpect {
            jsonPath("$.rows[0].player") { value("admin") }
        }
        get("/api/v1/worlds/$world/ranking?board=nonsense", playerToken).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("VALIDATION_ERROR") }
        }

        // A scroll of one row at a time walks the whole board, losing nobody and repeating nobody.
        val seen = mutableListOf<String>()
        var next: String? = null
        repeat(4) {
            val url = "/api/v1/worlds/$world/ranking?limit=1" + (next?.let { "&after=$it" } ?: "")
            val body = json(get(url, playerToken).andExpect { status { isOk() } })
            body["rows"].forEach { seen += it["player"].asText() }
            next = body["next"].takeIf { !it.isNull }?.asText()
        }
        assertThat(seen).containsExactly("george", "admin", "ana")
        assertThat(next).isNull()

        // Page numbers walk it too.
        assertThat(json(get("/api/v1/worlds/$world/ranking?limit=2&page=2", playerToken)
            .andExpect { status { isOk() } })["rows"].map { it["player"].asText() }).containsExactly("ana")

        // Searching keeps the rank the player holds in the whole world.
        get("/api/v1/worlds/$world/ranking?q=AN", playerToken).andExpect {
            jsonPath("$.total") { value(1) }
            jsonPath("$.rows.length()") { value(1) }
            jsonPath("$.rows[0].player") { value("ana") }
            jsonPath("$.rows[0].rank") { value(3) }
        }
        // A typo still finds her: trigram similarity, not just a substring.
        get("/api/v1/worlds/$world/ranking?q=ann", playerToken).andExpect {
            jsonPath("$.rows.length()") { value(1) }
            jsonPath("$.rows[0].player") { value("ana") }
        }
        // Something nothing resembles finds nobody.
        get("/api/v1/worlds/$world/ranking?q=zzzz", playerToken).andExpect {
            jsonPath("$.total") { value(0) }
            jsonPath("$.rows.length()") { value(0) }
        }
    }

    private fun userId(email: String): Long = requireNotNull(userRepository.findByEmailIgnoreCase(email)).id!!

    @Test
    fun `the ranking of a world the player has not joined is refused`() {
        val world = createPlayableWorld()
        get("/api/v1/worlds/$world/ranking", playerToken).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("NOT_JOINED") }
        }
    }
}
