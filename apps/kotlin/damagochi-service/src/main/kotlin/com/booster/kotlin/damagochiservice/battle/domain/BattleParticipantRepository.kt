package com.booster.kotlin.damagochiservice.battle.domain

import org.springframework.data.jpa.repository.JpaRepository

interface BattleParticipantRepository : JpaRepository<BattleParticipant, Long> {
    fun findAllByBattleId(battleId: Long): List<BattleParticipant>
    fun findByBattleIdAndUserId(battleId: Long, userId: Long): BattleParticipant?
}



