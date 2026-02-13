package com.booster.kotlin.damagochiservice.battle.application

import com.booster.kotlin.damagochiservice.common.config.BattlePolicy
import com.booster.kotlin.damagochiservice.creature.domain.CreatureState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BattleEngineTest {
    private val policy = BattlePolicy()

    @Test
    fun `same seed and same snapshots produce same duel result`() {
        val left = CreatureState.initial(1L).apply {
            health = 80
            hunger = 70
            conditionScore = 90
            winRate = 50
        }
        val right = CreatureState.initial(2L).apply {
            health = 82
            hunger = 68
            conditionScore = 89
            winRate = 49
        }

        val result1 = BattleEngine.duel(12345L, 1L, left, 2L, right, policy)
        val result2 = BattleEngine.duel(12345L, 1L, left, 2L, right, policy)

        assertThat(result1).isEqualTo(result2)
    }
}




