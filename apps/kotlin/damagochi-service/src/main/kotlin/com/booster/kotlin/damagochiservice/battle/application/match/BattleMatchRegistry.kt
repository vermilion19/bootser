package com.booster.kotlin.damagochiservice.battle.application.match

import java.time.LocalDateTime

interface BattleMatchRegistry {
    fun nextTicket(): Long
    fun enqueueRandom(entry: MatchQueueEntry): RandomQueueRegistryResult
    fun removeFromRandomQueue(userId: Long)

    fun findRoomByOwner(userId: Long, now: LocalDateTime): RoomSlot?
    fun listRoomsByOwner(userId: Long, now: LocalDateTime): List<RoomSlot>
    fun findRoomByCode(code: String, now: LocalDateTime): RoomSlot?
    fun saveRoom(room: RoomSlot): Boolean
    fun cancelRoom(userId: Long, code: String, now: LocalDateTime): Boolean
    fun takeRoomForJoin(code: String, now: LocalDateTime): RoomSlot?

    fun getLastIssuedRoomCode(userId: Long): String?
    fun setLastIssuedRoomCode(userId: Long, code: String)
}

data class MatchQueueEntry(
    val ticket: Long,
    val userId: Long,
    val creatureId: Long
)

sealed interface RandomQueueRegistryResult {
    data class Waiting(val entry: MatchQueueEntry) : RandomQueueRegistryResult
    data class Matched(val me: MatchQueueEntry, val opponent: MatchQueueEntry) : RandomQueueRegistryResult
}

data class RoomSlot(
    val code: String,
    val owner: MatchQueueEntry,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime
)



