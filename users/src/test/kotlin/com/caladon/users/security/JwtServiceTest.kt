package com.caladon.users.security

import com.caladon.users.domain.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class JwtServiceTest {
    private val properties = JwtProperties(
        secret = "test-secret-test-secret-test-secret-test-secret",
        accessTokenTtl = Duration.ofHours(1),
        refreshTokenTtl = Duration.ofDays(7),
    )
    private val start: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun serviceAt(instant: Instant) = JwtService(properties, Clock.fixed(instant, ZoneOffset.UTC))

    @Test
    fun `access token round-trips user id and role`() {
        val service = serviceAt(start)
        val issued = service.issueAccessToken(42, Role.ADMINISTRATOR)

        assertThat(issued.expiresAt).isEqualTo(start.plus(Duration.ofHours(1)))
        assertThat(service.parseAccessToken(issued.token)).isEqualTo(JwtService.AccessClaims(42, Role.ADMINISTRATOR))
    }

    @Test
    fun `access token is rejected after one hour`() {
        val token = serviceAt(start).issueAccessToken(1, Role.PLAYER).token

        assertThat(serviceAt(start.plus(Duration.ofMinutes(59))).parseAccessToken(token)).isNotNull()
        assertThat(serviceAt(start.plus(Duration.ofMinutes(61))).parseAccessToken(token)).isNull()
    }

    @Test
    fun `refresh token round-trips and lasts one week`() {
        val service = serviceAt(start)
        val id = UUID.randomUUID()
        val issued = service.issueRefreshToken(7, id)

        assertThat(service.parseRefreshToken(issued.token))
            .isEqualTo(JwtService.RefreshClaims(7, id, start.plus(Duration.ofDays(7))))
        assertThat(serviceAt(start.plus(Duration.ofDays(8))).parseRefreshToken(issued.token)).isNull()
    }

    @Test
    fun `token kinds are not interchangeable`() {
        val service = serviceAt(start)
        val access = service.issueAccessToken(1, Role.PLAYER).token
        val refresh = service.issueRefreshToken(1, UUID.randomUUID()).token

        assertThat(service.parseRefreshToken(access)).isNull()
        assertThat(service.parseAccessToken(refresh)).isNull()
    }

    @Test
    fun `tampered or foreign tokens are rejected`() {
        val service = serviceAt(start)
        val token = service.issueAccessToken(1, Role.PLAYER).token
        val other = JwtService(
            properties.copy(secret = "another-secret-another-secret-another-secret"),
            Clock.fixed(start, ZoneOffset.UTC),
        )

        assertThat(service.parseAccessToken(token.dropLast(3) + "abc")).isNull()
        assertThat(service.parseAccessToken(other.issueAccessToken(1, Role.PLAYER).token)).isNull()
        assertThat(service.parseAccessToken("not-a-jwt")).isNull()
    }
}
