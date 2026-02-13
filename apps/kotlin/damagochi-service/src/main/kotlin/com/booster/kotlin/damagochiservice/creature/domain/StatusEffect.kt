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
    name = "status_effects",
    indexes = [Index(name = "idx_status_effect_creature_id", columnList = "creature_id")]
)
class StatusEffect private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "creature_id", nullable = false)
    val creatureId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: StatusEffectType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var severity: EffectSeverity,

    @Column(nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun updateSeverity(severity: EffectSeverity, now: LocalDateTime = LocalDateTime.now()) {
        this.severity = severity
        this.updatedAt = now
    }

    fun downgrade(now: LocalDateTime = LocalDateTime.now()): Boolean {
        return when (severity) {
            EffectSeverity.HIGH -> {
                updateSeverity(EffectSeverity.MEDIUM, now)
                false
            }
            EffectSeverity.MEDIUM -> {
                updateSeverity(EffectSeverity.LOW, now)
                false
            }
            EffectSeverity.LOW -> true
        }
    }

    companion object {
        fun create(
            creatureId: Long,
            type: StatusEffectType,
            severity: EffectSeverity
        ): StatusEffect = StatusEffect(
            creatureId = creatureId,
            type = type,
            severity = severity
        )
    }
}



