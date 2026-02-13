package com.booster.kotlin.damagochiservice.creature.application

import com.booster.kotlin.damagochiservice.common.config.StatePolicy
import com.booster.kotlin.damagochiservice.creature.domain.CreatureState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CreatureStateCalculatorTest {
    private val policy = StatePolicy()

    @Test
    fun `awake state decreases hunger and health over time`() {
        val base = LocalDateTime.of(2026, 2, 13, 12, 0)
        val state = CreatureState.initial(creatureId = 1L).apply {
            hunger = 100
            health = 100
            conditionScore = 100
            sleeping = false
            updatedAt = base
        }

        CreatureStateCalculator.resolve(state, base.plusMinutes(60), policy)

        assertThat(state.hunger).isEqualTo(98)
        assertThat(state.health).isEqualTo(99)
        assertThat(state.age).isEqualTo(1)
    }

    @Test
    fun `sleeping state only increases age`() {
        val base = LocalDateTime.of(2026, 2, 13, 1, 0)
        val state = CreatureState.initial(creatureId = 1L).apply {
            hunger = 50
            health = 50
            conditionScore = 50
            sleeping = true
            updatedAt = base
        }

        CreatureStateCalculator.resolve(state, base.plusMinutes(60), policy)

        assertThat(state.hunger).isEqualTo(50)
        assertThat(state.health).isEqualTo(50)
        assertThat(state.conditionScore).isEqualTo(50)
        assertThat(state.age).isEqualTo(2)
    }

    @Test
    fun `night penalty decreases condition even when metrics are positive`() {
        val base = LocalDateTime.of(2026, 2, 13, 23, 0)
        val state = CreatureState.initial(creatureId = 2L).apply {
            hunger = 100
            health = 100
            conditionScore = 100
            sleeping = false
            updatedAt = base
        }

        CreatureStateCalculator.resolve(state, base.plusMinutes(60), policy)

        assertThat(state.conditionScore).isEqualTo(98)
    }

    @Test
    fun `zero health and hunger cause accelerated condition decay`() {
        val base = LocalDateTime.of(2026, 2, 13, 12, 0)
        val state = CreatureState.initial(creatureId = 3L).apply {
            hunger = 0
            health = 0
            conditionScore = 100
            sleeping = false
            updatedAt = base
        }

        CreatureStateCalculator.resolve(state, base.plusMinutes(60), policy)

        assertThat(state.conditionScore).isEqualTo(94)
    }
}




