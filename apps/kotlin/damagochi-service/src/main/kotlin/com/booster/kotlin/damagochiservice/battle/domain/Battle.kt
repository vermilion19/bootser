package com.booster.kotlin.damagochiservice.battle.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "battles")
class Battle private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: BattleType,

    @Column(nullable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var endedAt: LocalDateTime? = null,

    @Column
    val seed: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var result: BattleResult = BattleResult.PENDING
) {
    fun complete(now: LocalDateTime = LocalDateTime.now()) {
        this.result = BattleResult.COMPLETED
        this.endedAt = now
    }

    companion object {
        fun create(type: BattleType, seed: Long?): Battle =
            Battle(type = type, seed = seed)
    }
}



