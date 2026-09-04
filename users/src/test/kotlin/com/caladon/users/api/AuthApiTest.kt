package com.caladon.users.api

import com.caladon.users.repository.RefreshTokenRepository
import com.caladon.users.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthApiTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository

    @BeforeEach
    fun clean() {
        refreshTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun postJson(path: String, body: Any, bearer: String? = null): ResultActionsDsl =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
            bearer?.let { header("Authorization", "Bearer $it") }
        }

    private fun register(email: String = "a@b.com", nickname: String = "george", password: String = "password1") =
        postJson("/api/v1/auth/register", mapOf("email" to email, "nickname" to nickname, "password" to password))

    private fun login(email: String = "a@b.com", password: String = "password1"): Map<String, String> {
        val response = postJson("/api/v1/auth/login", mapOf("email" to email, "password" to password))
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(response, Map::class.java) as Map<String, String>
    }

    @Test
    fun `register creates a PLAYER and returns 201 with empty body`() {
        register().andExpect {
            status { isCreated() }
            content { string("") }
        }
        val user = userRepository.findByEmailIgnoreCase("a@b.com")!!
        assertThat(user.role.name).isEqualTo("PLAYER")
        assertThat(user.nickname).isEqualTo("george")
        assertThat(user.passwordHash).isNotEqualTo("password1").startsWith("\$2")
    }

    @Test
    fun `register rejects duplicate email and nickname, case-insensitively`() {
        register().andExpect { status { isCreated() } }

        register(email = "A@B.com", nickname = "other").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("EMAIL_TAKEN") }
        }
        register(email = "c@d.com", nickname = "George").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("NICKNAME_TAKEN") }
        }
    }

    @Test
    fun `register validates input`() {
        register(email = "not-an-email", nickname = "x", password = "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("VALIDATION_ERROR") }
            jsonPath("$.details.email") { exists() }
            jsonPath("$.details.nickname") { exists() }
            jsonPath("$.details.password") { exists() }
        }
    }

    @Test
    fun `login returns both tokens and nickname`() {
        register().andExpect { status { isCreated() } }

        postJson("/api/v1/auth/login", mapOf("email" to "a@b.com", "password" to "password1")).andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isString() }
            jsonPath("$.refreshToken") { isString() }
            jsonPath("$.nickname") { value("george") }
        }
        assertThat(refreshTokenRepository.count()).isEqualTo(1)
    }

    @Test
    fun `login with wrong password or unknown email is 401`() {
        register().andExpect { status { isCreated() } }

        postJson("/api/v1/auth/login", mapOf("email" to "a@b.com", "password" to "wrong")).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("INVALID_CREDENTIALS") }
        }
        postJson("/api/v1/auth/login", mapOf("email" to "nobody@b.com", "password" to "password1")).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `refresh returns a new access token and keeps the refresh token`() {
        register().andExpect { status { isCreated() } }
        val tokens = login()

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to tokens["refreshToken"])).andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isString() }
            jsonPath("$.refreshToken") { doesNotExist() }
        }
        // Not rotated: still usable.
        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to tokens["refreshToken"]))
            .andExpect { status { isOk() } }
    }

    @Test
    fun `refresh rejects garbage and access tokens`() {
        register().andExpect { status { isCreated() } }
        val tokens = login()

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to "garbage")).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("INVALID_REFRESH_TOKEN") }
        }
        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to tokens["accessToken"])).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `logout invalidates the refresh token`() {
        register().andExpect { status { isCreated() } }
        val tokens = login()

        postJson("/api/v1/auth/logout", mapOf("refreshToken" to tokens["refreshToken"]), bearer = tokens["accessToken"])
            .andExpect { status { isNoContent() } }

        assertThat(refreshTokenRepository.count()).isZero()
        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to tokens["refreshToken"])).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `logout requires a valid access token`() {
        register().andExpect { status { isCreated() } }
        val tokens = login()

        postJson("/api/v1/auth/logout", mapOf("refreshToken" to tokens["refreshToken"])).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("UNAUTHORIZED") }
        }
        // A refresh token must not be usable as an access token.
        postJson("/api/v1/auth/logout", mapOf("refreshToken" to tokens["refreshToken"]), bearer = tokens["refreshToken"])
            .andExpect { status { isUnauthorized() } }
        assertThat(refreshTokenRepository.count()).isEqualTo(1)
    }

    @Test
    fun `logout cannot revoke another user's refresh token`() {
        register(email = "a@b.com", nickname = "alice").andExpect { status { isCreated() } }
        register(email = "b@b.com", nickname = "bob").andExpect { status { isCreated() } }
        val alice = login(email = "a@b.com")
        val bob = login(email = "b@b.com")

        postJson("/api/v1/auth/logout", mapOf("refreshToken" to bob["refreshToken"]), bearer = alice["accessToken"])
            .andExpect { status { isNoContent() } }

        postJson("/api/v1/auth/refresh", mapOf("refreshToken" to bob["refreshToken"]))
            .andExpect { status { isOk() } }
    }
}
