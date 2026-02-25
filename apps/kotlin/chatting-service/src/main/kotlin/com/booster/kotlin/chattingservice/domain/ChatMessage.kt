package com.booster.kotlin.chattingservice.domain

import com.fasterxml.jackson.annotation.JsonCreator

data class ChatMessage(
    val type: Type,
    val roomId: String,
    val userId: String,
    val message: String = ""
) {
    enum class Type {
        ENTER, TALK, LEAVE, PING;

        companion object {
            @JvmStatic
            @JsonCreator
            fun fromString(value: String): Type = valueOf(value.uppercase())
        }
    }

    fun withUserId(newUserId: String): ChatMessage = copy(userId = newUserId)

    fun withMessage(newMessage: String): ChatMessage = copy(message = newMessage)

    companion object {
        fun enter(roomId: String, userId: String): ChatMessage =
            ChatMessage(Type.ENTER, roomId, userId, "${userId}님이 입장했습니다.")

        fun talk(roomId: String, userId: String, message: String): ChatMessage =
            ChatMessage(Type.TALK, roomId, userId, message)

        fun leave(roomId: String, userId: String): ChatMessage =
            ChatMessage(Type.LEAVE, roomId, userId, "${userId}님이 퇴장했습니다.")

        fun ping(roomId: String, userId: String): ChatMessage =
            ChatMessage(Type.PING, roomId, userId)

        fun system(roomId: String, message: String): ChatMessage =
            ChatMessage(Type.TALK, roomId, "SYSTEM", message)
    }
}
