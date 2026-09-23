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
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant

/** Shared setup for the API tests: one PostgreSQL container, users with tokens, JSON helpers. */
abstract class ApiTestBase {

    companion object {
        /**
         * One PostgreSQL for the whole test JVM, started once and shared by every API test class. Not a
         * `@Container`: the JUnit extension would stop it after the first class while the cached Spring
         * context still points at it.
         */
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").also { it.start() }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var worldRepository: WorldRepository
    @Autowired lateinit var cityRepository: CityRepository
    @Autowired lateinit var citySlotRepository: CitySlotRepository
    @Autowired lateinit var barbarianVillageRepository: BarbarianVillageRepository
    @Autowired lateinit var membershipRepository: WorldMembershipRepository
    @Autowired lateinit var cityResourcesRepository: CityResourcesRepository
    @Autowired lateinit var clock: MutableClock

    protected lateinit var adminToken: String
    protected lateinit var playerToken: String
    protected lateinit var otherPlayerToken: String

    @BeforeEach
    fun setUp() {
        clock.reset()
        worldRepository.deleteAll() // cascades to slots, cities, villages, memberships
        userRepository.deleteAll()
        adminToken = tokenFor(createUser("admin@caladon.test", "admin", Role.ADMINISTRATOR))
        playerToken = tokenFor(createUser("george@caladon.test", "george", Role.PLAYER))
        otherPlayerToken = tokenFor(createUser("ana@caladon.test", "ana", Role.PLAYER))
    }

    protected fun createUser(email: String, nickname: String, role: Role): User = userRepository.save(
        User(role = role, email = email, passwordHash = "x", nickname = nickname, createdAt = Instant.now()),
    )

    protected fun tokenFor(user: User) = jwtService.issueAccessToken(requireNotNull(user.id), user.role).token

    protected fun post(path: String, token: String, body: Any? = null): ResultActionsDsl = mockMvc.post(path) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        body?.let { content = objectMapper.writeValueAsString(it) }
    }

    protected fun get(path: String, token: String): ResultActionsDsl = mockMvc.get(path) {
        header("Authorization", "Bearer $token")
    }

    protected fun json(result: ResultActionsDsl): JsonNode = objectMapper.readTree(result.andReturn().response.contentAsString)

    protected fun createWorld(name: String = "Caladon I"): Long =
        json(post("/api/v1/admin/worlds", adminToken, mapOf("name" to name)).andExpect { status { isCreated() } })["id"].asLong()

    protected fun createPlayableWorld(name: String = "Caladon I"): Long =
        createWorld(name).also { post("/api/v1/admin/worlds/$it/approve", adminToken).andExpect { status { isOk() } } }

}
