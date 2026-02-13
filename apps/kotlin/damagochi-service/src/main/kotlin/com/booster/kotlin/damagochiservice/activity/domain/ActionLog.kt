package com.booster.kotlin.damagochiservice.activity.domain

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
import java.time.LocalDateTime

@Entity
@Table(
    name = "action_logs",
    indexes = [
        Index(name = "idx_action_log_user_id", columnList = "user_id"),
        Index(name = "idx_action_log_creature_id", columnList = "creature_id")
    ]
)
class ActionLog private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "creature_id")
    val creatureId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    val actionType: ActionType,

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    val payloadJson: String = "{}",

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun create(
            userId: Long,
            creatureId: Long?,
            actionType: ActionType,
            payloadJson: String = "{}"
        ): ActionLog = ActionLog(
            userId = userId,
            creatureId = creatureId,
            actionType = actionType,
            payloadJson = payloadJson
        )
    }
}



