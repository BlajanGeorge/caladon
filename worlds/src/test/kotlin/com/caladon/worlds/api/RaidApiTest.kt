package com.caladon.worlds.api

import com.caladon.worlds.army.CityUnit
import com.caladon.worlds.army.CityUnitId
import com.caladon.worlds.army.CityUnitRepository
import com.caladon.worlds.buildings.CityBuilding
import com.caladon.worlds.buildings.CityBuildingId
import com.caladon.worlds.buildings.CityBuildingRepository
import com.caladon.worlds.domain.BarbarianVillage
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
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

/** Raiding barbarian villages (ARCHITECTURE.md → Raiding barbarians), end to end over the API. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(MutableClockConfig::class)
class RaidApiTest : ApiTestBase() {

    @Autowired lateinit var cityUnitRepository: CityUnitRepository
    @Autowired lateinit var cityBuildingRepository: CityBuildingRepository
    @Autowired lateinit var sweeper: CitySweeper

    /** One city and a village five fields away, so every raid here is 5 fields at the units' own speed. */
    private data class Raid(val world: Long, val city: Long, val village: Long, val x: Int, val y: Int)

    private fun oneVillage(): Raid {
        val world = createPlayableWorld()
        val city = json(post("/api/v1/worlds/$world/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()
        val (x, y) = coordinates(city)
        val village = placeVillage(world, 0, x + 3, y + 4)
        return Raid(world, city, village, x + 3, y + 4)
    }

    private fun coordinates(cityId: Long): Pair<Int, Int> {
        val slot = citySlotRepository.findById(cityRepository.findById(cityId).orElseThrow().slotId).orElseThrow()
        return slot.x.toInt() to slot.y.toInt()
    }

    /**
     * Moves the world's [nth] village onto an exact field, shifting any other village standing there out
     * of the way. An empty city slot on the field is no obstacle: only a city that has been founded is.
     */
    private fun placeVillage(world: Long, nth: Int, x: Int, y: Int): Long {
        val all = barbarianVillageRepository.findAll().filter { it.worldId == world }.sortedBy { it.id }
        val village = all[nth]
        all.firstOrNull { it.id != village.id && it.x.toInt() == x && it.y.toInt() == y }?.let { blocking ->
            var free = 499
            while (all.any { it.x.toInt() == free && it.y.toInt() == free }) free--
            blocking.x = free.toShort()
            blocking.y = free.toShort()
            barbarianVillageRepository.saveAndFlush(blocking)
        }
        village.x = x.toShort()
        village.y = y.toShort()
        return requireNotNull(barbarianVillageRepository.saveAndFlush(village).id)
    }

    private fun village(id: Long): BarbarianVillage = barbarianVillageRepository.findById(id).orElseThrow()

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

    private fun send(
        raid: Raid, units: Map<String, Int>, kind: String = "ATTACK", x: Int = raid.x, y: Int = raid.y,
    ): ResultActionsDsl = post(
        "/api/v1/worlds/${raid.world}/cities/${raid.city}/movements", playerToken,
        mapOf("kind" to kind, "targetX" to x, "targetY" to y, "units" to units),
    )

    private fun movements(raid: Raid): JsonNode =
        json(get("/api/v1/worlds/${raid.world}/cities/${raid.city}/movements", playerToken).andExpect { status { isOk() } })

    private fun reports(raid: Raid): JsonNode =
        json(get("/api/v1/worlds/${raid.world}/reports", playerToken).andExpect { status { isOk() } })

    private fun report(raid: Raid, id: Long): JsonNode =
        json(get("/api/v1/worlds/${raid.world}/reports/$id", playerToken).andExpect { status { isOk() } })

    @Test
    fun `a raid on a fresh village wins, takes the store and hardens it`() {
        val raid = oneVillage()
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }

        // The village reads as one place with no owner, wherever the panel shows it.
        val out = movements(raid)["outgoing"].single()
        assertThat(out["otherCityName"].asText()).isEqualTo("Barbarian village")
        assertThat(out["otherPlayerName"].asText()).isEmpty()
        assertThat(out["x"].asInt()).isEqualTo(raid.x)
        assertThat(out["y"].asInt()).isEqualTo(raid.y)

        jump(5401)
        // 4000 attack against 100 militia (2000): the raid wins, losing 35 of its 100 axemen.
        val home = movements(raid)["outgoing"].single()
        assertThat(home["direction"].asText()).isEqualTo("HOMEWARD")
        assertThat(home["units"].single()["count"].asInt()).isEqualTo(65)
        // 65 axemen carry 650, but a level-1 Deposit holds 1000, so 200 of each is the raid's share.
        assertThat(home["carrying"]["wood"].asLong()).isEqualTo(200)
        assertThat(home["carrying"]["stone"].asLong()).isEqualTo(200)
        assertThat(home["carrying"]["silver"].asLong()).isEqualTo(200)

        val after = village(raid.village)
        assertThat(after.level).isEqualTo(2)
        assertThat(after.wood).isEqualTo(800)
        assertThat(after.raidedAt).isNotNull()

        jump(5401)
        get("/api/v1/worlds/${raid.world}/cities/${raid.city}", playerToken).andExpect {
            jsonPath("$.units[?(@.type=='AXEMAN')].home") { value(65) }
            // 500 at founding, 90 cut in the three hours the raid took, and 200 carried home.
            jsonPath("$.resources.wood.stock") { value(790) }
        }
    }

    @Test
    fun `a second raid meets twice the militia and a store refilled only by the time elapsed`() {
        val raid = oneVillage()
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        assertThat(village(raid.village).wood).isEqualTo(800)

        // An hour and a half later, at 200 an hour for a level-2 village, the store is 300 the richer.
        give(raid.city, Unit.AXEMAN, 200)
        send(raid, mapOf("AXEMAN" to 200)).andExpect { status { isOk() } }
        jump(5401)

        val payload = report(raid, reports(raid)["rows"][0]["id"].asLong())["payload"]
        assertThat(payload["defender"]["units"].single()["sent"].asInt()).isEqualTo(200)
        // 8000 against 200 militia (4000): 70 of the 200 axemen fall, and the share is still 200 each.
        assertThat(payload["attacker"]["units"].single()["left"].asInt()).isEqualTo(130)
        assertThat(payload["plunder"]["wood"].asLong()).isEqualTo(200)
        val after = village(raid.village)
        assertThat(after.wood).isEqualTo(900)   // 800 + 300 refilled - 200 taken
        assertThat(after.level).isEqualTo(3)
    }

    @Test
    fun `a raid that loses everything takes nothing and leaves the village as it was`() {
        val raid = oneVillage()
        give(raid.city, Unit.AXEMAN, 10)
        send(raid, mapOf("AXEMAN" to 10)).andExpect { status { isOk() } }

        jump(5401)
        // 400 against 2000: the raid dies to the last man and 8 militia fall with it.
        assertThat(movements(raid)["outgoing"]).isEmpty()
        assertThat(cityUnitRepository.findById(CityUnitId(raid.city, Unit.AXEMAN)).orElseThrow().count).isZero()
        val after = village(raid.village)
        assertThat(after.level).isEqualTo(1)
        assertThat(after.wood).isEqualTo(1000)
        assertThat(after.raidedAt).isNull()

        val row = reports(raid)["rows"].single()
        assertThat(row["won"].asBoolean()).isFalse()
        val payload = report(raid, row["id"].asLong())["payload"]
        assertThat(payload["attacker"]["units"].single()["lost"].asInt()).isEqualTo(10)
        assertThat(payload["plunder"].isNull).isTrue()
        // The militia are not paid for: what they killed earns the raider nothing back.
        assertThat(payload["attacker"]["points"].asLong()).isEqualTo(8)
    }

    @Test
    fun `the plunder is the least of the deposit's share, the store and what the survivors carry`() {
        val raid = oneVillage()
        val (cx, cy) = coordinates(raid.city)
        val second = placeVillage(raid.world, 1, cx - 3, cy + 4)
        val third = placeVillage(raid.world, 2, cx + 3, cy - 4)

        // A level-1 Deposit holds 1000: its fifth is the least of the three.
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        assertThat(movements(raid)["outgoing"].single()["carrying"]["wood"].asLong()).isEqualTo(200)
        assertThat(village(raid.village).wood).isEqualTo(800)

        // With room at home, 53 light cavalry carry 4240 and the village's own store runs out first.
        setLevel(raid.city, Building.DEPOSIT, 20)
        give(raid.city, Unit.LIGHT_CAV, 60)
        send(raid, mapOf("LIGHT_CAV" to 60), x = cx - 3, y = cy + 4).andExpect { status { isOk() } }
        jump(3001)   // five fields at the Light Cavalry's ten minutes each
        val emptied = movements(raid)["outgoing"].single { it["x"].asInt() == cx - 3 }
        assertThat(emptied["carrying"]["wood"].asLong()).isEqualTo(1000)
        assertThat(village(second).wood).isZero()

        // Room at home and a full store: 65 axemen can carry 650 of it and no more.
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100), x = cx + 3, y = cy - 4).andExpect { status { isOk() } }
        jump(5401)
        val carried = movements(raid)["outgoing"].single { it["x"].asInt() == cx + 3 && it["y"].asInt() == cy - 4 }
        assertThat(carried["carrying"]["wood"].asLong()).isEqualTo(217)
        assertThat(carried["carrying"]["stone"].asLong()).isEqualTo(217)
        assertThat(carried["carrying"]["silver"].asLong()).isEqualTo(216)
        assertThat(village(third).wood).isEqualTo(783)
    }

    @Test
    fun `only an attack reaches a village`() {
        val raid = oneVillage()
        give(raid.city, Unit.SPEARMAN, 10)
        send(raid, mapOf("SPEARMAN" to 10), kind = "SUPPORT").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NO_ONE_THERE") }
        }
        send(raid, mapOf("SPEARMAN" to 10), kind = "ESPIONAGE").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NO_ONE_THERE") }
        }
        setLevel(raid.city, Building.CAVE, 5)
        val row = cityResourcesRepository.findById(raid.city).orElseThrow()
        row.caveSilver = 500
        cityResourcesRepository.save(row)
        post(
            "/api/v1/worlds/${raid.world}/cities/${raid.city}/spy", playerToken,
            mapOf("targetX" to raid.x, "targetY" to raid.y, "silver" to 100),
        ).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NO_ONE_THERE") }
        }
        // Nothing left and nothing was spent.
        assertThat(cityUnitRepository.findById(CityUnitId(raid.city, Unit.SPEARMAN)).orElseThrow().count).isEqualTo(10)
        assertThat(cityResourcesRepository.findById(raid.city).orElseThrow().caveSilver).isEqualTo(500)
        assertThat(movements(raid)["outgoing"]).isEmpty()
    }

    @Test
    fun `a village nobody has bothered for a day falls back a level`() {
        val raid = oneVillage()
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        assertThat(village(raid.village).level).isEqualTo(2)

        jump(Duration.ofHours(24).seconds)
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)

        // Back at a hundred men when the raid arrives, so 100 axemen take it again — and harden it again.
        val payload = report(raid, reports(raid)["rows"][0]["id"].asLong())["payload"]
        assertThat(payload["defender"]["units"].single()["sent"].asInt()).isEqualTo(100)
        val after = village(raid.village)
        assertThat(after.level).isEqualTo(2)
        // A level-1 village holds a thousand, whatever it had filled up to while it was still a level 2.
        assertThat(after.wood).isEqualTo(800)
    }

    @Test
    fun `the report names the militia as the defending side and pays the raider a point a man`() {
        val raid = oneVillage()
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)
        sweeper.sweep()

        val row = reports(raid)["rows"].single()
        assertThat(row["kind"].asText()).isEqualTo("BATTLE")
        assertThat(row["subjectCity"].asText()).isEqualTo("george's city")
        assertThat(row["otherCity"].asText()).isEqualTo("Barbarian village")
        assertThat(row["otherPlayer"].isNull).isTrue()
        assertThat(row["won"].asBoolean()).isTrue()
        assertThat(row["summary"].asText()).isEqualTo("Attack on Barbarian village")

        val payload = report(raid, row["id"].asLong())["payload"]
        assertThat(payload["role"].asText()).isEqualTo("ATTACKER")
        assertThat(payload["attacker"]["player"].asText()).isEqualTo("george")
        assertThat(payload["attacker"]["points"].asLong()).isEqualTo(100)   // a militiaman is one population
        val militia = payload["defender"]["units"].single()
        assertThat(militia["type"].asText()).isEqualTo("MILITIA")
        assertThat(militia["name"].asText()).isEqualTo("Militia")
        assertThat(militia["sent"].asInt()).isEqualTo(100)
        assertThat(militia["lost"].asInt()).isEqualTo(100)
        assertThat(militia["left"].asInt()).isZero()
        assertThat(payload["defender"]["player"].asText()).isEmpty()
        assertThat(payload["defender"]["city"].asText()).isEqualTo("Barbarian village")
        assertThat(payload["defender"]["points"].asLong()).isEqualTo(35)   // the axemen it took with it
        assertThat(payload["wall"].isNull).isTrue()
        assertThat(payload["plunder"]["wood"].asLong()).isEqualTo(200)

        // The standings pay a raid as they pay any attack: the population it killed.
        val me = json(get("/api/v1/worlds/${raid.world}/ranking?board=attack", playerToken).andExpect { status { isOk() } })
        assertThat(me["me"]["attackPoints"].asLong()).isEqualTo(100)
    }

    @Test
    fun `the map tells the raider what a village is`() {
        val raid = oneVillage()
        give(raid.city, Unit.AXEMAN, 100)
        send(raid, mapOf("AXEMAN" to 100)).andExpect { status { isOk() } }
        jump(5401)

        val map = json(
            get(
                "/api/v1/worlds/${raid.world}/map?startX=${raid.x - 1}&startY=${raid.y - 1}&endX=${raid.x + 1}&endY=${raid.y + 1}",
                playerToken,
            ).andExpect { status { isOk() } },
        )
        val village = map["barbarians"].single { it["id"].asLong() == raid.village }
        assertThat(village["x"].asInt()).isEqualTo(raid.x)
        assertThat(village["y"].asInt()).isEqualTo(raid.y)
        assertThat(village["level"].asInt()).isEqualTo(2)
    }
}
