package com.booster.restaurantservice.restaurant.event.outbox;

import com.booster.restaurantservice.restaurant.domain.outbox.OutboxEvent;
import com.booster.restaurantservice.restaurant.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxMessageRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional // 읽고 -> 쏘고 -> 상태변경까지 하나의 작업
    public void publishEvents() {
        List<OutboxEvent> events = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            // 1. Kafka 전송 (Topic: booster.restaurant.events)
            // 키를 aggregateId로 설정하여 순서 보장
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    "booster.restaurant.events",
                    String.valueOf(event.getAggregateId()),
                    event.getPayload()
            );

            // 2. 전송 성공 시 상태 변경 (동기적 처리 혹은 콜백 처리)
            // 여기서는 간단하게 join()으로 동기 처리하여 안전성 확보
            try {
                future.join();
                event.publish(); // DB 업데이트 (published = true)
                log.info("Event Published: ID={}", event.getId());
            } catch (Exception e) {
                log.error("Kafka 전송 실패: ID={}", event.getId(), e);
                // 실패하면 다음 스케줄에 다시 시도함 (Retry)
            }
        }
    }

}
