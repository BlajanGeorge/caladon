package com.caladon.users.security

import com.caladon.users.domain.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Issues and verifies the two JWT kinds:
 *  - access token: `sub` = user id, `role`; valid [JwtProperties.accessTokenTtl].
 *  - refresh token: `sub` = user id, `jti` = store id, `typ` = "refresh"; valid [JwtProperties.refreshTokenTtl].
 */
@Service
class JwtService(
    private val properties: JwtProperties,
    private val clock: Clock,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))

    data class IssuedToken(val token: String, val expiresAt: Instant)
    data class AccessClaims(val userId: Long, val role: Role)
    data class RefreshClaims(val userId: Long, val tokenId: UUID, val expiresAt: Instant)

    fun issueAccessToken(userId: Long, role: Role): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(properties.accessTokenTtl)
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_ROLE, role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return IssuedToken(token, expiresAt)
    }

    fun issueRefreshToken(userId: Long, tokenId: UUID): IssuedToken {
        val now = clock.instant()
        val expiresAt = now.plus(properties.refreshTokenTtl)
        val token = Jwts.builder()
            .subject(userId.toString())
            .id(tokenId.toString())
            .claim(CLAIM_TYPE, TYPE_REFRESH)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return IssuedToken(token, expiresAt)
    }

    /** Returns null if the token is not a valid, unexpired access token. */
    fun parseAccessToken(token: String): AccessClaims? {
        val claims = parse(token) ?: return null
        if (claims[CLAIM_TYPE] != null) return null // refresh tokens are not access tokens
        val userId = claims.subject?.toLongOrNull() ?: return null
        val role = (claims[CLAIM_ROLE] as? String)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
            ?: return null
        return AccessClaims(userId, role)
    }

    /** Returns null if the token is not a valid, unexpired refresh token (signature-wise; store is checked separately). */
    fun parseRefreshToken(token: String): RefreshClaims? {
        val claims = parse(token) ?: return null
        if (claims[CLAIM_TYPE] != TYPE_REFRESH) return null
        val userId = claims.subject?.toLongOrNull() ?: return null
        val tokenId = claims.id?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val expiresAt = claims.expiration?.toInstant() ?: return null
        return RefreshClaims(userId, tokenId, expiresAt)
    }

    private fun parse(token: String): Claims? =
        try {
            Jwts.parser()
                .verifyWith(key)
                .clock { Date.from(clock.instant()) }
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private companion object {
        const val CLAIM_ROLE = "role"
        const val CLAIM_TYPE = "typ"
        const val TYPE_REFRESH = "refresh"
    }
}
