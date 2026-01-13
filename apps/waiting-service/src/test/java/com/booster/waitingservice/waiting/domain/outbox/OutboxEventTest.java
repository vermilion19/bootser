package com.booster.waitingservice.waiting.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEvent 도메인 엔티티 테스트")
class OutboxEventTest {

    @Nested
    @DisplayName("Builder로 생성")
    class Create {

        @Test
        @DisplayName("성공: Builder로 OutboxEvent를 생성하면 published는 false이다")
        void create_success() {
            // given
            String aggregateType = "WAITING";
            Long aggregateId = 100L;
            String eventType = "CALLED";
            String payload = "{\"waitingId\": 100}";

            // when
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .build();

            // then
            assertThat(outboxEvent.getId()).isNotNull(); // Snowflake ID 생성됨
            assertThat(outboxEvent.getAggregateType()).isEqualTo(aggregateType);
            assertThat(outboxEvent.getAggregateId()).isEqualTo(aggregateId);
            assertThat(outboxEvent.getEventType()).isEqualTo(eventType);
            assertThat(outboxEvent.getPayload()).isEqualTo(payload);
            assertThat(outboxEvent.isPublished()).isFalse(); // 초기값 false
            assertThat(outboxEvent.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("성공: 서로 다른 OutboxEvent는 다른 ID를 가진다")
        void create_uniqueIds() {
            // when
            OutboxEvent event1 = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(1L)
                    .eventType("CALLED")
                    .payload("{}")
                    .build();

            OutboxEvent event2 = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(2L)
                    .eventType("CALLED")
                    .payload("{}")
                    .build();

            // then
            assertThat(event1.getId()).isNotEqualTo(event2.getId());
        }
    }

    @Nested
    @DisplayName("publish 메서드")
    class Publish {

        @Test
        @DisplayName("성공: publish()를 호출하면 published가 true로 변경된다")
        void publish_success() {
            // given
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(100L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 100}")
                    .build();

            assertThat(outboxEvent.isPublished()).isFalse();

            // when
            outboxEvent.publish();

            // then
            assertThat(outboxEvent.isPublished()).isTrue();
        }

        @Test
        @DisplayName("성공: 이미 published인 상태에서 publish()를 다시 호출해도 에러가 발생하지 않는다")
        void publish_idempotent() {
            // given
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(100L)
                    .eventType("CALLED")
                    .payload("{}")
                    .build();

            outboxEvent.publish();
            assertThat(outboxEvent.isPublished()).isTrue();

            // when (다시 호출)
            outboxEvent.publish();

            // then (여전히 true)
            assertThat(outboxEvent.isPublished()).isTrue();
        }
    }

    @Nested
    @DisplayName("다양한 이벤트 타입")
    class EventTypes {

        @Test
        @DisplayName("WAITING 도메인의 REGISTER 이벤트")
        void waiting_register_event() {
            // when
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(1L)
                    .eventType("REGISTER")
                    .payload("{\"waitingId\": 1, \"restaurantId\": 100}")
                    .build();

            // then
            assertThat(event.getAggregateType()).isEqualTo("WAITING");
            assertThat(event.getEventType()).isEqualTo("REGISTER");
        }

        @Test
        @DisplayName("WAITING 도메인의 CANCEL 이벤트")
        void waiting_cancel_event() {
            // when
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(1L)
                    .eventType("CANCEL")
                    .payload("{\"waitingId\": 1, \"reason\": \"USER_REQUEST\"}")
                    .build();

            // then
            assertThat(event.getAggregateType()).isEqualTo("WAITING");
            assertThat(event.getEventType()).isEqualTo("CANCEL");
        }

        @Test
        @DisplayName("RESTAURANT 도메인의 UPDATED 이벤트")
        void restaurant_updated_event() {
            // when
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("RESTAURANT")
                    .aggregateId(100L)
                    .eventType("UPDATED")
                    .payload("{\"restaurantId\": 100, \"name\": \"새 이름\"}")
                    .build();

            // then
            assertThat(event.getAggregateType()).isEqualTo("RESTAURANT");
            assertThat(event.getEventType()).isEqualTo("UPDATED");
        }
    }
}