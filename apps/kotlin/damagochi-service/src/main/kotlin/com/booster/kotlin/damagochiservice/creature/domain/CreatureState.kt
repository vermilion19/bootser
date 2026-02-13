package com.booster.kotlin.damagochiservice.creature.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "creature_states")
class CreatureState private constructor(
    @Id
    @Column(name = "creature_id")
    val creatureId: Long,

    @Column(nullable = false)
    var age: Long = 0,

    @Column(nullable = false)
    var health: Int = 100,

    @Column(nullable = false)
    var hunger: Int = 100,

    @Column(name = "condition_score", nullable = false)
    var conditionScore: Int = 100,

    @Column(name = "win_rate", nullable = false)
    var winRate: Int = 0,

    @Column(nullable = false)
    var sleeping: Boolean = false,

    @Column
    var sleepingSince: LocalDateTime? = null,

    @Column
    var lastSleepToggleAt: LocalDateTime? = null,

    @Column
    var lastFedAt: LocalDateTime? = null,

    @Column
    var lastTreatedAt: LocalDateTime? = null,

    @Column(name = "effort_points", nullable = false)
    var effortPoints: Int = 0,

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val effortSlots: Int get() = effortPoints / TRAININGS_PER_SLOT

    fun train() {
        require(effortPoints < MAX_EFFORT_POINTS) {
            "Effort is already at maximum. effortPoints=$effortPoints"
        }
        this.effortPoints++
    }

    fun resetEffort() {
        this.effortPoints = 0
    }

    fun touch(now: LocalDateTime = LocalDateTime.now()) {
        this.updatedAt = now
    }

    companion object {
        const val MAX_SLOTS = 4
        const val TRAININGS_PER_SLOT = 4
        const val MAX_EFFORT_POINTS = MAX_SLOTS * TRAININGS_PER_SLOT

        fun initial(creatureId: Long): CreatureState =
            CreatureState(creatureId = creatureId)
    }
}



