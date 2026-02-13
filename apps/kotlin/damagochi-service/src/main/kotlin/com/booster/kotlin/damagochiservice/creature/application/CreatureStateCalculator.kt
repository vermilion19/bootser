package com.booster.kotlin.damagochiservice.creature.application

import com.booster.kotlin.damagochiservice.common.config.StatePolicy
import com.booster.kotlin.damagochiservice.creature.domain.CreatureState
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

object CreatureStateCalculator {
    private val KST_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")

    fun resolve(state: CreatureState, now: LocalDateTime, policy: StatePolicy): CreatureState {
        if (!now.isAfter(state.updatedAt)) {
            return state
        }

        val minutes = Duration.between(state.updatedAt, now).toMinutes()
        if (minutes <= 0) {
            state.updatedAt = now
            return state
        }

        if (state.sleeping) {
            val ageGain = (minutes / 30) * policy.agePerSleep30Min
            state.age += ageGain
            state.updatedAt = now
            return state
        }

        val hungerDrop = (minutes / 30).toInt() * policy.hungerDecayPer30Min
        val healthDrop = (minutes / 60).toInt() * policy.healthDecayPer60Min
        state.hunger = (state.hunger - hungerDrop).coerceAtLeast(0)
        state.health = (state.health - healthDrop).coerceAtLeast(0)
        state.age += (minutes / 60) * policy.agePerAwake60Min

        var conditionDrop = 0
        if (state.health == 0 || state.hunger == 0) {
            conditionDrop += (minutes / 20).toInt() * policy.conditionDecayPer20MinAtZero
            if (state.health == 0 && state.hunger == 0) {
                conditionDrop += (minutes / 20).toInt() * policy.conditionDecayPer20MinAtZero
            }
        }

        val nightMinutes = calculateNightMinutes(state.updatedAt, now, policy)
        conditionDrop += (nightMinutes / 30).toInt() * policy.nightConditionPenaltyPer30Min

        state.conditionScore = (state.conditionScore - conditionDrop).coerceAtLeast(0)
        state.updatedAt = now
        return state
    }

    private fun calculateNightMinutes(from: LocalDateTime, to: LocalDateTime, policy: StatePolicy): Long {
        var cursor = from
        var nightMinutes = 0L
        while (cursor.isBefore(to)) {
            val next = cursor.plusMinutes(30).let { if (it.isAfter(to)) to else it }
            val midpoint = cursor.plusMinutes(Duration.between(cursor, next).toMinutes() / 2)
            val hour = midpoint.atZone(KST_ZONE_ID).hour
            if (hour >= policy.nightStartHour || hour < policy.nightEndHourExclusive) {
                nightMinutes += Duration.between(cursor, next).toMinutes()
            }
            cursor = next
        }
        return nightMinutes
    }
}




