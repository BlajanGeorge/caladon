package com.caladon.users.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "caladon.jwt")
data class JwtProperties(
    /** HMAC-SHA256 signing secret. Must be at least 32 bytes. */
    val secret: String,
    val accessTokenTtl: Duration = Duration.ofHours(1),
    val refreshTokenTtl: Duration = Duration.ofDays(7),
)
