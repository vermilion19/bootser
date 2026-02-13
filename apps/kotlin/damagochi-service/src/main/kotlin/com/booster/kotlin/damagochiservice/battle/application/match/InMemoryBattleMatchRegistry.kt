package com.booster.kotlin.damagochiservice.battle.application.match

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
@ConditionalOnProperty(
    prefix = "damagochi.battle",
    name = ["registry-type"],
    havingValue = "memory",
    matchIfMissing = true
)
class InMemoryBattleMatchRegistry : BattleMatchRegistry {
    private val queueLock = Any()
    private val queue = ArrayDeque<MatchQueueEntry>()
    private val ticket = AtomicLong(1L)

    private val roomLock = Any()
    private val rooms = ConcurrentHashMap<String, RoomSlot>()
    private val lastCodeByUser = ConcurrentHashMap<Long, String>()

    override fun nextTicket(): Long = ticket.getAndIncrement()

    override fun enqueueRandom(entry: MatchQueueEntry): RandomQueueRegistryResult {
        synchronized(queueLock) {
            val existing = queue.firstOrNull { it.userId == entry.userId }
            if (existing != null) {
                return RandomQueueRegistryResult.Waiting(existing)
            }

            val iterator = queue.iterator()
            var opponent: MatchQueueEntry? = null
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate.userId != entry.userId) {
                    opponent = candidate
                    iterator.remove()
                    break
                }
            }
            if (opponent == null) {
                queue.addLast(entry)
                return RandomQueueRegistryResult.Waiting(entry)
            }
            return RandomQueueRegistryResult.Matched(entry, opponent)
        }
    }

    override fun removeFromRandomQueue(userId: Long) {
        synchronized(queueLock) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().userId == userId) {
                    iterator.remove()
                    break
                }
            }
        }
    }

    override fun findRoomByOwner(userId: Long, now: LocalDateTime): RoomSlot? {
        cleanupExpiredRooms(now)
        return rooms.values.firstOrNull { it.owner.userId == userId }
    }

    override fun listRoomsByOwner(userId: Long, now: LocalDateTime): List<RoomSlot> {
        cleanupExpiredRooms(now)
        return rooms.values.filter { it.owner.userId == userId }.sortedByDescending { it.createdAt }
    }

    override fun findRoomByCode(code: String, now: LocalDateTime): RoomSlot? {
        cleanupExpiredRooms(now)
        return rooms[code]
    }

    override fun saveRoom(room: RoomSlot): Boolean {
        synchronized(roomLock) {
            if (rooms.containsKey(room.code)) return false
            rooms[room.code] = room
            return true
        }
    }

    override fun cancelRoom(userId: Long, code: String, now: LocalDateTime): Boolean {
        cleanupExpiredRooms(now)
        val room = rooms[code] ?: return false
        if (room.owner.userId != userId) return false
        rooms.remove(code)
        return true
    }

    override fun takeRoomForJoin(code: String, now: LocalDateTime): RoomSlot? {
        cleanupExpiredRooms(now)
        return rooms.remove(code)
    }

    override fun getLastIssuedRoomCode(userId: Long): String? = lastCodeByUser[userId]

    override fun setLastIssuedRoomCode(userId: Long, code: String) {
        lastCodeByUser[userId] = code
    }

    private fun cleanupExpiredRooms(now: LocalDateTime) {
        synchronized(roomLock) {
            val iterator = rooms.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!entry.value.expiresAt.isAfter(now)) {
                    iterator.remove()
                }
            }
        }
    }
}



