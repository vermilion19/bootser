package com.booster.kotlin.damagochiservice.creature.domain

import org.springframework.data.jpa.repository.JpaRepository

interface CreatureRepository : JpaRepository<Creature, Long> {
    fun findAllByUserIdOrderByIdAsc(userId: Long): List<Creature>
    fun findByIdAndUserId(id: Long, userId: Long): Creature?
    fun countByUserId(userId: Long): Long
    fun findByUserIdAndIsActiveTrue(userId: Long): Creature?
    fun findTopByUserIdAndLastActivatedAtIsNotNullOrderByLastActivatedAtDesc(userId: Long): Creature?
}



