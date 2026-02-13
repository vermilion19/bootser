package com.booster.kotlin.damagochiservice.battle.application

import com.booster.kotlin.damagochiservice.activity.domain.ActionLog
import com.booster.kotlin.damagochiservice.activity.domain.ActionLogRepository
import com.booster.kotlin.damagochiservice.activity.domain.ActionType
import com.booster.kotlin.damagochiservice.battle.application.match.BattleMatchRegistry
import com.booster.kotlin.damagochiservice.battle.application.match.MatchQueueEntry
import com.booster.kotlin.damagochiservice.battle.application.match.RandomQueueRegistryResult
import com.booster.kotlin.damagochiservice.battle.application.match.RoomSlot
import com.booster.kotlin.damagochiservice.battle.domain.Battle
import com.booster.kotlin.damagochiservice.battle.domain.BattleOutcome
import com.booster.kotlin.damagochiservice.battle.domain.BattleParticipant
import com.booster.kotlin.damagochiservice.battle.domain.BattleParticipantRepository
import com.booster.kotlin.damagochiservice.battle.domain.BattleRepository
import com.booster.kotlin.damagochiservice.battle.domain.BattleType
import com.booster.kotlin.damagochiservice.common.config.GameBalanceProperties
import com.booster.kotlin.damagochiservice.creature.application.CreatureStateCalculator
import com.booster.kotlin.damagochiservice.creature.domain.CreatureRepository
import com.booster.kotlin.damagochiservice.creature.domain.CreatureState
import com.booster.kotlin.damagochiservice.creature.domain.CreatureStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random

@Service
@Transactional(readOnly = true)
class BattleApplicationService(
    private val creatureRepository: CreatureRepository,
    private val creatureStateRepository: CreatureStateRepository,
    private val battleRepository: BattleRepository,
    private val battleParticipantRepository: BattleParticipantRepository,
    private val actionLogRepository: ActionLogRepository,
    private val gameBalanceProperties: GameBalanceProperties,
    private val matchRegistry: BattleMatchRegistry,
    private val clock: Clock
) {
    @Transactional
    fun queueRandom(userId: Long, creatureId: Long): RandomQueueResponse {
        val now = now()
        if (matchRegistry.findRoomByOwner(userId, now) != null) {
            throw IllegalStateException("Room owner cannot join random queue. userId=$userId")
        }

        val myEntry = resolveBattleEntry(userId, creatureId)
        return when (val outcome = matchRegistry.enqueueRandom(myEntry)) {
            is RandomQueueRegistryResult.Waiting -> {
                actionLogRepository.save(
                    ActionLog.create(
                        userId = userId,
                        creatureId = creatureId,
                        actionType = ActionType.BATTLE_QUEUE
                    )
                )
                RandomQueueResponse(status = "WAITING", ticket = outcome.entry.ticket)
            }
            is RandomQueueRegistryResult.Matched -> {
                resolveBattle(BattleType.RANDOM, outcome.me, outcome.opponent, now)
            }
        }
    }

    @Transactional
    fun createRoom(userId: Long, creatureId: Long): RoomCreateResponse {
        val now = now()
        matchRegistry.removeFromRandomQueue(userId)

        val existing = matchRegistry.findRoomByOwner(userId, now)
        if (existing != null) {
            return RoomCreateResponse(code = existing.code, ticket = existing.owner.ticket)
        }

        val owner = resolveBattleEntry(userId, creatureId)
        val code = generateUniqueRoomCode(matchRegistry.getLastIssuedRoomCode(userId), now)
        val ttl = gameBalanceProperties.battle.roomTtlMinutes
        val room = RoomSlot(
            code = code,
            owner = owner,
            createdAt = now,
            expiresAt = now.plusMinutes(ttl)
        )
        val saved = matchRegistry.saveRoom(room)
        if (!saved) {
            // very rare collision in distributed mode
            return createRoom(userId, creatureId)
        }

        matchRegistry.setLastIssuedRoomCode(userId, code)
        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = creatureId,
                actionType = ActionType.BATTLE_QUEUE,
                payloadJson = """{"mode":"ROOM","code":"$code"}"""
            )
        )
        return RoomCreateResponse(code = code, ticket = owner.ticket)
    }

    fun getMyRooms(userId: Long): RoomListResponse {
        val now = now()
        val rooms = matchRegistry.listRoomsByOwner(userId, now).map {
            RoomSummary(
                code = it.code,
                ownerCreatureId = it.owner.creatureId,
                createdAt = it.createdAt.toString(),
                expiresAt = it.expiresAt.toString(),
                remainingSeconds = Duration.between(now, it.expiresAt).seconds.coerceAtLeast(0)
            )
        }
        return RoomListResponse(rooms = rooms)
    }

    fun getRoom(userId: Long, code: String): RoomInfoResponse {
        val now = now()
        val normalized = code.trim().uppercase(Locale.ROOT)
        val room = matchRegistry.findRoomByCode(normalized, now)
            ?: throw IllegalArgumentException("Room not found. code=$normalized")

        return RoomInfoResponse(
            code = room.code,
            ownerUserId = room.owner.userId,
            ownerCreatureId = room.owner.creatureId,
            createdAt = room.createdAt.toString(),
            expiresAt = room.expiresAt.toString(),
            remainingSeconds = Duration.between(now, room.expiresAt).seconds.coerceAtLeast(0),
            status = if (room.owner.userId == userId) "OPEN_OWNER" else "OPEN"
        )
    }

    @Transactional
    fun cancelRoom(userId: Long, code: String): RoomCancelResponse {
        val normalized = code.trim().uppercase(Locale.ROOT)
        val canceled = matchRegistry.cancelRoom(userId, normalized, now())
        require(canceled) { "Room not found or cancel not allowed. code=$normalized" }
        return RoomCancelResponse(code = normalized, canceled = true)
    }

    @Transactional
    fun joinRoom(userId: Long, code: String, creatureId: Long): RandomQueueResponse {
        val joiner = resolveBattleEntry(userId, creatureId)
        val normalized = code.trim().uppercase(Locale.ROOT)
        val now = now()
        val room = matchRegistry.takeRoomForJoin(normalized, now)
            ?: throw IllegalArgumentException("Room not found. code=$normalized")
        require(room.owner.userId != userId) { "You cannot join your own room. code=$normalized" }

        matchRegistry.removeFromRandomQueue(userId)
        matchRegistry.removeFromRandomQueue(room.owner.userId)
        return resolveBattle(BattleType.ROOM, joiner, room.owner, now, normalized)
    }

    fun getBattle(userId: Long, battleId: Long): BattleDetailView {
        val battle = battleRepository.findById(battleId)
            .orElseThrow { IllegalArgumentException("Battle not found. battleId=$battleId") }
        val myParticipant = battleParticipantRepository.findByBattleIdAndUserId(battleId, userId)
            ?: throw IllegalArgumentException("Battle is not accessible. battleId=$battleId userId=$userId")

        val participants = battleParticipantRepository.findAllByBattleId(battleId).map {
            BattleParticipantView(
                userId = it.userId,
                creatureId = it.creatureId,
                outcome = it.outcome.name,
                snapshotJson = it.snapshotJson
            )
        }

        return BattleDetailView(
            battleId = battle.id,
            type = battle.type.name,
            result = battle.result.name,
            seed = battle.seed,
            startedAt = battle.startedAt.toString(),
            endedAt = battle.endedAt?.toString(),
            myOutcome = myParticipant.outcome.name,
            participants = participants
        )
    }

    private fun resolveBattle(
        battleType: BattleType,
        rightEntry: MatchQueueEntry,
        leftEntry: MatchQueueEntry,
        now: LocalDateTime,
        roomCode: String? = null
    ): RandomQueueResponse {
        val leftState = loadResolvedState(leftEntry.creatureId, now)
        val rightState = loadResolvedState(rightEntry.creatureId, now)
        val seed = buildSeed(now, rightEntry.creatureId, leftEntry.creatureId)
        val duel = BattleEngine.duel(
            seed = seed,
            leftCreatureId = leftEntry.creatureId,
            left = leftState,
            rightCreatureId = rightEntry.creatureId,
            right = rightState,
            policy = gameBalanceProperties.battle
        )

        val battle = battleRepository.save(Battle.create(battleType, seed))
        battle.complete(now)

        battleParticipantRepository.save(
            BattleParticipant.create(
                battleId = battle.id,
                creatureId = leftEntry.creatureId,
                userId = leftEntry.userId,
                snapshotJson = snapshotJson(leftState, duel.leftScore),
                outcome = duel.leftOutcome
            )
        )
        battleParticipantRepository.save(
            BattleParticipant.create(
                battleId = battle.id,
                creatureId = rightEntry.creatureId,
                userId = rightEntry.userId,
                snapshotJson = snapshotJson(rightState, duel.rightScore),
                outcome = duel.rightOutcome
            )
        )

        updateWinRate(leftState, duel.leftOutcome, now)
        updateWinRate(rightState, duel.rightOutcome, now)
        creatureStateRepository.save(leftState)
        creatureStateRepository.save(rightState)

        actionLogRepository.save(
            ActionLog.create(
                userId = leftEntry.userId,
                creatureId = leftEntry.creatureId,
                actionType = ActionType.BATTLE_RESULT,
                payloadJson = """{"battleId":${battle.id},"type":"${battleType.name}","outcome":"${duel.leftOutcome}","roomCode":${if (roomCode == null) "null" else "\"$roomCode\""}}"""
            )
        )
        actionLogRepository.save(
            ActionLog.create(
                userId = rightEntry.userId,
                creatureId = rightEntry.creatureId,
                actionType = ActionType.BATTLE_RESULT,
                payloadJson = """{"battleId":${battle.id},"type":"${battleType.name}","outcome":"${duel.rightOutcome}","roomCode":${if (roomCode == null) "null" else "\"$roomCode\""}}"""
            )
        )

        return RandomQueueResponse(status = "MATCHED", ticket = rightEntry.ticket, battleId = battle.id)
    }

    private fun resolveBattleEntry(userId: Long, creatureId: Long): MatchQueueEntry {
        val creature = creatureRepository.findByIdAndUserId(creatureId, userId)
            ?: throw IllegalArgumentException("Creature not found. creatureId=$creatureId userId=$userId")
        require(creature.isActive) { "Only active creature can join battle. creatureId=$creatureId" }
        return MatchQueueEntry(
            ticket = matchRegistry.nextTicket(),
            userId = userId,
            creatureId = creature.id
        )
    }

    private fun loadResolvedState(creatureId: Long, now: LocalDateTime): CreatureState {
        val state = creatureStateRepository.findById(creatureId)
            .orElseThrow { IllegalArgumentException("CreatureState not found. creatureId=$creatureId") }
        val resolved = CreatureStateCalculator.resolve(state, now, gameBalanceProperties.state)
        return creatureStateRepository.save(resolved)
    }

    private fun updateWinRate(state: CreatureState, outcome: BattleOutcome, now: LocalDateTime) {
        val delta = when (outcome) {
            BattleOutcome.WIN -> gameBalanceProperties.battle.winRateDeltaWin
            BattleOutcome.LOSE -> gameBalanceProperties.battle.winRateDeltaLose
            BattleOutcome.DRAW -> gameBalanceProperties.battle.winRateDeltaDraw
        }
        state.winRate = (state.winRate + delta).coerceIn(0, 100)
        state.updatedAt = now
    }

    private fun snapshotJson(state: CreatureState, score: Int): String =
        """{"age":${state.age},"health":${state.health},"hunger":${state.hunger},"conditionScore":${state.conditionScore},"winRate":${state.winRate},"score":$score}"""

    private fun generateUniqueRoomCode(disallowCode: String?, now: LocalDateTime): String {
        val chars = gameBalanceProperties.battle.roomCodeChars
        val codeLength = gameBalanceProperties.battle.roomCodeLength
        repeat(gameBalanceProperties.battle.roomCodeGenerateAttempts) {
            val code = buildString(codeLength) {
                repeat(codeLength) {
                    append(chars[Random.nextInt(chars.length)])
                }
            }
            if (code == disallowCode) return@repeat
            if (matchRegistry.findRoomByCode(code, now) == null) {
                return code
            }
        }
        throw IllegalStateException("Failed to generate unique room code")
    }

    private fun buildSeed(now: LocalDateTime, creatureId1: Long, creatureId2: Long): Long =
        now.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() xor creatureId1 xor creatureId2

    private fun now(): LocalDateTime = LocalDateTime.now(clock)
}

data class RandomQueueResponse(
    val status: String,
    val ticket: Long,
    val battleId: Long? = null
)

data class RoomCreateResponse(
    val code: String,
    val ticket: Long
)

data class RoomInfoResponse(
    val code: String,
    val ownerUserId: Long,
    val ownerCreatureId: Long,
    val createdAt: String,
    val expiresAt: String,
    val remainingSeconds: Long,
    val status: String
)

data class RoomCancelResponse(
    val code: String,
    val canceled: Boolean
)

data class RoomListResponse(
    val rooms: List<RoomSummary>
)

data class RoomSummary(
    val code: String,
    val ownerCreatureId: Long,
    val createdAt: String,
    val expiresAt: String,
    val remainingSeconds: Long
)

data class BattleDetailView(
    val battleId: Long,
    val type: String,
    val result: String,
    val seed: Long?,
    val startedAt: String,
    val endedAt: String?,
    val myOutcome: String,
    val participants: List<BattleParticipantView>
)

data class BattleParticipantView(
    val userId: Long,
    val creatureId: Long,
    val outcome: String,
    val snapshotJson: String
)




