package com.booster.kotlin.damagochiservice.creature.web

import com.booster.kotlin.damagochiservice.creature.application.CreateCreatureCommand
import com.booster.kotlin.damagochiservice.creature.application.CreatureApplicationService
import com.booster.kotlin.damagochiservice.creature.application.CreatureSummary
import com.booster.kotlin.damagochiservice.common.web.CurrentUserId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/creatures")
class CreatureController(
    private val creatureApplicationService: CreatureApplicationService
) {
    @GetMapping
    fun getCreatures(@CurrentUserId userId: Long): ResponseEntity<List<CreatureSummary>> =
        ResponseEntity.ok(creatureApplicationService.findCreatures(userId))

    @PostMapping
    fun createCreature(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CreateCreatureRequest
    ): ResponseEntity<CreatureSummary> {
        val created = creatureApplicationService.createCreature(request.toCommand(userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PostMapping("/{id}/activate")
    fun activateCreature(
        @CurrentUserId userId: Long,
        @PathVariable id: Long
    ): ResponseEntity<CreatureSummary> =
        ResponseEntity.ok(creatureApplicationService.activateCreature(userId, id))

    @PostMapping("/{id}/actions/feed")
    fun feed(
        @CurrentUserId userId: Long,
        @PathVariable id: Long
    ): ResponseEntity<CreatureSummary> =
        ResponseEntity.ok(creatureApplicationService.feed(userId, id))

    @PostMapping("/{id}/actions/sleep")
    fun sleep(
        @CurrentUserId userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: SleepRequest
    ): ResponseEntity<CreatureSummary> =
        ResponseEntity.ok(creatureApplicationService.updateSleep(userId, id, request.sleeping))

    @PostMapping("/{id}/actions/treat")
    fun treat(
        @CurrentUserId userId: Long,
        @PathVariable id: Long
    ): ResponseEntity<CreatureSummary> =
        ResponseEntity.ok(creatureApplicationService.treat(userId, id))
}

data class CreateCreatureRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val species: String
) {
    fun toCommand(userId: Long): CreateCreatureCommand =
        CreateCreatureCommand(
            userId = userId,
            name = name,
            species = species
        )
}

data class SleepRequest(
    val sleeping: Boolean
)




