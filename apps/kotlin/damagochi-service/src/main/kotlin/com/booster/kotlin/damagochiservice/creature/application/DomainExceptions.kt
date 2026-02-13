package com.booster.kotlin.damagochiservice.creature.application

import java.time.LocalDateTime

class ActivationCooldownException(
    val availableAt: LocalDateTime,
    val remainingSeconds: Long
) : RuntimeException("Activate cooldown not passed. availableAt=$availableAt")

class SleepToggleCooldownException(
    val availableAt: LocalDateTime,
    val remainingSeconds: Long
) : RuntimeException("Sleep toggle cooldown not passed. availableAt=$availableAt")



