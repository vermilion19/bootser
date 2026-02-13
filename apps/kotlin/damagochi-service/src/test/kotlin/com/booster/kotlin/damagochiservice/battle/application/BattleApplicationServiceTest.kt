package com.booster.kotlin.damagochiservice.battle.application

import com.booster.kotlin.damagochiservice.creature.application.CreatureApplicationService
import com.booster.kotlin.damagochiservice.creature.application.CreateCreatureCommand
import com.booster.kotlin.damagochiservice.creature.domain.CreatureStateRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BattleApplicationServiceTest @Autowired constructor(
    private val creatureApplicationService: CreatureApplicationService,
    private val battleApplicationService: BattleApplicationService,
    private val creatureStateRepository: CreatureStateRepository
) {
    @Test
    fun `random queue matches second participant and creates battle`() {
        val c1 = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 1001L, name = "u1", species = "cat")
        )
        val c2 = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 1002L, name = "u2", species = "fox")
        )

        val firstQueue = battleApplicationService.queueRandom(1001L, c1.id)
        val secondQueue = battleApplicationService.queueRandom(1002L, c2.id)

        assertThat(firstQueue.status).isEqualTo("WAITING")
        assertThat(secondQueue.status).isEqualTo("MATCHED")
        assertThat(secondQueue.battleId).isNotNull()

        val battle = battleApplicationService.getBattle(1002L, secondQueue.battleId!!)
        assertThat(battle.participants).hasSize(2)

        val s1 = creatureStateRepository.findById(c1.id).orElseThrow()
        val s2 = creatureStateRepository.findById(c2.id).orElseThrow()
        assertThat(s1.winRate).isBetween(0, 100)
        assertThat(s2.winRate).isBetween(0, 100)
    }

    @Test
    fun `random queue does not enqueue duplicate user`() {
        val c1 = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 3001L, name = "solo", species = "cat")
        )

        val first = battleApplicationService.queueRandom(3001L, c1.id)
        val second = battleApplicationService.queueRandom(3001L, c1.id)

        assertThat(first.status).isEqualTo("WAITING")
        assertThat(second.status).isEqualTo("WAITING")
        assertThat(second.ticket).isEqualTo(first.ticket)
    }

    @Test
    fun `room flow creates code and joins to create battle`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 2001L, name = "owner", species = "cat")
        )
        val joinerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 2002L, name = "joiner", species = "fox")
        )

        val room = battleApplicationService.createRoom(2001L, ownerCreature.id)
        val joined = battleApplicationService.joinRoom(2002L, room.code, joinerCreature.id)

        assertThat(room.code).hasSize(6)
        assertThat(joined.status).isEqualTo("MATCHED")
        assertThat(joined.battleId).isNotNull()

        val ownerView = battleApplicationService.getBattle(2001L, joined.battleId!!)
        val joinerView = battleApplicationService.getBattle(2002L, joined.battleId!!)
        assertThat(ownerView.type).isEqualTo("ROOM")
        assertThat(joinerView.participants).hasSize(2)
    }

    @Test
    fun `room creation is idempotent per user while room is open`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 4001L, name = "owner2", species = "cat")
        )

        val room1 = battleApplicationService.createRoom(4001L, ownerCreature.id)
        val room2 = battleApplicationService.createRoom(4001L, ownerCreature.id)

        assertThat(room2.code).isEqualTo(room1.code)
        assertThat(room2.ticket).isEqualTo(room1.ticket)
    }

    @Test
    fun `room owner can query and cancel room`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 6001L, name = "owner3", species = "cat")
        )
        val room = battleApplicationService.createRoom(6001L, ownerCreature.id)

        val roomInfo = battleApplicationService.getRoom(6001L, room.code)
        assertThat(roomInfo.code).isEqualTo(room.code)
        assertThat(roomInfo.status).isEqualTo("OPEN_OWNER")
        assertThat(roomInfo.remainingSeconds).isGreaterThan(0)

        val canceled = battleApplicationService.cancelRoom(6001L, room.code)
        assertThat(canceled.canceled).isTrue()

        assertThatThrownBy {
            battleApplicationService.getRoom(6001L, room.code)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `owner can list my rooms`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 6501L, name = "owner-list", species = "cat")
        )
        val room = battleApplicationService.createRoom(6501L, ownerCreature.id)

        val mine = battleApplicationService.getMyRooms(6501L)

        assertThat(mine.rooms).hasSize(1)
        assertThat(mine.rooms.first().code).isEqualTo(room.code)
        assertThat(mine.rooms.first().remainingSeconds).isGreaterThan(0)
    }

    @Test
    fun `non owner cannot cancel room`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 7001L, name = "owner4", species = "cat")
        )
        val room = battleApplicationService.createRoom(7001L, ownerCreature.id)

        assertThatThrownBy {
            battleApplicationService.cancelRoom(7002L, room.code)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `new room code is reissued after cancel`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 8001L, name = "owner5", species = "cat")
        )
        val room1 = battleApplicationService.createRoom(8001L, ownerCreature.id)
        battleApplicationService.cancelRoom(8001L, room1.code)
        val room2 = battleApplicationService.createRoom(8001L, ownerCreature.id)

        assertThat(room2.code).isNotEqualTo(room1.code)
    }

    @Test
    fun `room owner cannot join random queue`() {
        val ownerCreature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = 9001L, name = "owner6", species = "cat")
        )
        battleApplicationService.createRoom(9001L, ownerCreature.id)

        assertThatThrownBy {
            battleApplicationService.queueRandom(9001L, ownerCreature.id)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Room owner cannot join random queue")
    }
}




