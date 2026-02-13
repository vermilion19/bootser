package com.booster.kotlin.damagochiservice.creature.application

import com.booster.kotlin.damagochiservice.activity.domain.ActionLog
import com.booster.kotlin.damagochiservice.activity.domain.ActionLogRepository
import com.booster.kotlin.damagochiservice.activity.domain.ActionType
import com.booster.kotlin.damagochiservice.common.config.GameBalanceProperties
import com.booster.kotlin.damagochiservice.creature.domain.Creature
import com.booster.kotlin.damagochiservice.creature.domain.CreatureRepository
import com.booster.kotlin.damagochiservice.creature.domain.CreatureState
import com.booster.kotlin.damagochiservice.creature.domain.CreatureStateRepository
import com.booster.kotlin.damagochiservice.creature.domain.StatusEffectRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CreatureApplicationService(
    private val creatureRepository: CreatureRepository,
    private val creatureStateRepository: CreatureStateRepository,
    private val statusEffectRepository: StatusEffectRepository,
    private val actionLogRepository: ActionLogRepository,
    private val clock: Clock,
    private val gameBalanceProperties: GameBalanceProperties
) {
    fun findCreatures(userId: Long): List<CreatureSummary> {
        val now = now()
        return creatureRepository.findAllByUserIdOrderByIdAsc(userId).map { creature ->
            val state = resolveAndPersistState(creature.id)
            CreatureSummary.from(creature, state, now)
        }
    }

    @Transactional
    fun createCreature(command: CreateCreatureCommand): CreatureSummary {
        val now = now()
        val count = creatureRepository.countByUserId(command.userId)
        require(count < gameBalanceProperties.creature.maxCreatureCountPerUser) {
            "Creature slot limit exceeded. userId=${command.userId}, count=$count"
        }

        val activeExists = creatureRepository.findByUserIdAndIsActiveTrue(command.userId) != null
        val creature = creatureRepository.save(
            Creature.create(
                userId = command.userId,
                name = command.name,
                species = command.species,
                isActive = !activeExists,
                now = now
            )
        )

        creatureStateRepository.save(CreatureState.initial(creature.id))
        actionLogRepository.save(
            ActionLog.create(
                userId = command.userId,
                creatureId = creature.id,
                actionType = ActionType.CREATE_CREATURE,
                payloadJson = """{"name":"${command.name}","species":"${command.species}"}"""
            )
        )
        return CreatureSummary.from(creature, creatureStateRepository.findById(creature.id).orElseThrow(), now)
    }

    @Transactional
    fun activateCreature(userId: Long, creatureId: Long): CreatureSummary {
        val now = now()
        val target = creatureRepository.findByIdAndUserId(creatureId, userId)
            ?: throw IllegalArgumentException("Creature not found. creatureId=$creatureId userId=$userId")
        val targetState = resolveAndPersistState(target.id)
        assertAwakeForAction(targetState, "activate")

        if (target.isActive) {
            return CreatureSummary.from(target, targetState, now)
        }

        val latestSwitch = creatureRepository
            .findTopByUserIdAndLastActivatedAtIsNotNullOrderByLastActivatedAtDesc(userId)
            ?.lastActivatedAt

        if (latestSwitch != null) {
            val availableAt = latestSwitch.plusMinutes(gameBalanceProperties.creature.activateCooldownMinutes)
            if (availableAt.isAfter(now)) {
                throw ActivationCooldownException(
                    availableAt = availableAt,
                    remainingSeconds = Duration.between(now, availableAt).seconds
                )
            }
        }

        creatureRepository.findByUserIdAndIsActiveTrue(userId)?.deactivate()
        target.activate(now)

        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = target.id,
                actionType = ActionType.ACTIVATE_CREATURE,
                payloadJson = """{"creatureId":${target.id}}"""
            )
        )
        return CreatureSummary.from(target, targetState, now)
    }

    @Transactional
    fun feed(userId: Long, creatureId: Long): CreatureSummary {
        val creature = findUserCreature(userId, creatureId)
        val state = resolveAndPersistState(creature.id)
        assertAwakeForAction(state, "feed")
        val now = now()
        state.hunger = (state.hunger + gameBalanceProperties.action.feedHungerGain).coerceAtMost(100)
        state.conditionScore = (state.conditionScore + gameBalanceProperties.action.feedConditionGain).coerceAtMost(100)
        state.health = (state.health + gameBalanceProperties.action.feedHealthGain).coerceAtMost(100)
        state.lastFedAt = now
        state.touch(now)

        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = creature.id,
                actionType = ActionType.FEED
            )
        )

        return CreatureSummary.from(creature, state, now)
    }

    @Transactional
    fun updateSleep(userId: Long, creatureId: Long, sleeping: Boolean): CreatureSummary {
        val creature = findUserCreature(userId, creatureId)
        val state = resolveAndPersistState(creature.id)
        val now = now()
        if (state.sleeping == sleeping) {
            throw IllegalStateException("Sleep state is already set to $sleeping")
        }

        val lastToggle = state.lastSleepToggleAt
        if (sleeping && lastToggle != null) {
            val availableAt = lastToggle.plusMinutes(gameBalanceProperties.action.sleepToggleCooldownMinutes)
            if (availableAt.isAfter(now)) {
                throw SleepToggleCooldownException(
                    availableAt = availableAt,
                    remainingSeconds = Duration.between(now, availableAt).seconds
                )
            }
        }

        state.sleeping = sleeping
        state.lastSleepToggleAt = now
        state.sleepingSince = if (sleeping) now else null
        state.touch(now)

        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = creature.id,
                actionType = if (sleeping) ActionType.SLEEP_START else ActionType.SLEEP_END
            )
        )

        return CreatureSummary.from(creature, state, now)
    }

    @Transactional
    fun evolve(userId: Long, creatureId: Long): CreatureSummary {
        val creature = findUserCreature(userId, creatureId)
        val state = resolveAndPersistState(creature.id)
        assertAwakeForAction(state, "evolve")
        val now = now()

        val nextStage = creature.stage.next()
            ?: throw IllegalStateException("Already at max stage. stage=${creature.stage}")

        creature.evolve(nextStage)
        state.resetEffort()
        state.touch(now)

        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = creature.id,
                actionType = ActionType.EVOLVE,
                payloadJson = """{"fromStage":"${creature.stage.name}","toStage":"${nextStage.name}"}"""
            )
        )

        return CreatureSummary.from(creature, state, now)
    }

    @Transactional
    fun train(userId: Long, creatureId: Long): CreatureSummary {
        val creature = findUserCreature(userId, creatureId)
        val state = resolveAndPersistState(creature.id)
        val now = now()

        assertAwakeForAction(state, "train")

        state.train()
        state.touch(now)

        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = creature.id,
                actionType = ActionType.TRAIN
            )
        )

        return CreatureSummary.from(creature, state, now)
    }

    @Transactional
    fun treat(userId: Long, creatureId: Long): CreatureSummary {
        val creature = findUserCreature(userId, creatureId)
        val state = resolveAndPersistState(creature.id)
        assertAwakeForAction(state, "treat")
        val now = now()
        state.health = (state.health + gameBalanceProperties.action.treatHealthGain).coerceAtMost(100)
        state.conditionScore = (state.conditionScore + gameBalanceProperties.action.treatConditionGain).coerceAtMost(100)
        state.lastTreatedAt = now
        state.touch(now)
        statusEffectRepository.findAllByCreatureId(creatureId)
            .forEach {
                val shouldDelete = it.downgrade(now)
                if (shouldDelete) {
                    statusEffectRepository.delete(it)
                }
            }

        actionLogRepository.save(
            ActionLog.create(
                userId = userId,
                creatureId = creature.id,
                actionType = ActionType.TREAT
            )
        )

        return CreatureSummary.from(creature, state, now)
    }

    private fun findUserCreature(userId: Long, creatureId: Long): Creature =
        creatureRepository.findByIdAndUserId(creatureId, userId)
            ?: throw IllegalArgumentException("Creature not found. creatureId=$creatureId userId=$userId")

    private fun resolveAndPersistState(creatureId: Long): CreatureState {
        val state = creatureStateRepository.findById(creatureId)
            .orElseThrow { IllegalArgumentException("CreatureState not found. creatureId=$creatureId") }
        val resolved = CreatureStateCalculator.resolve(state, now(), gameBalanceProperties.state)
        return creatureStateRepository.save(resolved)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun assertAwakeForAction(state: CreatureState, action: String) {
        require(!state.sleeping) { "Cannot $action while sleeping. Wake up first." }
    }

}

data class CreateCreatureCommand(
    val userId: Long,
    val name: String,
    val species: String
)

data class CreatureSummary(
    val id: Long,
    val userId: Long,
    val name: String,
    val species: String,
    val stage: String,
    val level: Int,
    val ageDays: Long,
    val active: Boolean,
    val state: CreatureStateView
) {
    companion object {
        fun from(creature: Creature, state: CreatureState, now: LocalDateTime): CreatureSummary =
            CreatureSummary(
                id = creature.id,
                userId = creature.userId,
                name = creature.name,
                species = creature.species,
                stage = creature.stage.name,
                level = creature.level,
                ageDays = creature.ageDays(now),
                active = creature.isActive,
                state = CreatureStateView(
                    age = state.age,
                    health = state.health,
                    hunger = state.hunger,
                    conditionScore = state.conditionScore,
                    winRate = state.winRate,
                    effortPoints = state.effortPoints,
                    effortSlots = state.effortSlots,
                    sleeping = state.sleeping,
                    sleepingSince = state.sleepingSince?.toString(),
                    lastSleepToggleAt = state.lastSleepToggleAt?.toString(),
                    lastFedAt = state.lastFedAt?.toString(),
                    lastTreatedAt = state.lastTreatedAt?.toString(),
                    updatedAt = state.updatedAt.toString()
                )
            )
    }
}

data class CreatureStateView(
    val age: Long,
    val health: Int,
    val hunger: Int,
    val conditionScore: Int,
    val winRate: Int,
    val effortPoints: Int,
    val effortSlots: Int,
    val sleeping: Boolean,
    val sleepingSince: String?,
    val lastSleepToggleAt: String?,
    val lastFedAt: String?,
    val lastTreatedAt: String?,
    val updatedAt: String
)



