package com.caladon.worlds.api

import com.caladon.users.security.AuthenticatedUser
import com.caladon.worlds.service.WorldService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/worlds")
@PreAuthorize("hasRole('ADMINISTRATOR')")
class AdminWorldController(private val worldService: WorldService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal admin: AuthenticatedUser, @Valid @RequestBody request: CreateWorldRequest): WorldResponse {
        val world = worldService.create(request.name, admin.id)
        return WorldResponse(requireNotNull(world.id), world.name, world.state)
    }

    @GetMapping
    fun list(): List<AdminWorldResponse> = worldService.listAll().map { (w, players) ->
        AdminWorldResponse(requireNotNull(w.id), w.name, w.state, players, w.createdAt)
    }

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long): WorldResponse {
        val world = worldService.approve(id)
        return WorldResponse(requireNotNull(world.id), world.name, world.state)
    }
}
