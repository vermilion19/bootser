package com.booster.queryburst.order.event;

import com.booster.common.JsonUtils;
import com.booster.queryburst.lock.DistributedLock;
import com.booster.queryburst.lock.FencingToken;
import com.booster.queryburst.lock.LockAcquisitionException;
import com.booster.queryburst.order.domain.outbox.OutboxEvent;
import com.booster.queryburst.order.domain.outbox.OutboxEventRepository;
import com.booster.queryburst.order.domain.outbox.OutboxStatus;
import com.booster.storage.kafka.core.KafkaTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageRelay {

    private static final String RELAY_LOCK_KEY = "outbox:relay:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration SENDING_STALE_THRESHOLD = Duration.ofMinutes(1);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DistributedLock distributedLock;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 3000)
    public void schedule() {
        FencingToken token;
        try {
            token = distributedLock.tryLock(RELAY_LOCK_KEY, LOCK_TTL);
        } catch (LockAcquisitionException e) {
            log.debug("[OutboxRelay] another instance is already running.");
            return;
        }

        try {
            publishPendingEvents();
        } finally {
            distributedLock.unlock(RELAY_LOCK_KEY, token);
        }
    }

    private void publishPendingEvents() {
        List<RelayCandidate> events = claimPublishCandidates();
        if (events.isEmpty()) {
            return;
        }

        log.info("[OutboxRelay] start publishing {} events", events.size());

        for (RelayCandidate event : events) {
            try {
                OrderEventPayload payload = JsonUtils.MAPPER.readValue(event.payload(), OrderEventPayload.class);
                kafkaTemplate.send(
                                KafkaTopic.ORDER_EVENTS.getTopic(),
                                String.valueOf(event.aggregateId()),
                                payload)
                        .get(5, TimeUnit.SECONDS);

                markPublished(event.id());
                log.debug("[OutboxRelay] publish success. eventId={}, type={}", event.id(), event.eventType());
            } catch (Exception e) {
                markPublishFailed(event.id());
                log.error("[OutboxRelay] publish failed. eventId={}", event.id(), e);
            }
        }
    }

    private List<RelayCandidate> claimPublishCandidates() {
        return transactionTemplate.execute(status -> {
            LocalDateTime staleThreshold = LocalDateTime.now().minus(SENDING_STALE_THRESHOLD);
            List<OutboxEvent> candidates = outboxEventRepository.findPublishCandidates(
                    OutboxStatus.PENDING,
                    OutboxStatus.SENDING,
                    staleThreshold,
                    PageRequest.of(0, BATCH_SIZE)
            );

            candidates.forEach(OutboxEvent::markSending);

            return candidates.stream()
                    .map(event -> new RelayCandidate(
                            event.getId(),
                            event.getAggregateId(),
                            event.getEventType(),
                            event.getPayload()
                    ))
                    .toList();
        });
    }

    private void markPublished(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> outboxEventRepository.findById(eventId)
                .ifPresent(OutboxEvent::markPublished));
    }

    private void markPublishFailed(Long eventId) {
        transactionTemplate.executeWithoutResult(status -> outboxEventRepository.findById(eventId)
                .ifPresent(OutboxEvent::markPublishFailed));
    }

    private record RelayCandidate(
            Long id,
            Long aggregateId,
            String eventType,
            String payload
    ) {
    }
}
