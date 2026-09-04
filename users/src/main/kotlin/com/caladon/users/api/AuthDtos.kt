package com.caladon.users.api

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 3, max = 20)
    @field:Pattern(regexp = "^[A-Za-z0-9_]+$", message = "may contain only letters, digits and underscore")
    val nickname: String,

    @field:NotBlank @field:Size(min = 8, max = 72)
    val password: String,
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val nickname: String,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class RefreshResponse(
    val accessToken: String,
)

data class LogoutRequest(
    @field:NotBlank val refreshToken: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: String,
    val details: Map<String, String>? = null,
)
