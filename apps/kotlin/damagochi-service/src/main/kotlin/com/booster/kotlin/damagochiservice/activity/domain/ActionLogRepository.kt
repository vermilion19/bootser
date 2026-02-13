package com.booster.kotlin.damagochiservice.activity.domain

import org.springframework.data.jpa.repository.JpaRepository

interface ActionLogRepository : JpaRepository<ActionLog, Long> {
    fun findTop100ByUserIdOrderByCreatedAtDesc(userId: Long): List<ActionLog>
}



