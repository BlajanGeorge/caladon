package com.caladon.users.service

import org.springframework.http.HttpStatus

/** Errors surfaced to the API as `{ "error": "<code>" }` with the given status. */
sealed class AuthException(val status: HttpStatus, val code: String) : RuntimeException(code) {
    class EmailTaken : AuthException(HttpStatus.CONFLICT, "EMAIL_TAKEN")
    class NicknameTaken : AuthException(HttpStatus.CONFLICT, "NICKNAME_TAKEN")
    class InvalidCredentials : AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS")
    class InvalidRefreshToken : AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN")
}
