package com.caladon.users.api

import com.caladon.users.security.AuthenticatedUser
import com.caladon.users.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest) {
        authService.register(request.email, request.nickname, request.password)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse {
        val result = authService.login(request.email, request.password)
        return LoginResponse(result.accessToken, result.refreshToken, result.nickname)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): RefreshResponse =
        RefreshResponse(authService.refresh(request.refreshToken))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: LogoutRequest,
    ) {
        authService.logout(user.id, request.refreshToken)
    }
}
