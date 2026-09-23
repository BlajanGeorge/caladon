package com.caladon.worlds.api

import com.caladon.users.domain.Role
import com.caladon.users.domain.User
import com.caladon.users.repository.UserRepository
import com.caladon.users.security.JwtService
import com.caladon.worlds.generation.MapConstants
import com.caladon.worlds.repository.BarbarianVillageRepository
import com.caladon.worlds.repository.CityRepository
import com.caladon.worlds.repository.CityResourcesRepository
import com.caladon.worlds.repository.CitySlotRepository
import com.caladon.worlds.repository.WorldMembershipRepository
import com.caladon.worlds.repository.WorldRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import kotlin.math.hypot

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(MutableClockConfig::class)
class WorldApiTest : ApiTestBase() {

    // ---- administrator ----

    @Test
    fun `admin creates a fully seeded DRAFT world`() {
        val result = post("/api/v1/admin/worlds", adminToken, mapOf("name" to "Caladon I")).andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Caladon I") }
            jsonPath("$.state") { value("DRAFT") }
        }
        val id = json(result)["id"].asLong()

        assertThat(citySlotRepository.countByWorldId(id)).isBetween(1_800L, 4_000L)
        assertThat(barbarianVillageRepository.countByWorldId(id)).isBetween(300L, 3_000L)

        post("/api/v1/admin/worlds", adminToken, mapOf("name" to "caladon i")).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NAME_TAKEN") }
        }
    }

    @Test
    fun `admin lists every world with player counts and approves drafts once`() {
        val id = createWorld()
        get("/api/v1/admin/worlds", adminToken).andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(id) }
            jsonPath("$[0].state") { value("DRAFT") }
            jsonPath("$[0].players") { value(0) }
            jsonPath("$[0].createdAt") { exists() }
        }

        post("/api/v1/admin/worlds/$id/approve", adminToken).andExpect {
            status { isOk() }
            jsonPath("$.state") { value("PLAYABLE") }
        }
        post("/api/v1/admin/worlds/$id/approve", adminToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("WORLD_NOT_DRAFT") }
        }
        post("/api/v1/admin/worlds/999999/approve", adminToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("WORLD_NOT_FOUND") }
        }

        post("/api/v1/worlds/$id/join", playerToken).andExpect { status { isOk() } }
        get("/api/v1/admin/worlds", adminToken).andExpect { jsonPath("$[0].players") { value(1) } }
    }

    @Test
    fun `admin endpoints are forbidden to players and closed to anonymous callers`() {
        post("/api/v1/admin/worlds", playerToken, mapOf("name" to "Nope")).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("FORBIDDEN") }
        }
        get("/api/v1/admin/worlds", playerToken).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/admin/worlds").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("UNAUTHORIZED") }
        }
        assertThat(worldRepository.count()).isZero()
    }

    // ---- player ----

    @Test
    fun `players see only PLAYABLE worlds with their joined flag`() {
        createWorld("Draft world")
        val playable = createPlayableWorld("Live world")

        get("/api/v1/worlds", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(playable) }
            jsonPath("$[0].name") { value("Live world") }
            jsonPath("$[0].joined") { value(false) }
        }
        get("/api/v1/worlds/mine", playerToken).andExpect { jsonPath("$.length()") { value(0) } }

        post("/api/v1/worlds/$playable/join", playerToken).andExpect { status { isOk() } }

        get("/api/v1/worlds", playerToken).andExpect { jsonPath("$[0].joined") { value(true) } }
        get("/api/v1/worlds", otherPlayerToken).andExpect { jsonPath("$[0].joined") { value(false) } }
        get("/api/v1/worlds/mine", playerToken).andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(playable) }
            jsonPath("$[0].name") { value("Live world") }
        }
    }

    @Test
    fun `join enrols the player and founds a start city near the centre, then at the frontier`() {
        val id = createPlayableWorld()

        val first = json(post("/api/v1/worlds/$id/join", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.worldId") { value(id) }
            jsonPath("$.startCity.id") { isNumber() }
            jsonPath("$.startCity.name") { value("george's city") }
            jsonPath("$.startCity.points") { value(39) }
        })["startCity"]
        val centre = (MapConstants.SIZE - 1) / 2.0
        assertThat(hypot(first["x"].asDouble() - centre, first["y"].asDouble() - centre)).isLessThan(25.0) // nearest free slot; generation is random

        val second = json(post("/api/v1/worlds/$id/join", otherPlayerToken).andExpect { status { isOk() } })["startCity"]
        val distance = hypot(first["x"].asDouble() - second["x"].asDouble(), first["y"].asDouble() - second["y"].asDouble())
        assertThat(distance).isGreaterThanOrEqualTo(MapConstants.START_CITY_MIN_DISTANCE).isLessThan(20.0)

        assertThat(membershipRepository.countByIdWorldId(id)).isEqualTo(2)
        assertThat(cityRepository.count()).isEqualTo(2)
    }

    @Test
    fun `join is rejected when already joined or the world is not playable`() {
        val draft = createWorld("Draft")
        post("/api/v1/worlds/$draft/join", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("WORLD_NOT_PLAYABLE") }
        }

        val live = createPlayableWorld("Live")
        post("/api/v1/worlds/$live/join", playerToken).andExpect { status { isOk() } }
        post("/api/v1/worlds/$live/join", playerToken).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("ALREADY_JOINED") }
        }
        post("/api/v1/worlds/999999/join", playerToken).andExpect { status { isNotFound() } }
    }

    @Test
    fun `cities mine lists the player's cities once joined`() {
        val draft = createWorld("Draft")
        get("/api/v1/worlds/$draft/cities/mine", playerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("WORLD_NOT_FOUND") }
        }

        val id = createPlayableWorld()
        get("/api/v1/worlds/$id/cities/mine", playerToken).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("NOT_JOINED") }
        }

        val start = json(post("/api/v1/worlds/$id/join", playerToken).andExpect { status { isOk() } })["startCity"]
        get("/api/v1/worlds/$id/cities/mine", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(start["id"].asLong()) }
            jsonPath("$[0].x") { value(start["x"].asInt()) }
            jsonPath("$[0].y") { value(start["y"].asInt()) }
            jsonPath("$[0].name") { value("george's city") }
            jsonPath("$[0].points") { value(39) }
        }
        get("/api/v1/worlds/$id/cities/mine", otherPlayerToken).andExpect { status { isForbidden() } }
    }

    @Test
    fun `map returns terrain, free slots, cities and barbarians for a rectangle`() {
        val id = createPlayableWorld()
        val city = json(post("/api/v1/worlds/$id/join", playerToken).andExpect { status { isOk() } })["startCity"]
        val cx = city["x"].asInt()
        val cy = city["y"].asInt()
        val (sx, sy, ex, ey) = listOf(cx - 20, cy - 20, cx + 20, cy + 20)

        val map = json(get("/api/v1/worlds/$id/map?startX=$sx&startY=$sy&endX=$ex&endY=$ey", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.startX") { value(sx) }
            jsonPath("$.endY") { value(ey) }
        })

        assertThat(map["terrain"].size()).isEqualTo(41 * 41)
        assertThat(map["terrain"].map { it.asInt() }).allMatch { it in 0..3 }

        val cities = map["cities"]
        assertThat(cities.size()).isEqualTo(1)
        assertThat(cities[0]["x"].asInt()).isEqualTo(cx)
        assertThat(cities[0]["y"].asInt()).isEqualTo(cy)
        assertThat(cities[0]["name"].asText()).isEqualTo("george's city")
        assertThat(cities[0]["points"].asInt()).isEqualTo(39)
        assertThat(cities[0]["owner"].asText()).isEqualTo("george")

        // The occupied slot is not listed as free; the city tile is GRASS; everything is inside the rectangle.
        val slots = map["slots"].map { it["x"].asInt() to it["y"].asInt() }
        assertThat(slots).isNotEmpty.doesNotContain(cx to cy)
        assertThat(map["barbarians"].size()).isGreaterThan(0)
        for (node in map["slots"] + map["barbarians"]) {
            assertThat(node["x"].asInt()).isBetween(sx, ex)
            assertThat(node["y"].asInt()).isBetween(sy, ey)
        }
        val width = ex - sx + 1
        assertThat(map["terrain"][(cy - sy) * width + (cx - sx)].asInt()).isEqualTo(0)
    }

    @Test
    fun `map validates the rectangle`() {
        val id = createPlayableWorld()

        get("/api/v1/worlds/$id/map?startX=10&startY=10&endX=5&endY=20", playerToken).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("VALIDATION_ERROR") }
            jsonPath("$.details.endX") { exists() }
        }
        get("/api/v1/worlds/$id/map?startX=0&startY=0&endX=499&endY=499", playerToken).andExpect {
            status { isBadRequest() }
            jsonPath("$.details.endX") { exists() }
            jsonPath("$.details.endY") { exists() }
        }
        get("/api/v1/worlds/$id/map?startX=0&startY=0&endX=500&endY=0", playerToken).andExpect {
            status { isBadRequest() }
            jsonPath("$.details.endX") { exists() }
        }
        get("/api/v1/worlds/$id/map?startX=0&startY=0&endX=99", playerToken).andExpect {
            status { isBadRequest() }
            jsonPath("$.details.endY") { exists() }
        }
        get("/api/v1/worlds/$id/map?startX=0&startY=0&endX=99&endY=99", playerToken).andExpect { status { isOk() } }
    }

    @Test
    fun `map of a DRAFT world is visible to administrators only`() {
        val draft = createWorld("Draft")
        val query = "/api/v1/worlds/$draft/map?startX=0&startY=0&endX=9&endY=9"

        get(query, playerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("WORLD_NOT_FOUND") }
        }
        get(query, adminToken).andExpect {
            status { isOk() }
            jsonPath("$.terrain.length()") { value(100) }
        }
    }

    // ---- resources ----

    @Test
    fun `join founds the city with the starting stock and population, and the detail settles production`() {
        val id = createPlayableWorld()
        val start = json(post("/api/v1/worlds/$id/join", playerToken).andExpect { status { isOk() } })["startCity"]
        val cityId = start["id"].asLong()

        val row = cityResourcesRepository.findById(cityId).orElseThrow()
        assertThat(row.wood).isEqualTo(500)
        assertThat(row.stone).isEqualTo(500)
        assertThat(row.iron).isEqualTo(500)
        assertThat(row.population).isEqualTo(240)

        get("/api/v1/worlds/$id/cities/$cityId", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cityId) }
            jsonPath("$.name") { value("george's city") }
            jsonPath("$.x") { value(start["x"].asInt()) }
            jsonPath("$.y") { value(start["y"].asInt()) }
            jsonPath("$.points") { value(39) }
            jsonPath("$.resources.wood.stock") { value(500) }
            jsonPath("$.resources.wood.ratePerHour") { value(30) }
            jsonPath("$.resources.capacity") { value(1000) }
            jsonPath("$.resources.serverTime") { exists() }
            jsonPath("$.population") { value(240) }
        }

        clock.advance(Duration.ofMinutes(5)) // 30/h = one unit per 2 min: +2, one minute of production carried as time
        get("/api/v1/worlds/$id/cities/$cityId", playerToken).andExpect {
            status { isOk() }
            jsonPath("$.resources.wood.stock") { value(502) }
            jsonPath("$.resources.stone.stock") { value(502) }
            jsonPath("$.resources.iron.stock") { value(502) }
            jsonPath("$.resources.serverTime") { value(clock.instant().toString()) }
        }
        val row2 = cityResourcesRepository.findById(cityId).orElseThrow()
        assertThat(row2.woodSettledAt).isEqualTo(clock.instant().minus(Duration.ofMinutes(1)))

        clock.advance(Duration.ofMinutes(1)) // the carried minute completes the third unit
        get("/api/v1/worlds/$id/cities/$cityId", playerToken).andExpect {
            jsonPath("$.resources.wood.stock") { value(503) }
        }

        clock.advance(Duration.ofMinutes(50)) // stays inside the 1 h access-token TTL (same clock): +25
        get("/api/v1/worlds/$id/cities/$cityId", playerToken).andExpect {
            jsonPath("$.resources.wood.stock") { value(528) }
        }
    }

    @Test
    fun `city detail is owner-only and hidden when the city or world is not visible`() {
        val id = createPlayableWorld()
        val cityId = json(post("/api/v1/worlds/$id/join", playerToken).andExpect { status { isOk() } })["startCity"]["id"].asLong()

        get("/api/v1/worlds/$id/cities/$cityId", otherPlayerToken).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("NOT_OWNER") }
        }
        get("/api/v1/worlds/$id/cities/999999", playerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("CITY_NOT_FOUND") }
        }
        get("/api/v1/worlds/999999/cities/$cityId", playerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("CITY_NOT_FOUND") }
        }
        val other = createPlayableWorld("Other")
        get("/api/v1/worlds/$other/cities/$cityId", playerToken).andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("CITY_NOT_FOUND") }
        }
    }
}
