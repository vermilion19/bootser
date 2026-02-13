package com.booster.kotlin.damagochiservice.creature.application

import com.booster.kotlin.damagochiservice.creature.domain.EffectSeverity
import com.booster.kotlin.damagochiservice.creature.domain.CreatureRepository
import com.booster.kotlin.damagochiservice.creature.domain.StatusEffect
import com.booster.kotlin.damagochiservice.creature.domain.StatusEffectRepository
import com.booster.kotlin.damagochiservice.creature.domain.StatusEffectType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CreatureApplicationServiceTest @Autowired constructor(
    private val creatureApplicationService: CreatureApplicationService,
    private val creatureRepository: CreatureRepository,
    private val statusEffectRepository: StatusEffectRepository
) {
    @Test
    fun `first creature becomes active and second becomes backup`() {
        val userId = 100L

        val first = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "alpha", species = "cat")
        )
        val second = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "beta", species = "fox")
        )

        assertThat(first.active).isTrue()
        assertThat(second.active).isFalse()
        assertThat(creatureRepository.findAllByUserIdOrderByIdAsc(userId)).hasSize(2)
    }

    @Test
    fun `cannot create more than three creatures per user`() {
        val userId = 200L

        creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "a", species = "cat")
        )
        creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "b", species = "dog")
        )
        creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "c", species = "owl")
        )

        assertThatThrownBy {
            creatureApplicationService.createCreature(
                CreateCreatureCommand(userId = userId, name = "d", species = "fox")
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `activate has cooldown policy`() {
        val userId = 300L

        creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "active", species = "cat")
        )
        val backup = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "backup", species = "fox")
        )

        assertThatThrownBy {
            creatureApplicationService.activateCreature(userId, backup.id)
        }.isInstanceOf(ActivationCooldownException::class.java)
    }

    @Test
    fun `sleep toggle has cooldown policy`() {
        val userId = 400L
        val creature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "sleepy", species = "cat")
        )

        creatureApplicationService.updateSleep(userId, creature.id, true)

        assertThatThrownBy {
            creatureApplicationService.updateSleep(userId, creature.id, false)
        }.isInstanceOf(SleepToggleCooldownException::class.java)
    }

    @Test
    fun `treat downgrades status effect severity instead of deleting all`() {
        val userId = 500L
        val creature = creatureApplicationService.createCreature(
            CreateCreatureCommand(userId = userId, name = "injured", species = "fox")
        )
        statusEffectRepository.save(
            StatusEffect.create(
                creatureId = creature.id,
                type = StatusEffectType.INJURY,
                severity = EffectSeverity.HIGH
            )
        )

        creatureApplicationService.treat(userId, creature.id)
        val effectsAfterFirst = statusEffectRepository.findAllByCreatureId(creature.id)
        assertThat(effectsAfterFirst).hasSize(1)
        assertThat(effectsAfterFirst.first().severity).isEqualTo(EffectSeverity.MEDIUM)

        creatureApplicationService.treat(userId, creature.id)
        val effectsAfterSecond = statusEffectRepository.findAllByCreatureId(creature.id)
        assertThat(effectsAfterSecond.first().severity).isEqualTo(EffectSeverity.LOW)

        creatureApplicationService.treat(userId, creature.id)
        val effectsAfterThird = statusEffectRepository.findAllByCreatureId(creature.id)
        assertThat(effectsAfterThird).isEmpty()
    }
}




