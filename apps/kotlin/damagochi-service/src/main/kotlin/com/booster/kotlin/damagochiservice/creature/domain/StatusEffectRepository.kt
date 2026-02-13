package com.booster.kotlin.damagochiservice.creature.domain

import org.springframework.data.jpa.repository.JpaRepository

interface StatusEffectRepository : JpaRepository<StatusEffect, Long> {
    fun findAllByCreatureId(creatureId: Long): List<StatusEffect>
}



