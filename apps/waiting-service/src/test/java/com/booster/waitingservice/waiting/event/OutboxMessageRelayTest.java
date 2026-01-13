package com.booster.waitingservice.waiting.event;

import com.booster.waitingservice.waiting.domain.outbox.OutboxEvent;
import com.booster.waitingservice.waiting.domain.outbox.OutboxRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxMessageRelay 테스트")
class OutboxMessageRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxMessageRelay outboxMessageRelay;

    @Nested
    @DisplayName("publishEvents 메서드")
    class PublishEvents {

        @Test
        @DisplayName("성공: 미발행 이벤트를 Kafka로 전송하고 published를 true로 변경한다")
        void publishEvents_success() {
            // given
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(100L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 100}")
                    .build();

            given(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                    .willReturn(List.of(event));

            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("booster.waiting.events", 0), 0L, 0, 0L, 0, 0);
            SendResult<String, String> sendResult = new SendResult<>(null, metadata);
            CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

            given(kafkaTemplate.send(eq("booster.waiting.events"), anyString(), anyString()))
                    .willReturn(future);

            // when
            outboxMessageRelay.publishEvents();

            // then
            assertThat(event.isPublished()).isTrue();
            verify(kafkaTemplate).send(eq("booster.waiting.events"), eq("100"), eq("{\"waitingId\": 100}"));
        }

        @Test
        @DisplayName("성공: 미발행 이벤트가 없으면 아무 작업도 하지 않는다")
        void publishEvents_noEvents() {
            // given
            given(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                    .willReturn(Collections.emptyList());

            // when
            outboxMessageRelay.publishEvents();

            // then
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 여러 이벤트를 순차적으로 발행한다")
        void publishEvents_multipleEvents() {
            // given
            OutboxEvent event1 = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(1L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 1}")
                    .build();

            OutboxEvent event2 = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(2L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 2}")
                    .build();

            given(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                    .willReturn(List.of(event1, event2));

            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("booster.waiting.events", 0), 0L, 0, 0L, 0, 0);
            SendResult<String, String> sendResult = new SendResult<>(null, metadata);
            CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

            given(kafkaTemplate.send(eq("booster.waiting.events"), anyString(), anyString()))
                    .willReturn(future);

            // when
            outboxMessageRelay.publishEvents();

            // then
            assertThat(event1.isPublished()).isTrue();
            assertThat(event2.isPublished()).isTrue();
            verify(kafkaTemplate, times(2)).send(eq("booster.waiting.events"), anyString(), anyString());
        }

        @Test
        @DisplayName("실패: Kafka 전송 실패 시 published는 false로 유지된다")
        void publishEvents_kafkaFailure() {
            // given
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(100L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 100}")
                    .build();

            given(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                    .willReturn(List.of(event));

            CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka connection failed"));

            given(kafkaTemplate.send(eq("booster.waiting.events"), anyString(), anyString()))
                    .willReturn(failedFuture);

            // when
            outboxMessageRelay.publishEvents();

            // then
            assertThat(event.isPublished()).isFalse(); // 실패했으므로 false 유지
        }

        @Test
        @DisplayName("부분 실패: 첫 번째 이벤트 성공, 두 번째 이벤트 실패 시 첫 번째만 published")
        void publishEvents_partialFailure() {
            // given
            OutboxEvent event1 = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(1L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 1}")
                    .build();

            OutboxEvent event2 = OutboxEvent.builder()
                    .aggregateType("WAITING")
                    .aggregateId(2L)
                    .eventType("CALLED")
                    .payload("{\"waitingId\": 2}")
                    .build();

            given(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                    .willReturn(List.of(event1, event2));

            // 첫 번째 성공
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("booster.waiting.events", 0), 0L, 0, 0L, 0, 0);
            SendResult<String, String> sendResult = new SendResult<>(null, metadata);
            CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(sendResult);

            // 두 번째 실패
            CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka error"));

            given(kafkaTemplate.send(eq("booster.waiting.events"), eq("1"), anyString()))
                    .willReturn(successFuture);
            given(kafkaTemplate.send(eq("booster.waiting.events"), eq("2"), anyString()))
                    .willReturn(failedFuture);

            // when
            outboxMessageRelay.publishEvents();

            // then
            assertThat(event1.isPublished()).isTrue();  // 성공
            assertThat(event2.isPublished()).isFalse(); // 실패
        }
    }
}