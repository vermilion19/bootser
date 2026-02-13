package com.booster.kotlin.damagochiservice.battle.application.match

import tools.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

@Component
@ConditionalOnProperty(
    prefix = "damagochi.battle",
    name = ["registry-type"],
    havingValue = "redis"
)
class RedisBattleMatchRegistry(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : BattleMatchRegistry {
    override fun nextTicket(): Long =
        redisTemplate.opsForValue().increment(KEY_TICKET_SEQ) ?: 1L

    override fun enqueueRandom(entry: MatchQueueEntry): RandomQueueRegistryResult {
        val existing = readHash(KEY_RANDOM_ENTRIES, entry.userId.toString(), MatchQueueEntry::class.java)
        if (existing != null) {
            return RandomQueueRegistryResult.Waiting(existing)
        }

        while (true) {
            val opponentUserId = redisTemplate.opsForList().leftPop(KEY_RANDOM_ORDER) ?: break
            if (opponentUserId == entry.userId.toString()) continue

            val opponent = readHash(KEY_RANDOM_ENTRIES, opponentUserId, MatchQueueEntry::class.java)
            redisTemplate.opsForHash<String, String>().delete(KEY_RANDOM_ENTRIES, opponentUserId)
            if (opponent != null) {
                return RandomQueueRegistryResult.Matched(entry, opponent)
            }
        }

        writeHash(KEY_RANDOM_ENTRIES, entry.userId.toString(), entry)
        redisTemplate.opsForList().rightPush(KEY_RANDOM_ORDER, entry.userId.toString())
        return RandomQueueRegistryResult.Waiting(entry)
    }

    override fun removeFromRandomQueue(userId: Long) {
        redisTemplate.opsForHash<String, String>().delete(KEY_RANDOM_ENTRIES, userId.toString())
    }

    override fun findRoomByOwner(userId: Long, now: LocalDateTime): RoomSlot? {
        val code = redisTemplate.opsForValue().get(ownerKey(userId)) ?: return null
        return findRoomByCode(code, now)
    }

    override fun listRoomsByOwner(userId: Long, now: LocalDateTime): List<RoomSlot> =
        listOfNotNull(findRoomByOwner(userId, now))

    override fun findRoomByCode(code: String, now: LocalDateTime): RoomSlot? {
        val payload = redisTemplate.opsForValue().get(roomKey(code)) ?: return null
        val room = objectMapper.readValue(payload, RoomSlot::class.java)
        if (!room.expiresAt.isAfter(now)) {
            redisTemplate.delete(roomKey(code))
            redisTemplate.delete(ownerKey(room.owner.userId))
            return null
        }
        return room
    }

    override fun saveRoom(room: RoomSlot): Boolean {
        val ttl = Duration.between(LocalDateTime.now(), room.expiresAt).seconds.coerceAtLeast(1)
        val saved = redisTemplate.opsForValue().setIfAbsent(
            roomKey(room.code),
            objectMapper.writeValueAsString(room),
            Duration.ofSeconds(ttl)
        ) ?: false
        if (!saved) return false
        redisTemplate.opsForValue().set(ownerKey(room.owner.userId), room.code, Duration.ofSeconds(ttl))
        return true
    }

    override fun cancelRoom(userId: Long, code: String, now: LocalDateTime): Boolean {
        val room = findRoomByCode(code, now) ?: return false
        if (room.owner.userId != userId) return false
        redisTemplate.delete(roomKey(code))
        redisTemplate.delete(ownerKey(userId))
        return true
    }

    override fun takeRoomForJoin(code: String, now: LocalDateTime): RoomSlot? {
        val payload = redisTemplate.opsForValue().getAndDelete(roomKey(code)) ?: return null
        val room = objectMapper.readValue(payload, RoomSlot::class.java)
        redisTemplate.delete(ownerKey(room.owner.userId))
        if (!room.expiresAt.isAfter(now)) return null
        return room
    }

    override fun getLastIssuedRoomCode(userId: Long): String? =
        redisTemplate.opsForValue().get(lastCodeKey(userId))

    override fun setLastIssuedRoomCode(userId: Long, code: String) {
        redisTemplate.opsForValue().set(lastCodeKey(userId), code)
    }

    private fun <T> readHash(key: String, field: String, type: Class<T>): T? {
        val payload = redisTemplate.opsForHash<String, String>().get(key, field) ?: return null
        return objectMapper.readValue(payload, type)
    }

    private fun writeHash(key: String, field: String, value: Any) {
        redisTemplate.opsForHash<String, String>().put(key, field, objectMapper.writeValueAsString(value))
    }

    private fun roomKey(code: String): String = "$KEY_ROOM_PREFIX:$code"
    private fun ownerKey(userId: Long): String = "$KEY_OWNER_PREFIX:$userId"
    private fun lastCodeKey(userId: Long): String = "$KEY_LAST_CODE_PREFIX:$userId"

    companion object {
        private const val KEY_TICKET_SEQ = "damagochi:battle:ticket-seq"
        private const val KEY_RANDOM_ORDER = "damagochi:battle:random:order"
        private const val KEY_RANDOM_ENTRIES = "damagochi:battle:random:entries"
        private const val KEY_ROOM_PREFIX = "damagochi:battle:room"
        private const val KEY_OWNER_PREFIX = "damagochi:battle:room-owner"
        private const val KEY_LAST_CODE_PREFIX = "damagochi:battle:last-code"
    }
}



