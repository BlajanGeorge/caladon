package com.caladon.users.security

import com.caladon.users.domain.Role

/** Principal placed in the SecurityContext for a request carrying a valid access token. */
data class AuthenticatedUser(
    val id: Long,
    val role: Role,
)
