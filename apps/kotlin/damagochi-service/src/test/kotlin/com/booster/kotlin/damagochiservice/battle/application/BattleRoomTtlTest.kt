package com.booster.kotlin.damagochiservice.battle.application

import com.booster.kotlin.damagochiservice.creature.application.CreatureApplicationService
import com.booster.kotlin.damagochiservice.creature.application.CreateCreatureCommand
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "damagochi.balance.battle.room-ttl-minutes=0"
    ]
)
class BattleRoomTtlTest @Autowired constructor(
    private val creatureApplicationService: CreatureApplicationService,
    private val battleApplicationService: BattleApplicationService
) {
    @Test
    fun `expired room cannot be joined`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 5001L, name = "owner-ttl", species = "cat")
        )
        val joinerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 5002L, name = "joiner-ttl", species = "fox")
        )

        val room = battleApplicationService.createRoom(5001L, ownerCreature.id)

        assertThatThrownBy {
            battleApplicationService.joinRoom(5002L, room.code, joinerCreature.id)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Room not found")
    }
}



