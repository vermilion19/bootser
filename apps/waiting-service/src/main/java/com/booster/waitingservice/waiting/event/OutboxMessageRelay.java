package com.booster.waitingservice.waiting.event;

import com.booster.waitingservice.waiting.domain.outbox.OutboxEvent;
import com.booster.waitingservice.waiting.domain.outbox.OutboxRepository;
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

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishEvents() {
        List<OutboxEvent> events = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            CompletableFuture<SendResult<String,String>> future = kafkaTemplate.send(
                    "booster.waiting.events",
                    String.valueOf(event.getAggregateId()),
                    event.getPayload()
            );
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
