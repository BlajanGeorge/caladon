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
    class NotJoined : WorldException(HttpStatus.FORBIDDEN, "NOT_JOINED")
    class CityNotFound : WorldException(HttpStatus.NOT_FOUND, "CITY_NOT_FOUND")
    class NotOwner : WorldException(HttpStatus.FORBIDDEN, "NOT_OWNER")
    class MaxLevel : WorldException(HttpStatus.CONFLICT, "MAX_LEVEL")
    /** details: building code → level still needed. */
    class RequirementsNotMet(details: Map<String, String>) : WorldException(HttpStatus.CONFLICT, "REQUIREMENTS_NOT_MET", details)
    class QueueFull : WorldException(HttpStatus.CONFLICT, "QUEUE_FULL")
    /** details: resource → shortfall. */
    class NotEnoughResources(details: Map<String, String>) : WorldException(HttpStatus.CONFLICT, "NOT_ENOUGH_RESOURCES", details)
    class NotEnoughPopulation(shortfall: String) : WorldException(HttpStatus.CONFLICT, "NOT_ENOUGH_POPULATION", mapOf("population" to shortfall))
    class OrderNotFound : WorldException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND")
    class NotLastInQueue : WorldException(HttpStatus.CONFLICT, "NOT_LAST_IN_QUEUE")
    class AlreadyStudied : WorldException(HttpStatus.CONFLICT, "ALREADY_STUDIED")
    class NotStudied : WorldException(HttpStatus.CONFLICT, "NOT_STUDIED")
    class InvalidCount : WorldException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", mapOf("count" to "must be between 1 and 10000"))
    class InvalidViewport(details: Map<String, String>) : WorldException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details)
}
