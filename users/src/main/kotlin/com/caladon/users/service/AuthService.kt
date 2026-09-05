package com.caladon.users.service

import com.caladon.users.domain.RefreshToken
import com.caladon.users.domain.Role
import com.caladon.users.domain.User
import com.caladon.users.repository.RefreshTokenRepository
import com.caladon.users.repository.UserRepository
import com.caladon.users.security.JwtService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val clock: Clock,
) {
    data class LoginResult(val accessToken: String, val refreshToken: String, val nickname: String)

    /** Creates a PLAYER account. Throws [AuthException.EmailTaken] / [AuthException.NicknameTaken]. */
    @Transactional
    fun register(email: String, nickname: String, rawPassword: String) {
        val normalizedEmail = email.trim().lowercase()
        val normalizedNickname = nickname.trim()
        rejectIfTaken(normalizedEmail, normalizedNickname)

        val user = User(
            role = Role.PLAYER,
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(rawPassword),
            nickname = normalizedNickname,
            createdAt = clock.instant(),
        )
        try {
            userRepository.saveAndFlush(user)
        } catch (e: DataIntegrityViolationException) {
            // Lost a race with a concurrent registration; report which field collided.
            rejectIfTaken(normalizedEmail, normalizedNickname)
            throw e
        }
    }

    @Transactional
    fun login(email: String, rawPassword: String): LoginResult {
        val user = userRepository.findByEmailIgnoreCase(email.trim())
        // Always run the hash comparison so timing does not reveal whether the email exists.
        val matches = passwordEncoder.matches(rawPassword, user?.passwordHash ?: DUMMY_HASH)
        if (user == null || !matches) throw AuthException.InvalidCredentials()

        val userId = requireNotNull(user.id)
        val access = jwtService.issueAccessToken(userId, user.role)
        val refreshId = UUID.randomUUID()
        val refresh = jwtService.issueRefreshToken(userId, refreshId)
        refreshTokenRepository.save(
            RefreshToken(id = refreshId, userId = userId, expiresAt = refresh.expiresAt, createdAt = clock.instant()),
        )
        return LoginResult(access.token, refresh.token, user.nickname)
    }

    /** Returns a fresh access token. The refresh token is not rotated. */
    @Transactional(readOnly = true)
    fun refresh(refreshToken: String): String {
        val claims = jwtService.parseRefreshToken(refreshToken) ?: throw AuthException.InvalidRefreshToken()
        val stored = refreshTokenRepository.findById(claims.tokenId).orElse(null)
            ?: throw AuthException.InvalidRefreshToken()
        if (stored.userId != claims.userId || !stored.expiresAt.isAfter(clock.instant())) {
            throw AuthException.InvalidRefreshToken()
        }
        val user = userRepository.findById(claims.userId).orElse(null) ?: throw AuthException.InvalidRefreshToken()
        return jwtService.issueAccessToken(claims.userId, user.role).token
    }

    /** Revokes the refresh token if it belongs to [userId]. Idempotent. */
    @Transactional
    fun logout(userId: Long, refreshToken: String) {
        val claims = jwtService.parseRefreshToken(refreshToken) ?: return
        refreshTokenRepository.deleteByIdAndUserId(claims.tokenId, userId)
    }

    private fun rejectIfTaken(email: String, nickname: String) {
        if (userRepository.existsByEmailIgnoreCase(email)) throw AuthException.EmailTaken()
        if (userRepository.existsByNicknameIgnoreCase(nickname)) throw AuthException.NicknameTaken()
    }

    private companion object {
        /** A valid bcrypt hash of an unguessable value, used to equalize login timing for unknown emails. */
        val DUMMY_HASH: String = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .encode(UUID.randomUUID().toString())
    }
}
