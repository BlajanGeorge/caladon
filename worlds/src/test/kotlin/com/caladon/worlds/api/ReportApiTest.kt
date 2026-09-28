package com.caladon.worlds.api

import com.caladon.worlds.army.CityUnit
import com.caladon.worlds.army.CityUnitId
import com.caladon.worlds.army.CityUnitRepository
import com.caladon.worlds.buildings.CityBuilding
import com.caladon.worlds.buildings.CityBuildingId
import com.caladon.worlds.buildings.CityBuildingRepository
import com.caladon.worlds.report.ReportService
import com.caladon.worlds.resources.CitySweeper
import com.caladon.worlds.rules.Building
import com.caladon.worlds.rules.Unit
import com.fasterxml.jackson.databind.JsonNode
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
class ReportApiTest : ApiTestBase() {

    @Autowired lateinit var cityUnitRepository: CityUnitRepository
    @Autowired lateinit var cityBuildingRepository: CityBuildingRepository
    @Autowired lateinit var reportService: ReportService
    @Autowired lateinit var sweeper: CitySweeper

    /** Three cities in a line, five fields apart: george attacks ana, admin supports her from beyond. */
    private data class Three(val world: Long, val mine: Long, val theirs: Long, val ally: Long, val x: Int, val y: Int)

    private fun threeCities(): Three {
        val world = createPlayableWorld()
        val mine = join(world, playerToken)
        val theirs = join(world, otherPlayerToken)
        val ally = join(world, adminToken)
        val (x, y) = coordinates(mine)
        place(theirs, x + 3, y + 4)
        place(ally, x + 6, y + 8)
        return Three(world, mine, theirs, ally, x + 3, y + 4)
    }

    private fun join(world: Long, token: String): Long =
        json(post("/api/v1/worlds/$world/join", token).andExpect { status { isOk() } })["startCity"]["id"].asLong()

    private fun coordinates(cityId: Long): Pair<Int, Int> {
        val slot = citySlotRepository.findById(cityRepository.findById(cityId).orElseThrow().slotId).orElseThrow()
        return slot.x.toInt() to slot.y.toInt()
    }

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

    private fun jump(seconds: Long) {
        clock.advance(Duration.ofSeconds(seconds))
        adminToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("admin@caladon.test")))
        playerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("george@caladon.test")))
        otherPlayerToken = tokenFor(requireNotNull(userRepository.findByEmailIgnoreCase("ana@caladon.test")))
        sweeper.sweep()
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

    private fun caveSilver(cityId: Long, amount: Long) {
        val row = cityResourcesRepository.findById(cityId).orElseThrow()
        row.caveSilver = amount
        cityResourcesRepository.save(row)
    }

    private fun send(three: Three, from: Long, token: String, units: Map<String, Int>, x: Int = three.x, y: Int = three.y) =
        post(
            "/api/v1/worlds/${three.world}/cities/$from/movements", token,
            mapOf("kind" to "ATTACK", "targetX" to x, "targetY" to y, "units" to units),
        )

    private fun spy(three: Three, silver: Long, from: Long = three.mine, token: String = playerToken, x: Int = three.x, y: Int = three.y): ResultActionsDsl =
        post(
            "/api/v1/worlds/${three.world}/cities/$from/spy", token,
            mapOf("targetX" to x, "targetY" to y, "silver" to silver),
        )

    private fun reports(world: Long, token: String, query: String = ""): JsonNode =
        json(get("/api/v1/worlds/$world/reports$query", token).andExpect { status { isOk() } })

    private fun report(world: Long, token: String, id: Long): JsonNode =
        json(get("/api/v1/worlds/$world/reports/$id", token).andExpect { status { isOk() } })

    /** The battle everything below is read from: 100 axemen against 40 spearmen and 10 lent by the ally. */
    private fun oneBattle(): Three {
        val three = threeCities()
        give(three.ally, Unit.SPEARMAN, 10)
        post(
            "/api/v1/worlds/${three.world}/cities/${three.ally}/movements", adminToken,
            mapOf("kind" to "SUPPORT", "targetX" to three.x, "targetY" to three.y, "units" to mapOf("SPEARMAN" to 10)),
        ).andExpect { status { isOk() } }
        jump(5401)

        give(three.mine, Unit.AXEMAN, 100)
        give(three.theirs, Unit.SPEARMAN, 40)
        stock(three.theirs, 1000)
        send(three, three.mine, playerToken, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        return three
    }

    @Test
    fun `a won attack is reported to the attacker, the defender and the supporter`() {
        val three = oneBattle()

        val mine = reports(three.world, playerToken)
        assertThat(mine["unread"].asLong()).isEqualTo(1)
        assertThat(mine["total"].asLong()).isEqualTo(1)
        assertThat(mine["limit"].asInt()).isEqualTo(50)
        assertThat(mine["page"].asInt()).isEqualTo(1)
        val row = mine["rows"].single()
        assertThat(row["kind"].asText()).isEqualTo("BATTLE")
        assertThat(row["subjectCity"].asText()).isEqualTo("george's city")
        assertThat(row["otherCity"].asText()).isEqualTo("ana's city")
        assertThat(row["otherPlayer"].asText()).isEqualTo("ana")
        assertThat(row["won"].asBoolean()).isTrue()
        assertThat(row["summary"].asText()).isEqualTo("Attack on ana's city")
        assertThat(row["read"].asBoolean()).isFalse()

        // The attacker won, so it sees everything: its own 8 dead, the whole defence, the loot, the Wall.
        val attacker = report(three.world, playerToken, row["id"].asLong())["payload"]
        assertThat(attacker["role"].asText()).isEqualTo("ATTACKER")
        assertThat(attacker["attacker"]["player"].asText()).isEqualTo("george")
        assertThat(attacker["attacker"]["city"].asText()).isEqualTo("george's city")
        val axemen = attacker["attacker"]["units"].single()
        assertThat(axemen["type"].asText()).isEqualTo("AXEMAN")
        assertThat(axemen["name"].asText()).isEqualTo("Axeman")
        assertThat(axemen["sent"].asInt()).isEqualTo(100)
        assertThat(axemen["lost"].asInt()).isEqualTo(8)
        assertThat(axemen["left"].asInt()).isEqualTo(92)
        // The defending side is one army: the city's forty spearmen and the ten it was lent.
        val spearmen = attacker["defender"]["units"].single()
        assertThat(attacker["defender"]["player"].asText()).isEqualTo("ana")
        assertThat(spearmen["sent"].asInt()).isEqualTo(50)
        assertThat(spearmen["lost"].asInt()).isEqualTo(50)
        assertThat(spearmen["left"].asInt()).isZero()
        // 92 axemen carry 920, filled evenly across the three resources.
        assertThat(attacker["plunder"]["wood"].asLong()).isEqualTo(307)
        assertThat(attacker["plunder"]["stone"].asLong()).isEqualTo(307)
        assertThat(attacker["plunder"]["silver"].asLong()).isEqualTo(306)
        assertThat(attacker["wall"]["before"].asInt()).isZero()
        assertThat(attacker["wall"]["after"].asInt()).isZero()

        val theirRow = reports(three.world, otherPlayerToken)["rows"].single()
        assertThat(theirRow["subjectCity"].asText()).isEqualTo("ana's city")
        assertThat(theirRow["otherPlayer"].asText()).isEqualTo("george")
        assertThat(theirRow["won"].asBoolean()).isFalse()
        assertThat(theirRow["summary"].asText()).isEqualTo("Attack from george's city")
        val defender = report(three.world, otherPlayerToken, theirRow["id"].asLong())["payload"]
        assertThat(defender["role"].asText()).isEqualTo("DEFENDER")
        assertThat(defender["attacker"]["units"].single()["lost"].asInt()).isEqualTo(8)
        assertThat(defender["defender"]["units"].single()["sent"].asInt()).isEqualTo(50)

        // The supporter's troops were in the fight, so it gets the same report from where it stood.
        val allyRow = reports(three.world, adminToken)["rows"].single()
        assertThat(allyRow["subjectCity"].asText()).isEqualTo("admin's city")
        assertThat(allyRow["otherCity"].asText()).isEqualTo("ana's city")
        assertThat(allyRow["summary"].asText()).isEqualTo("Battle at ana's city")
        val supporter = report(three.world, adminToken, allyRow["id"].asLong())["payload"]
        assertThat(supporter["role"].asText()).isEqualTo("SUPPORTER")
        assertThat(supporter["defender"]["units"].single()["sent"].asInt()).isEqualTo(50)
    }

    @Test
    fun `a beaten attacker is told what it lost and nothing of what it met`() {
        val three = threeCities()
        give(three.mine, Unit.AXEMAN, 10)
        give(three.theirs, Unit.SPEARMAN, 50)
        send(three, three.mine, playerToken, mapOf("AXEMAN" to 10)).andExpect { status { isOk() } }
        jump(5401)

        val row = reports(three.world, playerToken)["rows"].single()
        assertThat(row["won"].asBoolean()).isFalse()
        val payload = report(three.world, playerToken, row["id"].asLong())["payload"]
        assertThat(payload["attacker"]["units"].single()["lost"].asInt()).isEqualTo(10)
        assertThat(payload["attacker"]["units"].single()["left"].asInt()).isZero()
        assertThat(payload["defender"].isNull).isTrue()
        assertThat(payload["plunder"].isNull).isTrue()
        assertThat(payload["wall"].isNull).isTrue()

        // The city it hit kept the field and sees both sides: it was at home.
        val theirs = report(three.world, otherPlayerToken, reports(three.world, otherPlayerToken)["rows"].single()["id"].asLong())
        assertThat(theirs["won"].asBoolean()).isTrue()
        assertThat(theirs["payload"]["defender"]["units"].single()["left"].asInt()).isEqualTo(31)
    }

    @Test
    fun `rams and catapults take the wall down and the report records it before and after`() {
        val three = threeCities()
        setLevel(three.theirs, Building.WALL, 10)
        give(three.mine, Unit.RAM, 10)
        give(three.mine, Unit.CATAPULT, 14)
        val points = cityRepository.findById(three.theirs).orElseThrow().points
        send(three, three.mine, playerToken, mapOf("RAM" to 10, "CATAPULT" to 14)).andExpect { status { isOk() } }

        jump(9001)   // five fields at 30 minutes each
        // 10 rams and 14 catapults are worth 38: 20 takes the Wall from 10 to 9, 18 more from 9 to 8.
        val payload = report(three.world, playerToken, reports(three.world, playerToken)["rows"].single()["id"].asLong())["payload"]
        assertThat(payload["wall"]["before"].asInt()).isEqualTo(10)
        assertThat(payload["wall"]["after"].asInt()).isEqualTo(8)
        assertThat(cityBuildingRepository.findById(CityBuildingId(three.theirs, Building.WALL)).orElseThrow().level).isEqualTo(8)
        // Two levels gone are two levels the city is no longer worth.
        assertThat(cityRepository.findById(three.theirs).orElseThrow().points).isLessThan(points)
    }

    @Test
    fun `a spy that outbids the cave sees the city and is never noticed`() {
        val three = threeCities()
        setLevel(three.mine, Building.CAVE, 5)
        caveSilver(three.mine, 1200)
        stock(three.theirs, 1000)   // at capacity, so the stock is the same when the spy gets there
        give(three.theirs, Unit.SPEARMAN, 40)
        setLevel(three.theirs, Building.WALL, 3)

        spy(three, 1200).andExpect {
            status { isOk() }
            jsonPath("$.cave.silver") { value(0) }     // the silver goes at once and never comes back
        }
        jump(1801)   // five fields at the spy's six minutes each

        val row = reports(three.world, playerToken)["rows"].single()
        assertThat(row["kind"].asText()).isEqualTo("ESPIONAGE")
        assertThat(row["summary"].asText()).isEqualTo("Espionage of ana's city")
        val payload = report(three.world, playerToken, row["id"].asLong())["payload"]
        assertThat(payload["success"].asBoolean()).isTrue()
        assertThat(payload["silver"].asLong()).isEqualTo(1200)
        assertThat(payload["seen"]["resources"]["wood"].asLong()).isEqualTo(1000)
        assertThat(payload["seen"]["units"].single()["type"].asText()).isEqualTo("SPEARMAN")
        assertThat(payload["seen"]["units"].single()["count"].asInt()).isEqualTo(40)
        val wall = payload["seen"]["buildings"].single { it["type"].asText() == "WALL" }
        assertThat(wall["level"].asInt()).isEqualTo(3)

        // The target learns nothing at all.
        assertThat(reports(three.world, otherPlayerToken)["total"].asLong()).isZero()
    }

    @Test
    fun `a spy the cave outbids learns only that it failed, and the target learns by whom`() {
        val three = threeCities()
        setLevel(three.mine, Building.CAVE, 5)
        caveSilver(three.mine, 1200)
        caveSilver(three.theirs, 1200)   // not more than the Cave holds: the attempt fails

        spy(three, 1200).andExpect { status { isOk() } }
        jump(1801)

        val mine = report(three.world, playerToken, reports(three.world, playerToken)["rows"].single()["id"].asLong())
        assertThat(mine["won"].asBoolean()).isFalse()
        assertThat(mine["summary"].asText()).isEqualTo("Espionage of ana's city failed")
        assertThat(mine["payload"]["success"].asBoolean()).isFalse()
        assertThat(mine["payload"]["seen"].isNull).isTrue()

        val theirRow = reports(three.world, otherPlayerToken)["rows"].single()
        assertThat(theirRow["kind"].asText()).isEqualTo("ESPIONAGE_CAUGHT")
        assertThat(theirRow["otherPlayer"].asText()).isEqualTo("george")
        val caught = report(three.world, otherPlayerToken, theirRow["id"].asLong())["payload"]
        assertThat(caught["player"].asText()).isEqualTo("george")
        assertThat(caught["city"].asText()).isEqualTo("george's city")
        assertThat(caught["silver"].asLong()).isEqualTo(1200)

        // The silver bought the attempt, not the outcome: none of it comes home.
        jump(1801)
        assertThat(cityResourcesRepository.findById(three.mine).orElseThrow().caveSilver).isZero()
    }

    @Test
    fun `a spy mission needs a cave, silver in it, and no mission already out at that city`() {
        val three = threeCities()
        spy(three, 500).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("REQUIREMENTS_NOT_MET") }
            jsonPath("$.details.CAVE") { value("1") }
        }
        setLevel(three.mine, Building.CAVE, 5)
        caveSilver(three.mine, 500)
        spy(three, 800).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NOT_ENOUGH_SILVER") }
            jsonPath("$.details.silver") { value("300") }
        }
        val (x, y) = coordinates(three.mine)
        spy(three, 100, x = x, y = y).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("SAME_CITY") }
        }
        spy(three, 100, x = x + 1, y = y + 40).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("CITY_NOT_FOUND") }
        }
        spy(three, 100).andExpect { status { isOk() } }
        // One mission per target city at a time: the way home is the cooldown.
        spy(three, 100).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_SPYING") }
        }
        // The other city is fair game while that one is out.
        spy(three, 100, x = coordinates(three.ally).first, y = coordinates(three.ally).second)
            .andExpect { status { isOk() } }
    }

    @Test
    fun `the list is newest first, reading one marks it read and a report may be thrown away`() {
        val three = oneBattle()
        give(three.mine, Unit.AXEMAN, 50)
        send(three, three.mine, playerToken, mapOf("AXEMAN" to 50)).andExpect { status { isOk() } }
        jump(5401)

        val page = reports(three.world, playerToken)
        assertThat(page["total"].asLong()).isEqualTo(2)
        assertThat(page["unread"].asLong()).isEqualTo(2)
        val newest = page["rows"][0]
        val oldest = page["rows"][1]
        assertThat(newest["createdAt"].asText()).isGreaterThan(oldest["createdAt"].asText())

        assertThat(report(three.world, playerToken, newest["id"].asLong())["read"].asBoolean()).isTrue()
        val afterReading = reports(three.world, playerToken)
        assertThat(afterReading["unread"].asLong()).isEqualTo(1)
        assertThat(afterReading["rows"][0]["read"].asBoolean()).isTrue()

        // Narrowing by kind narrows the rows, never the unread count the top bar carries. The filter is a
        // group: SPYING covers a run of one's own and one caught alike, and these are battles.
        val spying = reports(three.world, playerToken, "?kind=SPYING")
        assertThat(spying["total"].asLong()).isZero()
        assertThat(spying["unread"].asLong()).isEqualTo(1)
        assertThat(reports(three.world, playerToken, "?kind=BATTLE")["total"].asLong()).isEqualTo(2)
        get("/api/v1/worlds/${three.world}/reports?kind=NONSENSE", playerToken).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("VALIDATION_ERROR") }
        }

        mockMvc.delete("/api/v1/worlds/${three.world}/reports/${newest["id"].asLong()}") {
            header("Authorization", "Bearer $playerToken")
        }.andExpect { status { isNoContent() } }
        assertThat(reports(three.world, playerToken)["total"].asLong()).isEqualTo(1)
    }

    @Test
    fun `another player's report does not exist as far as the caller sees`() {
        val three = oneBattle()
        val mine = reports(three.world, playerToken)["rows"].single()["id"].asLong()
        get("/api/v1/worlds/${three.world}/reports/$mine", otherPlayerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("REPORT_NOT_FOUND") }
        }
        mockMvc.delete("/api/v1/worlds/${three.world}/reports/$mine") {
            header("Authorization", "Bearer $otherPlayerToken")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("REPORT_NOT_FOUND") }
        }
        assertThat(reports(three.world, playerToken)["total"].asLong()).isEqualTo(1)
    }

    @Test
    fun `only the newest two hundred are kept for a player in a world`() {
        val three = threeCities()
        val me = requireNotNull(userRepository.findByEmailIgnoreCase("george@caladon.test")).id!!
        repeat(205) { n ->
            reportService.write(
                worldId = three.world, ownerUserId = me, kind = ReportService.Kind.BATTLE,
                createdAt = clock.instant().plusSeconds(n.toLong()), subjectCity = "george's city",
                otherCity = "ana's city", otherPlayer = "ana", won = true, summary = "Attack $n",
                payload = mapOf("role" to "ATTACKER", "n" to n),
            )
        }
        val page = reports(three.world, playerToken, "?limit=1")
        assertThat(page["total"].asLong()).isEqualTo(200)
        assertThat(page["rows"].single()["summary"].asText()).isEqualTo("Attack 204")
        assertThat(reports(three.world, playerToken, "?limit=1&page=200")["rows"].single()["summary"].asText())
            .isEqualTo("Attack 5")
    }
}
