package com.booster.kotlin.damagochiservice.battle.application

import com.booster.kotlin.damagochiservice.battle.domain.BattleOutcome
import com.booster.kotlin.damagochiservice.common.config.BattlePolicy
import com.booster.kotlin.damagochiservice.creature.domain.CreatureState
import kotlin.random.Random

object BattleEngine {
    fun duel(
        seed: Long,
        leftCreatureId: Long,
        left: CreatureState,
        rightCreatureId: Long,
        right: CreatureState,
        policy: BattlePolicy
    ): BattleDuelResult {
        val leftScore = score(seed, leftCreatureId, left, policy)
        val rightScore = score(seed, rightCreatureId, right, policy)

        val leftOutcome: BattleOutcome
        val rightOutcome: BattleOutcome
        if (leftScore > rightScore) {
            leftOutcome = BattleOutcome.WIN
            rightOutcome = BattleOutcome.LOSE
        } else if (leftScore < rightScore) {
            leftOutcome = BattleOutcome.LOSE
            rightOutcome = BattleOutcome.WIN
        } else {
            leftOutcome = BattleOutcome.DRAW
            rightOutcome = BattleOutcome.DRAW
        }

        return BattleDuelResult(
            leftScore = leftScore,
            rightScore = rightScore,
            leftOutcome = leftOutcome,
            rightOutcome = rightOutcome
        )
    }

    private fun score(seed: Long, creatureId: Long, state: CreatureState, policy: BattlePolicy): Int {
        val base = state.conditionScore * policy.baseConditionWeight +
            state.health * policy.baseHealthWeight +
            state.hunger * policy.baseHungerWeight +
            state.winRate * policy.baseWinRateWeight
        val random = Random(seed xor creatureId)
            .nextInt(policy.randomScoreMinInclusive, policy.randomScoreMaxExclusive)
        return base + random
    }
}

data class BattleDuelResult(
    val leftScore: Int,
    val rightScore: Int,
    val leftOutcome: BattleOutcome,
    val rightOutcome: BattleOutcome
)




