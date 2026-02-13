package com.booster.kotlin.damagochiservice.battle.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(
    name = "battle_participants",
    indexes = [Index(name = "idx_battle_participant_battle_id", columnList = "battle_id")]
)
class BattleParticipant private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "battle_id", nullable = false)
    val battleId: Long,

    @Column(name = "creature_id", nullable = false)
    val creatureId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    val snapshotJson: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var outcome: BattleOutcome
) {
    companion object {
        fun create(
            battleId: Long,
            creatureId: Long,
            userId: Long,
            snapshotJson: String,
            outcome: BattleOutcome
        ): BattleParticipant = BattleParticipant(
            battleId = battleId,
            creatureId = creatureId,
            userId = userId,
            snapshotJson = snapshotJson,
            outcome = outcome
        )
    }
}



