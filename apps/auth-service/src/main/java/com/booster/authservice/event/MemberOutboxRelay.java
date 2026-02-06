package com.booster.authservice.event;

import com.booster.authservice.domain.outbox.MemberOutboxEvent;
import com.booster.authservice.domain.outbox.MemberOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberOutboxRelay {
    private final MemberOutboxRepository memberOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishEvents() {
        List<MemberOutboxEvent> events = memberOutboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (MemberOutboxEvent event : events) {
            CompletableFuture<SendResult<String,String>> future = kafkaTemplate.send(
                    "member-events",
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
