package com.booster.kotlin.damagochiservice.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "damagochi.balance")
data class GameBalanceProperties(
    val creature: CreaturePolicy = CreaturePolicy(),
    val action: ActionPolicy = ActionPolicy(),
    val state: StatePolicy = StatePolicy(),
    val battle: BattlePolicy = BattlePolicy()
)

data class CreaturePolicy(
    val maxCreatureCountPerUser: Long = 3,
    val activateCooldownMinutes: Long = 30
)

data class ActionPolicy(
    val sleepToggleCooldownMinutes: Long = 10,
    val feedHungerGain: Int = 20,
    val feedConditionGain: Int = 5,
    val feedHealthGain: Int = 2,
    val treatHealthGain: Int = 15,
    val treatConditionGain: Int = 10
)

data class StatePolicy(
    val agePerSleep30Min: Long = 1,
    val agePerAwake60Min: Long = 1,
    val hungerDecayPer30Min: Int = 1,
    val healthDecayPer60Min: Int = 1,
    val conditionDecayPer20MinAtZero: Int = 1,
    val nightConditionPenaltyPer30Min: Int = 1,
    val nightStartHour: Int = 23,
    val nightEndHourExclusive: Int = 7
)

data class BattlePolicy(
    val roomTtlMinutes: Long = 10,
    val roomCodeLength: Int = 6,
    val roomCodeGenerateAttempts: Int = 32,
    val roomCodeChars: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789",
    val baseConditionWeight: Int = 2,
    val baseHealthWeight: Int = 1,
    val baseHungerWeight: Int = 1,
    val baseWinRateWeight: Int = 1,
    val randomScoreMinInclusive: Int = 0,
    val randomScoreMaxExclusive: Int = 31,
    val winRateDeltaWin: Int = 3,
    val winRateDeltaLose: Int = -3,
    val winRateDeltaDraw: Int = 0
)



