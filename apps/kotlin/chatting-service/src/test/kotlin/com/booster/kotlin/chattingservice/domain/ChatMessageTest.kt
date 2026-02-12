package com.booster.kotlin.chattingservice.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested

class ChatMessageTest {

    private val objectMapper = jacksonObjectMapper()

    @Nested
    inner class Factory {

        @Test
        fun `enter 팩토리 메서드는 ENTER 타입 메시지를 생성한다`() {
            val message = ChatMessage.enter("room-1", "user1")

            assertThat(message.type).isEqualTo(ChatMessage.Type.ENTER)
            assertThat(message.roomId).isEqualTo("room-1")
            assertThat(message.userId).isEqualTo("user1")
            assertThat(message.message).contains("user1")
        }

        @Test
        fun `talk 팩토리 메서드는 TALK 타입 메시지를 생성한다`() {
            val message = ChatMessage.talk("room-1", "user1", "안녕하세요")

            assertThat(message.type).isEqualTo(ChatMessage.Type.TALK)
            assertThat(message.message).isEqualTo("안녕하세요")
        }

        @Test
        fun `leave 팩토리 메서드는 LEAVE 타입 메시지를 생성한다`() {
            val message = ChatMessage.leave("room-1", "user1")

            assertThat(message.type).isEqualTo(ChatMessage.Type.LEAVE)
            assertThat(message.message).contains("user1")
        }

        @Test
        fun `system 팩토리 메서드는 SYSTEM userId로 메시지를 생성한다`() {
            val message = ChatMessage.system("room-1", "서버 점검 중")

            assertThat(message.userId).isEqualTo("SYSTEM")
            assertThat(message.message).isEqualTo("서버 점검 중")
        }
    }

    @Nested
    inner class Immutability {

        @Test
        fun `withUserId는 원본을 변경하지 않고 새 객체를 반환한다`() {
            val original = ChatMessage.talk("room-1", "user1", "hello")
            val modified = original.withUserId("user2")

            assertThat(original.userId).isEqualTo("user1")
            assertThat(modified.userId).isEqualTo("user2")
            assertThat(modified.message).isEqualTo("hello")
        }

        @Test
        fun `withMessage는 원본을 변경하지 않고 새 객체를 반환한다`() {
            val original = ChatMessage.talk("room-1", "user1", "hello")
            val modified = original.withMessage("world")

            assertThat(original.message).isEqualTo("hello")
            assertThat(modified.message).isEqualTo("world")
        }
    }

    @Nested
    inner class Serialization {

        @Test
        fun `JSON 직렬화 및 역직렬화가 정상 동작한다`() {
            val original = ChatMessage.talk("room-1", "user1", "테스트 메시지")
            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue<ChatMessage>(json)

            assertThat(deserialized).isEqualTo(original)
        }

        @Test
        fun `type 필드가 소문자여도 역직렬화된다`() {
            val json = """{"type":"talk","roomId":"room-1","userId":"user1","message":"hello"}"""
            val message = objectMapper.readValue<ChatMessage>(json)

            assertThat(message.type).isEqualTo(ChatMessage.Type.TALK)
        }

        @Test
        fun `message 필드가 없으면 빈 문자열로 역직렬화된다`() {
            val json = """{"type":"PING","roomId":"room-1","userId":"user1"}"""
            val message = objectMapper.readValue<ChatMessage>(json)

            assertThat(message.message).isEmpty()
        }
    }
}
