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

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun touch(now: LocalDateTime = LocalDateTime.now()) {
        this.updatedAt = now
    }

    companion object {
        fun initial(creatureId: Long): CreatureState =
            CreatureState(creatureId = creatureId)
    }
}



