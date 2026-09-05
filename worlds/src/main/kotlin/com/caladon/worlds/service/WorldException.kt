package com.caladon.worlds.service

import org.springframework.http.HttpStatus

sealed class WorldException(val status: HttpStatus, val code: String, val details: Map<String, String>? = null) :
    RuntimeException(code) {
    class NameTaken : WorldException(HttpStatus.CONFLICT, "NAME_TAKEN")
    class NotFound : WorldException(HttpStatus.NOT_FOUND, "WORLD_NOT_FOUND")
    class NotDraft : WorldException(HttpStatus.CONFLICT, "WORLD_NOT_DRAFT")
    class NotPlayable : WorldException(HttpStatus.CONFLICT, "WORLD_NOT_PLAYABLE")
    class AlreadyJoined : WorldException(HttpStatus.CONFLICT, "ALREADY_JOINED")
    class Full : WorldException(HttpStatus.CONFLICT, "WORLD_FULL")
    class InvalidViewport(details: Map<String, String>) : WorldException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details)
}
