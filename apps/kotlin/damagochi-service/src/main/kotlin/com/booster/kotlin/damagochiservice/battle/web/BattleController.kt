package com.booster.kotlin.damagochiservice.battle.web

import com.booster.kotlin.damagochiservice.battle.application.BattleApplicationService
import com.booster.kotlin.damagochiservice.battle.application.RoomCancelResponse
import com.booster.kotlin.damagochiservice.battle.application.BattleDetailView
import com.booster.kotlin.damagochiservice.battle.application.RoomListResponse
import com.booster.kotlin.damagochiservice.battle.application.RandomQueueResponse
import com.booster.kotlin.damagochiservice.battle.application.RoomInfoResponse
import com.booster.kotlin.damagochiservice.battle.application.RoomCreateResponse
import com.booster.kotlin.damagochiservice.common.web.CurrentUserId
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/battles")
class BattleController(
    private val battleApplicationService: BattleApplicationService
) {
    @PostMapping("/random/queue")
    fun queueRandom(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: RandomQueueRequest
    ): ResponseEntity<RandomQueueResponse> =
        ResponseEntity.ok(
            battleApplicationService.queueRandom(
                userId = userId,
                creatureId = request.creatureId
            )
        )

    @PostMapping("/rooms")
    fun createRoom(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: RandomQueueRequest
    ): ResponseEntity<RoomCreateResponse> =
        ResponseEntity.ok(
            battleApplicationService.createRoom(
                userId = userId,
                creatureId = request.creatureId
            )
        )

    @GetMapping("/rooms/mine")
    fun getMyRooms(
        @CurrentUserId userId: Long
    ): ResponseEntity<RoomListResponse> =
        ResponseEntity.ok(
            battleApplicationService.getMyRooms(userId)
        )

    @GetMapping("/rooms/{code}")
    fun getRoom(
        @CurrentUserId userId: Long,
        @PathVariable code: String
    ): ResponseEntity<RoomInfoResponse> =
        ResponseEntity.ok(
            battleApplicationService.getRoom(
                userId = userId,
                code = code
            )
        )

    @DeleteMapping("/rooms/{code}")
    fun cancelRoom(
        @CurrentUserId userId: Long,
        @PathVariable code: String
    ): ResponseEntity<RoomCancelResponse> =
        ResponseEntity.ok(
            battleApplicationService.cancelRoom(
                userId = userId,
                code = code
            )
        )

    @PostMapping("/rooms/{code}/join")
    fun joinRoom(
        @CurrentUserId userId: Long,
        @PathVariable code: String,
        @Valid @RequestBody request: RandomQueueRequest
    ): ResponseEntity<RandomQueueResponse> =
        ResponseEntity.ok(
            battleApplicationService.joinRoom(
                userId = userId,
                code = code,
                creatureId = request.creatureId
            )
        )

    @GetMapping("/{id}")
    fun getBattle(
        @CurrentUserId userId: Long,
        @PathVariable id: Long
    ): ResponseEntity<BattleDetailView> =
        ResponseEntity.ok(battleApplicationService.getBattle(userId, id))
}

data class RandomQueueRequest(
    @field:Positive
    val creatureId: Long
)




