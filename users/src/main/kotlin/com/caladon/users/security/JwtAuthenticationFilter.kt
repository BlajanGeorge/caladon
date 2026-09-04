package com.caladon.users.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Reads `Authorization: Bearer <access token>`; on success populates the SecurityContext with an
 * [AuthenticatedUser] principal and a `ROLE_<role>` authority. Invalid/missing tokens simply leave
 * the request unauthenticated (Spring Security then answers 401 for protected endpoints).
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER_PREFIX, ignoreCase = true) &&
            SecurityContextHolder.getContext().authentication == null
        ) {
            val token = header.substring(BEARER_PREFIX.length).trim()
            jwtService.parseAccessToken(token)?.let { claims ->
                val principal = AuthenticatedUser(claims.userId, claims.role)
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role.name}"))
                val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
