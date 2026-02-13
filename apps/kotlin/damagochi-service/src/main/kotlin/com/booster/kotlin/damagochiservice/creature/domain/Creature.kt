package com.booster.kotlin.damagochiservice.creature.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "creatures",
    indexes = [
        Index(name = "idx_creature_user_id", columnList = "user_id"),
        Index(name = "idx_creature_user_active", columnList = "user_id,is_active")
    ]
)
class Creature private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, length = 50)
    var species: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var stage: CreatureStage = CreatureStage.initial(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false,

    @Column(name = "last_activated_at")
    var lastActivatedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun activate(now: LocalDateTime = LocalDateTime.now()) {
        this.isActive = true
        this.lastActivatedAt = now
    }

    fun deactivate() {
        this.isActive = false
    }

    val level: Int get() = stage.level

    fun ageDays(now: LocalDateTime): Long =
        java.time.Duration.between(createdAt, now).toDays().coerceAtLeast(0)

    fun evolve(nextStage: CreatureStage) {
        require(nextStage.level == this.stage.level + 1) {
            "Can only evolve to the next stage. current=${this.stage}, requested=$nextStage"
        }
        this.stage = nextStage
    }

    companion object {
        fun create(
            userId: Long,
            name: String,
            species: String,
            isActive: Boolean,
            now: LocalDateTime = LocalDateTime.now()
        ): Creature = Creature(
            userId = userId,
            name = name,
            species = species,
            isActive = isActive,
            lastActivatedAt = if (isActive) now else null
        )
    }
}



