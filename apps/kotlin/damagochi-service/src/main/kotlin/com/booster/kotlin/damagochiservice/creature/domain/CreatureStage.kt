package com.booster.kotlin.damagochiservice.creature.domain

enum class CreatureStage(val level: Int) {
    STAGE_1(1),
    STAGE_2(2),
    STAGE_3(3),
    STAGE_4(4),
    STAGE_5(5);

    fun next(): CreatureStage? = entries.firstOrNull { it.level == this.level + 1 }

    companion object {
        fun initial(): CreatureStage = STAGE_1
    }
}



